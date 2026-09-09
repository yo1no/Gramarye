package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

/** Focused ownership, generation, and bounded-handoff tests for P8-S4 client state. */
final class P8ClientPresentationStateTest {
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
    void catalogMailboxCoalescesToTheLatestGenerationWithOneDrain() {
        var state = new P8ClientPresentationState(() -> true);
        state.onConnectionOpened();
        var generationTwo = catalog(2L);
        var generationThree = catalog(3L);
        var generationFour = catalog(4L);

        var soleDrain = state.prepareProfileCatalog(generationTwo).orElseThrow();
        assertEquals(2L, state.pendingCatalogGeneration());
        assertEquals(catalogCharge(generationTwo), state.combinedQueuedCharge());

        assertTrue(state.prepareProfileCatalog(generationFour).isEmpty());
        assertEquals(4L, state.pendingCatalogGeneration());
        assertEquals(catalogCharge(generationFour), state.combinedQueuedCharge());
        assertTrue(state.prepareProfileCatalog(generationThree).isEmpty());
        assertEquals(4L, state.pendingCatalogGeneration());

        soleDrain.run();
        assertAll(
                () -> assertEquals(4L, state.installedCatalogGeneration()),
                () -> assertSame(
                        generationFour.snapshot(), state.installedCatalogSnapshot()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));

        soleDrain.run();
        soleDrain.releaseAfterFailedEnqueue();
        soleDrain.releaseAfterFailedEnqueue();
        assertEquals(0L, state.combinedQueuedCharge());
        assertTrue(state.prepareProfileCatalog(generationFour).isEmpty());

        var nextDrain = state.prepareProfileCatalog(catalog(5L)).orElseThrow();
        nextDrain.releaseAfterFailedEnqueue();
        nextDrain.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertEquals(4L, state.installedCatalogGeneration()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));
    }

    @Test
    void staleGenerationsAndNonIncreasingSequencesNeverAdvanceHighWater() {
        var state = readyState(7L);

        runPrepared(state, event(6L, 1L));
        assertEquals(0L, state.lastAcceptedSequence());

        runPrepared(state, event(7L, 5L));
        assertAll(
                () -> assertEquals(5L, state.lastAcceptedSequence()),
                () -> assertEquals(1, state.pendingEventCount()));
        runPrepared(state, event(7L, 5L));
        runPrepared(state, event(7L, 4L));
        assertAll(
                () -> assertEquals(5L, state.lastAcceptedSequence()),
                () -> assertEquals(1, state.pendingEventCount()));

        runPrepared(state, event(7L, 9L));
        assertAll(
                () -> assertEquals(9L, state.lastAcceptedSequence()),
                () -> assertEquals(2, state.pendingEventCount()));

        state.onWorldLoaded();
        assertAll(
                () -> assertEquals(9L, state.lastAcceptedSequence()),
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.reservedEventCount()));

        var staleResourceTask = state.preparePresentationEvent(event(7L, 10L))
                .orElseThrow();
        var previousResourceGeneration = state.resourceGeneration();
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        staleResourceTask.run();
        staleResourceTask.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertEquals(
                        previousResourceGeneration + 1L, state.resourceGeneration()),
                () -> assertEquals(9L, state.lastAcceptedSequence()),
                () -> assertEquals(0L, state.reservedEventCount()));

        installCatalog(state, 8L);
        assertEquals(9L, state.lastAcceptedSequence());
        runPrepared(state, event(7L, 10L));
        runPrepared(state, event(8L, 8L));
        assertEquals(9L, state.lastAcceptedSequence());
        runPrepared(state, event(8L, 10L));
        assertEquals(10L, state.lastAcceptedSequence());

        var staleConnectionTask = state.preparePresentationEvent(event(8L, 11L))
                .orElseThrow();
        state.onLoggedOut();
        staleConnectionTask.run();
        staleConnectionTask.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertFalse(state.connected()),
                () -> assertEquals(0L, state.lastAcceptedSequence()),
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertNull(state.installedCatalogSnapshot()));

        state.onConnectionOpened();
        state.onWorldLoaded();
        installCatalog(state, 8L);
        runPrepared(state, event(8L, 1L));
        assertEquals(1L, state.lastAcceptedSequence());
    }

    @Test
    void sharedPoolRejectsTheSixtyFifthEventBeforeEnqueueAndReleasesExactlyOnce() {
        var state = readyState(1L);
        var tasks = new ArrayList<P8ClientDispatchTask>();
        long expectedBodyBytes = 0L;
        long expectedCharge = 0L;
        for (var sequence = 1L;
                sequence <= PresentationLimits.MAX_CLIENT_PENDING_EVENTS;
                sequence++) {
            var payload = event(1L, sequence);
            expectedBodyBytes += payload.bodySize();
            expectedCharge += eventCharge(payload);
            tasks.add(state.preparePresentationEvent(payload).orElseThrow());
        }
        var admittedBodyBytes = expectedBodyBytes;
        var admittedCharge = expectedCharge;

        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_CLIENT_PENDING_EVENTS,
                        state.reservedEventCount()),
                () -> assertEquals(admittedBodyBytes, state.reservedEventBodyBytes()),
                () -> assertEquals(admittedCharge, state.combinedQueuedCharge()),
                () -> assertTrue(state.preparePresentationEvent(event(1L, 65L)).isEmpty()),
                () -> assertEquals(0, state.pendingEventCount()));

        var first = tasks.getFirst();
        first.releaseAfterFailedEnqueue();
        first.releaseAfterFailedEnqueue();
        assertEquals(63L, state.reservedEventCount());
        var replacement = state.preparePresentationEvent(event(1L, 65L)).orElseThrow();
        assertEquals(64L, state.reservedEventCount());

        for (var task : tasks) {
            task.releaseAfterFailedEnqueue();
        }
        replacement.releaseAfterFailedEnqueue();
        replacement.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()));
    }

    @Test
    void logoutCleanupInvalidatesNetworkAndPendingOwnersIdempotently() {
        var state = readyState(1L);
        var pendingOwner = state.preparePresentationEvent(event(1L, 1L)).orElseThrow();
        pendingOwner.run();
        pendingOwner.releaseAfterFailedEnqueue();
        pendingOwner.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertEquals(1, state.pendingEventCount()),
                () -> assertEquals(1L, state.reservedEventCount()));

        var networkOwner = state.preparePresentationEvent(event(1L, 2L)).orElseThrow();
        var catalogOwner = state.prepareProfileCatalog(catalog(2L)).orElseThrow();
        assertTrue(state.combinedQueuedCharge() > 0L);
        state.onLoggedOut();
        assertAll(
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()),
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.lastAcceptedSequence()));

        pendingOwner.run();
        pendingOwner.releaseAfterFailedEnqueue();
        networkOwner.run();
        networkOwner.releaseAfterFailedEnqueue();
        networkOwner.releaseAfterFailedEnqueue();
        catalogOwner.run();
        catalogOwner.releaseAfterFailedEnqueue();
        catalogOwner.releaseAfterFailedEnqueue();
        assertAll(
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()),
                () -> assertEquals(0L, state.installedCatalogGeneration()));
    }

    @Test
    void loadThenUnloadWorldReplacementAdvancesTwiceAndKeepsTheNewWorldReady() {
        var state = readyState(1L);
        runPrepared(state, event(1L, 1L));
        assertEquals(1, state.pendingEventCount());

        var initialWorldGeneration = state.worldGeneration();
        state.onWorldLoaded();
        var afterNewLoad = state.worldGeneration();
        assertAll(
                () -> assertEquals(initialWorldGeneration + 1L, afterNewLoad),
                () -> assertTrue(state.worldReady()),
                () -> assertEquals(0, state.pendingEventCount()));

        var supersededLoadTask = state.preparePresentationEvent(event(1L, 2L))
                .orElseThrow();
        state.onWorldUnloaded(true);
        assertAll(
                () -> assertEquals(afterNewLoad + 1L, state.worldGeneration()),
                () -> assertTrue(state.worldReady()),
                () -> assertEquals(0L, state.reservedEventCount()));
        supersededLoadTask.run();
        supersededLoadTask.releaseAfterFailedEnqueue();
        assertEquals(1L, state.lastAcceptedSequence());

        runPrepared(state, event(1L, 2L));
        assertAll(
                () -> assertEquals(2L, state.lastAcceptedSequence()),
                () -> assertEquals(1, state.pendingEventCount()));
        state.onWorldUnloaded(false);
        assertAll(
                () -> assertFalse(state.worldReady()),
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(2L, state.lastAcceptedSequence()));
    }

    @Test
    void registeredResourceListenerAdvancesOnlyAtItsSuccessfulApplyBoundary()
            throws ReflectiveOperationException {
        var state = new P8ClientPresentationState(() -> true);
        var listenerType = Arrays.stream(P8ClientPresentationLifecycle.class
                        .getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("P8ResourceReloadListener"))
                .findFirst()
                .orElseThrow();
        assertTrue(SimplePreparableReloadListener.class.isAssignableFrom(listenerType));
        var constructor = listenerType.getDeclaredConstructor(
                P8ClientPresentationState.class);
        constructor.setAccessible(true);
        var listener = constructor.newInstance(state);
        var prepare = listenerType.getDeclaredMethod(
                "prepare",
                ResourceManager.class,
                net.minecraft.util.profiling.ProfilerFiller.class);
        var apply = listenerType.getDeclaredMethod(
                "apply",
                P8ClientResourceIndex.class,
                ResourceManager.class,
                net.minecraft.util.profiling.ProfilerFiller.class);
        prepare.setAccessible(true);
        apply.setAccessible(true);

        var prepared = assertInstanceOf(
                P8ClientResourceIndex.class,
                prepare.invoke(
                        listener, ResourceManager.Empty.INSTANCE, InactiveProfiler.INSTANCE));
        assertAll(
                () -> assertEquals(0L, state.resourceGeneration()),
                () -> assertFalse(state.resourceReady()));

        apply.invoke(
                listener,
                prepared,
                ResourceManager.Empty.INSTANCE,
                InactiveProfiler.INSTANCE);
        assertAll(
                () -> assertEquals(1L, state.resourceGeneration()),
                () -> assertTrue(state.resourceReady()));

        var failure = assertThrows(
                InvocationTargetException.class,
                () -> apply.invoke(
                        listener,
                        null,
                        ResourceManager.Empty.INSTANCE,
                        InactiveProfiler.INSTANCE));
        assertAll(
                () -> assertInstanceOf(NullPointerException.class, failure.getCause()),
                () -> assertEquals(1L, state.resourceGeneration()),
                () -> assertTrue(state.resourceReady()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new P8ClientResourceIndex(List.of(
                        ResourceLocation.fromNamespaceAndPath(
                                "a", "x".repeat(128)))));
    }

    @Test
    void unavailableTerminalDrainsAtMostThirtyTwoEventsPerClientTick() {
        var state = readyState(1L);
        for (var sequence = 1L;
                sequence <= PresentationLimits.MAX_CLIENT_PENDING_EVENTS;
                sequence++) {
            runPrepared(state, event(1L, sequence));
        }
        assertAll(
                () -> assertEquals(64, state.pendingEventCount()),
                () -> assertEquals(64L, state.reservedEventCount()),
                () -> assertEquals(64L, state.lastAcceptedSequence()),
                () -> assertEquals(0L, state.unavailableHandoffCount()));

        assertEquals(32, state.onClientPostTick());
        assertAll(
                () -> assertEquals(32, state.pendingEventCount()),
                () -> assertEquals(32L, state.reservedEventCount()),
                () -> assertEquals(32L, state.unavailableHandoffCount()),
                () -> assertTrue(state.combinedQueuedCharge() > 0L));

        assertEquals(32, state.onClientPostTick());
        assertAll(
                () -> assertEquals(0, state.pendingEventCount()),
                () -> assertEquals(0L, state.reservedEventCount()),
                () -> assertEquals(0L, state.reservedEventBodyBytes()),
                () -> assertEquals(0L, state.combinedQueuedCharge()),
                () -> assertEquals(64L, state.unavailableHandoffCount()),
                () -> assertEquals(0, state.onClientPostTick()));
    }

    @Test
    void localGenerationCountersUseLongMaximumOnceAndNeverWrap() {
        var connection = new P8ClientPresentationState(() -> true);
        connection.setGenerationCountersForTest(Long.MAX_VALUE - 1L, 0L, 0L);
        connection.onConnectionOpened();
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, connection.connectionGeneration()),
                () -> assertTrue(connection.connected()));
        connection.onLoggedOut();
        connection.onConnectionOpened();
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, connection.connectionGeneration()),
                () -> assertFalse(connection.connected()));

        var world = readyState(1L);
        world.setGenerationCountersForTest(
                world.connectionGeneration(), Long.MAX_VALUE - 1L, world.resourceGeneration());
        world.onWorldLoaded();
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, world.worldGeneration()),
                () -> assertTrue(world.worldReady()));
        world.onWorldUnloaded(true);
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, world.worldGeneration()),
                () -> assertFalse(world.worldReady()));

        var resource = new P8ClientPresentationState(() -> true);
        resource.setGenerationCountersForTest(0L, 0L, Long.MAX_VALUE - 1L);
        resource.onResourceIndexApplied(P8ClientResourceIndex.empty());
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, resource.resourceGeneration()),
                () -> assertTrue(resource.resourceReady()));
        resource.onResourceIndexApplied(P8ClientResourceIndex.empty());
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, resource.resourceGeneration()),
                () -> assertFalse(resource.resourceReady()));
    }

    private static P8ClientPresentationState readyState(long catalogGeneration) {
        var state = new P8ClientPresentationState(() -> true);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        installCatalog(state, catalogGeneration);
        assertAll(
                () -> assertTrue(state.connected()),
                () -> assertTrue(state.worldReady()),
                () -> assertTrue(state.resourceReady()),
                () -> assertEquals(catalogGeneration, state.installedCatalogGeneration()),
                () -> assertEquals(
                        state.resourceGeneration(),
                        state.catalogEvaluationResourceGeneration()));
        return state;
    }

    private static void installCatalog(
            P8ClientPresentationState state, long catalogGeneration) {
        var task = state.prepareProfileCatalog(catalog(catalogGeneration)).orElseThrow();
        task.run();
    }

    private static void runPrepared(
            P8ClientPresentationState state, PresentationEventPayload payload) {
        state.preparePresentationEvent(payload).orElseThrow().run();
    }

    private static ProfileCatalogPayload catalog(long catalogGeneration) {
        var entries = new ArrayList<>(List.of(
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

    private static long catalogCharge(ProfileCatalogPayload payload) {
        return payload.bodySize()
                + (long) PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES;
    }

    private static long eventCharge(PresentationEventPayload payload) {
        return payload.bodySize() + (long) PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }
}
