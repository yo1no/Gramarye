package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct §67 tests over exact Connection and ICommonPacketListener witnesses. */
final class P8ClientPlayEpochTest {
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
    void earlyCatalogTransfersFromPreopenToTheFirstPublishedEpoch() {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var early = catalog(5L);
            assertTrue(state.prepareProfileCatalog(
                            transport.connection(), transport.listener(), early)
                    .isEmpty());
            assertAll(
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(5L, state.pendingCatalogGeneration()),
                    () -> assertTrue(state.combinedQueuedCharge() > 0L));

            var opened = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            assertAll(
                    () -> assertTrue(opened.opened()),
                    () -> assertEquals(1L, opened.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.NONE,
                            opened.maintenance().cleanupDisposition()),
                    () -> assertTrue(
                            opened.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .isEmpty()),
                    () -> assertTrue(opened.catalogDrain().isPresent()),
                    () -> assertEquals(0, execution.clearAllCalls));
            opened.catalogDrain().orElseThrow().run();
            assertAll(
                    () -> assertEquals(5L, state.installedCatalogGeneration()),
                    () -> assertEquals(0L, state.pendingCatalogGeneration()),
                    () -> assertEquals(1, execution.publishCalls));

            var duplicate = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            assertAll(
                    () -> assertFalse(duplicate.opened()),
                    () -> assertEquals(0L, duplicate.publishedGeneration()),
                    () -> assertTrue(duplicate.catalogDrain().isEmpty()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.NONE,
                            duplicate.maintenance().cleanupDisposition()),
                    () -> assertEquals(1L, state.connectionGeneration()),
                    () -> assertEquals(0, execution.clearAllCalls));
        }
    }

    @Test
    void sameConnectionNewPlayListenerStartsANewCatalogGenerationDomain() {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var firstListener = transport.listener();
            var firstOpen = state.onConnectionOpened(
                    transport.connection(), firstListener);
            state.prepareProfileCatalog(
                            transport.connection(), firstListener, catalog(9L))
                    .orElseThrow()
                    .run();
            assertEquals(9L, state.installedCatalogGeneration());

            var secondListener = transport.replacePlayListener();
            assertTrue(state.prepareProfileCatalog(
                            transport.connection(), firstListener, catalog(10L))
                    .isEmpty());
            assertTrue(state.prepareProfileCatalog(
                            transport.connection(), secondListener, catalog(1L))
                    .isEmpty());
            assertAll(
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(1L, state.pendingCatalogGeneration()),
                    () -> assertEquals(0L, state.installedCatalogGeneration()));

            var lateFirstLogout = state.onLoggedOut(
                    transport.connection(), firstListener);
            assertAll(
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            lateFirstLogout.cleanupDisposition()),
                    () -> assertEquals(
                            firstOpen.publishedGeneration(),
                            lateFirstLogout
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertEquals(1L, state.pendingCatalogGeneration()),
                    () -> assertEquals(1, execution.clearAllCalls));

            var secondOpen = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertTrue(secondOpen.opened()),
                    () -> assertEquals(3L, secondOpen.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.NONE,
                            secondOpen.maintenance().cleanupDisposition()),
                    () -> assertTrue(secondOpen.catalogDrain().isPresent()));
            secondOpen.catalogDrain().orElseThrow().run();
            assertAll(
                    () -> assertEquals(1L, state.installedCatalogGeneration()),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(3L)),
                    () -> assertEquals(1, execution.clearAllCalls));

            var staleFirstLogout = state.onLoggedOut(
                    transport.connection(), firstListener);
            assertAll(
                    () -> assertEquals(
                            P8ClientCleanupDisposition.NONE,
                            staleFirstLogout.cleanupDisposition()),
                    () -> assertTrue(
                            staleFirstLogout.invalidatedPublishedGeneration().isEmpty()),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(3L)));
        }
    }

    @Test
    void lateOldEpochDrainReleaseCannotCancelTheNewPreopenCandidate() {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var firstListener = transport.listener();
            state.onConnectionOpened(transport.connection(), firstListener);
            var oldDrain = state.prepareProfileCatalog(
                            transport.connection(), firstListener, catalog(9L))
                    .orElseThrow();

            var secondListener = transport.replacePlayListener();
            assertTrue(state.prepareProfileCatalog(
                            transport.connection(), secondListener, catalog(1L))
                    .isEmpty());
            var retainedCharge = state.combinedQueuedCharge();
            oldDrain.releaseAfterFailedEnqueue();
            oldDrain.run();

            assertAll(
                    () -> assertEquals(1L, state.pendingCatalogGeneration()),
                    () -> assertEquals(retainedCharge, state.combinedQueuedCharge()),
                    () -> assertEquals(0L, state.installedCatalogGeneration()));
            var opened = state.onConnectionOpened(
                    transport.connection(), secondListener);
            opened.catalogDrain().orElseThrow().run();
            assertEquals(1L, state.installedCatalogGeneration());
        }
    }

    @Test
    void lateOldEpochEventTaskCannotOwnTheNewEpochReservation() {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        try (var transport = new P8ClientPlayConnection()) {
            var firstListener = transport.listener();
            state.onConnectionOpened(transport.connection(), firstListener);
            state.onWorldLoaded();
            state.prepareProfileCatalog(
                            transport.connection(), firstListener, catalog(7L))
                    .orElseThrow()
                    .run();
            var lateFirstEvent = state.preparePresentationEvent(
                            transport.connection(), firstListener, event(7L, 41L))
                    .orElseThrow();

            var secondListener = transport.replacePlayListener();
            var secondOpen = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertTrue(secondOpen.opened());
            state.onWorldLoaded();
            state.prepareProfileCatalog(
                            transport.connection(), secondListener, catalog(1L))
                    .orElseThrow()
                    .run();
            var secondPayload = event(1L, 1L);
            state.preparePresentationEvent(
                            transport.connection(), secondListener, secondPayload)
                    .orElseThrow()
                    .run();
            var secondCharge = state.combinedQueuedCharge();
            var secondBodyBytes = state.reservedEventBodyBytes();
            assertAll(
                    () -> assertEquals(1L, state.lastAcceptedSequence()),
                    () -> assertEquals(1, state.pendingEventCount()),
                    () -> assertEquals(1L, state.reservedEventCount()),
                    () -> assertTrue(secondCharge > 0L));

            lateFirstEvent.run();
            lateFirstEvent.releaseAfterFailedEnqueue();
            lateFirstEvent.releaseAfterFailedEnqueue();
            lateFirstEvent.run();
            assertAll(
                    () -> assertEquals(secondCharge, state.combinedQueuedCharge()),
                    () -> assertEquals(secondBodyBytes, state.reservedEventBodyBytes()),
                    () -> assertEquals(1L, state.lastAcceptedSequence()),
                    () -> assertEquals(1, state.pendingEventCount()),
                    () -> assertEquals(1L, state.reservedEventCount()));

            assertEquals(1, state.onClientPostTick());
            lateFirstEvent.releaseAfterFailedEnqueue();
            assertAll(
                    () -> assertEquals(List.of(1L), execution.presentedSequences),
                    () -> assertEquals(0L, state.combinedQueuedCharge()),
                    () -> assertEquals(0, state.pendingEventCount()),
                    () -> assertEquals(0L, state.reservedEventCount()),
                    () -> assertEquals(1L, state.lastAcceptedSequence()));
        }
    }

    @Test
    void closedPreopenTransportIsReleasedByMaintenanceWithoutSyntheticInvalidation() {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        var transport = new P8ClientPlayConnection();
        state.prepareProfileCatalog(
                transport.connection(), transport.listener(), catalog(1L));
        assertTrue(state.combinedQueuedCharge() > 0L);
        transport.close();

        var maintenance = state.maintainTransportLiveness();
        assertAll(
                () -> assertEquals(
                        P8ClientCleanupDisposition.NONE,
                        maintenance.cleanupDisposition()),
                () -> assertTrue(
                        maintenance.invalidatedPublishedGeneration().isEmpty()),
                () -> assertEquals(0L, state.pendingCatalogGeneration()),
                () -> assertEquals(0L, state.combinedQueuedCharge()),
                () -> assertFalse(state.connected()),
                () -> assertEquals(0, execution.clearAllCalls));
    }

    @Test
    void completedOldCleanupCannotGhostClearTheNewestPreopenEpoch() throws Exception {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection();
                var main = Executors.newSingleThreadExecutor()) {
            var firstListener = transport.listener();
            var firstOpen = state.onConnectionOpened(
                    transport.connection(), firstListener);

            var secondListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), secondListener, catalog(2L));
            execution.blockNextClear();
            var secondAttempt = main.submit(() -> state.onConnectionOpened(
                    transport.connection(), secondListener));
            assertTrue(execution.awaitBlockedClear());

            var thirdListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), thirdListener, catalog(3L));
            execution.releaseBlockedClear();
            var cancelledSecond = secondAttempt.get(5L, TimeUnit.SECONDS);
            assertAll(
                    () -> assertFalse(cancelledSecond.opened()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            cancelledSecond.maintenance().cleanupDisposition()),
                    () -> assertEquals(
                            firstOpen.publishedGeneration(),
                            cancelledSecond.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertEquals(3L, state.pendingCatalogGeneration()),
                    () -> assertEquals(1, execution.clearAllCalls));

            var thirdOpen = state.onConnectionOpened(
                    transport.connection(), thirdListener);
            assertAll(
                    () -> assertTrue(thirdOpen.opened()),
                    () -> assertEquals(4L, thirdOpen.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.SUPERSEDED,
                            thirdOpen.maintenance().cleanupDisposition()),
                    () -> assertTrue(thirdOpen.maintenance()
                            .invalidatedPublishedGeneration()
                            .isEmpty()),
                    () -> assertEquals(1, execution.clearAllCalls));
            thirdOpen.catalogDrain().orElseThrow().run();
            assertEquals(3L, state.installedCatalogGeneration());
        }
    }

    private static ProfileCatalogPayload catalog(long generation) {
        return new ProfileCatalogPayload(generation, List.of(
                entry("default_particle", "particle", ProfileChannel.PARTICLE, PARTICLE_JSON),
                entry("default_sound", "sound", ProfileChannel.SOUND, SOUND_JSON),
                entry("default_trail", "trail", ProfileChannel.TRAIL, TRAIL_JSON)));
    }

    private static P8ProfileCatalogEntry entry(
            String profile,
            String type,
            ProfileChannel channel,
            String configuration) {
        var typeId = id(type);
        return new P8ProfileCatalogEntry(
                id(profile), typeId, channel, typeId, 0, configuration);
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
                        Optional.of(id("default_sound")),
                        Optional.of(id("default_particle")),
                        Optional.of(id("default_trail")),
                        Map.of()),
                sequence * 31L,
                sequence);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static final class BlockingExecution
            extends P8ClientPresentationExecutionPort {
        private CountDownLatch clearEntered = new CountDownLatch(0);
        private CountDownLatch clearRelease = new CountDownLatch(0);
        private boolean blockNextClear;
        private int clearAllCalls;
        private int publishCalls;
        private final List<Long> presentedSequences = new java.util.ArrayList<>();

        private synchronized void blockNextClear() {
            clearEntered = new CountDownLatch(1);
            clearRelease = new CountDownLatch(1);
            blockNextClear = true;
        }

        private boolean awaitBlockedClear() throws InterruptedException {
            return clearEntered.await(5L, TimeUnit.SECONDS);
        }

        private void releaseBlockedClear() {
            clearRelease.countDown();
        }

        @Override
        P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            return new PreparedCatalog();
        }

        @Override
        void publishCatalog(P8ClientPreparedCatalog preparedCatalog) {
            publishCalls++;
        }

        @Override
        void onClientTick(
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration,
                long catalogGeneration) {}

        @Override
        P8ClientPresentationHandoffResult present(
                PresentationEventPayload payload,
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            presentedSequences.add(payload.sequence());
            return P8ClientPresentationHandoffResult.PRESENTED;
        }

        @Override
        void clearActive() {}

        @Override
        void clearActiveForCatalogReplacement() {}

        @Override
        void clearAll() {
            final boolean shouldBlock;
            synchronized (this) {
                clearAllCalls++;
                shouldBlock = blockNextClear;
                blockNextClear = false;
                if (shouldBlock) {
                    clearEntered.countDown();
                }
            }
            if (!shouldBlock) {
                return;
            }
            try {
                if (!clearRelease.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to release P8 cleanup");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("P8 cleanup wait was interrupted", failure);
            }
        }
    }

    private static final class PreparedCatalog extends P8ClientPreparedCatalog {}
}
