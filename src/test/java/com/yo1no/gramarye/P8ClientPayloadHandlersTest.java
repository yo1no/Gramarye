package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;

/** Direct executable NETWORK-to-client-main tests for the production P8 handlers. */
final class P8ClientPayloadHandlersTest {
    private static final ResourceLocation DEFAULT_PARTICLE = id("default_particle");
    private static final ResourceLocation DEFAULT_SOUND = id("default_sound");
    private static final ResourceLocation DEFAULT_TRAIL = id("default_trail");
    private static final ResourceLocation PARTICLE_TYPE = id("particle");
    private static final ResourceLocation SOUND_TYPE = id("sound");
    private static final ResourceLocation TRAIL_TYPE = id("trail");

    private static final String SOUND_JSON =
            "{\"pitch_milli\":1000,\"sound\":\"minecraft:entity.experience_orb.pickup\","
                    + "\"volume_milli\":600}";
    private static final String PARTICLE_JSON =
            "{\"count\":8,\"lifetime_ticks\":20,\"particle\":\"minecraft:enchant\","
                    + "\"size_milli_blocks\":250,\"speed_milli_blocks\":50}";
    private static final String TRAIL_JSON =
            "{\"lifetime_ticks\":16,\"particle\":\"minecraft:enchant\","
                    + "\"sample_interval_ticks\":2,\"segments\":8,"
                    + "\"size_milli_blocks\":200}";

    @Test
    void actualHandlersTransferExactlyOnceAndReleaseBothFailedEnqueues() {
        var disconnectedCatalogContext = new RecordingContext(null, true);
        P8ClientPayloadHandlers.handleProfileCatalog(
                catalog(1L), disconnectedCatalogContext);
        var disconnectedEventContext = new RecordingContext(null, true);
        P8ClientPayloadHandlers.handlePresentationEvent(
                event(1L, 1L), disconnectedEventContext);
        assertAll(
                () -> assertEquals(0, disconnectedCatalogContext.enqueueCalls()),
                () -> assertEquals(1, disconnectedCatalogContext.disconnectCalls()),
                () -> assertEquals(
                        "disconnect.gramarye.invalid_presentation_payload",
                        disconnectedCatalogContext.disconnectReason().getString()),
                () -> assertEquals(0, disconnectedEventContext.enqueueCalls()),
                () -> assertEquals(1, disconnectedEventContext.disconnectCalls()),
                () -> assertEquals(
                        "disconnect.gramarye.invalid_presentation_payload",
                        disconnectedEventContext.disconnectReason().getString()));

        var clientMain = new AtomicBoolean(true);
        var state = new P8ClientPresentationState(clientMain::get);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        P8ClientPayloadDispatchFactory.installClient(state);
        clientMain.set(false);

        var catalogFailure = new IllegalStateException("catalog enqueue rejected");
        var failedCatalogContext = new RecordingContext(catalogFailure);
        var generationOne = catalog(1L);
        assertSame(
                catalogFailure,
                assertThrows(
                        IllegalStateException.class,
                        () -> P8ClientPayloadHandlers.handleProfileCatalog(
                                generationOne, failedCatalogContext)));
        assertAll(
                () -> assertEquals(1, failedCatalogContext.enqueueCalls()),
                () -> assertEquals(0, failedCatalogContext.queuedTaskCount()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));
        assertSafeDispatchTask(failedCatalogContext.lastOfferedTask());
        failedCatalogContext.lastOfferedTask().releaseAfterFailedEnqueue();
        assertEquals(0L, state.combinedQueuedCharge());

        var catalogContext = new RecordingContext(null);
        P8ClientPayloadHandlers.handleProfileCatalog(generationOne, catalogContext);
        assertAll(
                () -> assertEquals(1, catalogContext.enqueueCalls()),
                () -> assertEquals(1, catalogContext.queuedTaskCount()),
                () -> assertEquals(1L, state.pendingCatalogGeneration()),
                () -> assertTrue(state.combinedQueuedCharge() > 0L));
        var catalogTask = catalogContext.takeOnlyTask();
        assertSafeDispatchTask(catalogTask);
        clientMain.set(true);
        catalogTask.run();
        assertAll(
                () -> assertEquals(1L, state.installedCatalogGeneration()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));

        clientMain.set(false);
        var eventFailure = new AssertionError("event enqueue rejected");
        var failedEventContext = new RecordingContext(eventFailure);
        var firstEvent = event(1L, 1L);
        assertSame(
                eventFailure,
                assertThrows(
                        AssertionError.class,
                        () -> P8ClientPayloadHandlers.handlePresentationEvent(
                                firstEvent, failedEventContext)));
        assertAll(
                () -> assertEquals(1, failedEventContext.enqueueCalls()),
                () -> assertEquals(0, failedEventContext.queuedTaskCount()),
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()),
                () -> assertEquals(0L, state.lastAcceptedSequence()));
        assertSafeDispatchTask(failedEventContext.lastOfferedTask());
        failedEventContext.lastOfferedTask().releaseAfterFailedEnqueue();
        assertEquals(0L, state.reservedEventCount());

        var eventContext = new RecordingContext(null);
        P8ClientPayloadHandlers.handlePresentationEvent(firstEvent, eventContext);
        assertAll(
                () -> assertEquals(1, eventContext.enqueueCalls()),
                () -> assertEquals(1, eventContext.queuedTaskCount()),
                () -> assertEquals(1L, state.reservedEventCount()),
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.lastAcceptedSequence()));
        var eventTask = eventContext.takeOnlyTask();
        assertSafeDispatchTask(eventTask);
        clientMain.set(true);
        eventTask.run();
        assertAll(
                () -> assertEquals(1, state.pendingEventCount()),
                () -> assertEquals(1L, state.reservedEventCount()),
                () -> assertEquals(1L, state.lastAcceptedSequence()),
                () -> assertEquals(1, state.onClientPostTick()),
                () -> assertEquals(1L, state.unavailableHandoffCount()),
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));
    }

    private static void assertSafeDispatchTask(P8ClientDispatchTask task) {
        assertInstanceOf(P8ClientDispatchTask.class, task);
        assertAll(
                () -> assertTrue(task.getClass().isRecord()),
                () -> assertFalse(task.getClass().isSynthetic()),
                () -> assertTrue(Arrays.stream(task.getClass().getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .map(field -> field.getType())
                        .noneMatch(P8ClientPayloadHandlersTest::isForbiddenRetainedType)));
    }

    private static boolean isForbiddenRetainedType(Class<?> type) {
        return IPayloadContext.class.isAssignableFrom(type)
                || ByteBuf.class.isAssignableFrom(type)
                || Connection.class.isAssignableFrom(type)
                || Player.class.isAssignableFrom(type)
                || Throwable.class.isAssignableFrom(type);
    }

    private static ProfileCatalogPayload catalog(long catalogGeneration) {
        var entries = new ArrayList<>(java.util.List.of(
                new P8ProfileCatalogEntry(
                        DEFAULT_PARTICLE,
                        PARTICLE_TYPE,
                        ProfileChannel.PARTICLE,
                        PARTICLE_TYPE,
                        0,
                        PARTICLE_JSON),
                new P8ProfileCatalogEntry(
                        DEFAULT_SOUND,
                        SOUND_TYPE,
                        ProfileChannel.SOUND,
                        SOUND_TYPE,
                        0,
                        SOUND_JSON),
                new P8ProfileCatalogEntry(
                        DEFAULT_TRAIL,
                        TRAIL_TYPE,
                        ProfileChannel.TRAIL,
                        TRAIL_TYPE,
                        0,
                        TRAIL_JSON)));
        entries.sort(Comparator.comparing(P8ProfileCatalogEntry::profileId));
        return new ProfileCatalogPayload(catalogGeneration, entries);
    }

    private static PresentationEventPayload event(
            long catalogGeneration, long sequence) {
        return new PresentationEventPayload(
                catalogGeneration,
                PresentationEventKind.CAST_RELEASE,
                new PresentationSourceSummary(OptionalInt.of(1), OptionalInt.empty()),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new PresentationPosition(1.0D, 2.0D, 3.0D),
                new PresentationDirection((short) 32_767, (short) 0, (short) 0),
                new PresentationAppearance(
                        0xffffffff,
                        0xffabcdef,
                        1_000,
                        Optional.of(DEFAULT_SOUND),
                        Optional.of(DEFAULT_PARTICLE),
                        Optional.of(DEFAULT_TRAIL),
                        Map.of()),
                sequence * 31L,
                sequence);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static final class RecordingContext implements IPayloadContext {
        private final Throwable enqueueFailure;
        private final boolean disconnectExpected;
        private final ArrayDeque<P8ClientDispatchTask> queuedTasks = new ArrayDeque<>();
        private int enqueueCalls;
        private int disconnectCalls;
        private Component disconnectReason;
        private P8ClientDispatchTask lastOfferedTask;

        private RecordingContext(Throwable enqueueFailure) {
            this(enqueueFailure, false);
        }

        private RecordingContext(Throwable enqueueFailure, boolean disconnectExpected) {
            if (enqueueFailure != null
                    && !(enqueueFailure instanceof RuntimeException)
                    && !(enqueueFailure instanceof Error)) {
                throw new IllegalArgumentException("enqueue failure must be unchecked");
            }
            this.enqueueFailure = enqueueFailure;
            this.disconnectExpected = disconnectExpected;
        }

        @Override
        public ICommonPacketListener listener() {
            throw new AssertionError("listener access was not expected");
        }

        @Override
        public Player player() {
            throw new AssertionError("player access was not expected");
        }

        @Override
        public void reply(CustomPacketPayload payload) {
            throw new AssertionError("reply was not expected");
        }

        @Override
        public void disconnect(Component reason) {
            if (!disconnectExpected || disconnectReason != null) {
                throw new AssertionError("disconnect was not expected");
            }
            disconnectCalls++;
            disconnectReason = Objects.requireNonNull(reason, "disconnect reason");
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            enqueueCalls++;
            lastOfferedTask = assertInstanceOf(P8ClientDispatchTask.class, task);
            throwEnqueueFailure();
            queuedTasks.addLast(lastOfferedTask);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            throw new AssertionError("supplier enqueue was not expected");
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.CLIENTBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new AssertionError("nested payload handling was not expected");
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new AssertionError("configuration task completion was not expected");
        }

        private int enqueueCalls() {
            return enqueueCalls;
        }

        private int disconnectCalls() {
            return disconnectCalls;
        }

        private Component disconnectReason() {
            return Objects.requireNonNull(disconnectReason, "disconnect reason");
        }

        private int queuedTaskCount() {
            return queuedTasks.size();
        }

        private P8ClientDispatchTask lastOfferedTask() {
            return Objects.requireNonNull(lastOfferedTask, "last offered task");
        }

        private P8ClientDispatchTask takeOnlyTask() {
            if (queuedTasks.size() != 1) {
                throw new AssertionError("expected exactly one queued task");
            }
            return queuedTasks.removeFirst();
        }

        private void throwEnqueueFailure() {
            if (enqueueFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (enqueueFailure instanceof Error error) {
                throw error;
            }
        }
    }
}
