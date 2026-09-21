package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** State-level acceptance coverage for the §67 typed PLAY-epoch contract. */
final class P8ClientPlayEpochAcceptanceTest {
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
    void typedResultsRejectNullSyntheticAndContradictoryStates() {
        var maintenance = new P8ClientTransportMaintenanceResult(
                P8ClientCleanupDisposition.NONE, OptionalLong.empty());
        var task = new P8ClientDispatchTask() {
            @Override
            public void run() {}

            @Override
            public void releaseAfterFailedEnqueue() {}
        };

        assertAll(
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new P8ClientConnectionOpenResult(
                                true, 1L, null, maintenance)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new P8ClientConnectionOpenResult(
                                true, 1L, Optional.empty(), null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ClientConnectionOpenResult(
                                true, 0L, Optional.empty(), maintenance)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ClientConnectionOpenResult(
                                false, 1L, Optional.empty(), maintenance)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ClientConnectionOpenResult(
                                false, 0L, Optional.of(task), maintenance)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new P8ClientTransportMaintenanceResult(
                                null, OptionalLong.empty())),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> new P8ClientTransportMaintenanceResult(
                                P8ClientCleanupDisposition.NONE, null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ClientTransportMaintenanceResult(
                                P8ClientCleanupDisposition.NONE,
                                OptionalLong.of(0L))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ClientTransportMaintenanceResult(
                                P8ClientCleanupDisposition.CLEARED,
                                OptionalLong.of(-1L))));
    }

    @Test
    void partialAndMismatchedLogoutWitnessesCannotClaimALiveEpoch() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var selected = new P8ClientPlayConnection();
                var unrelated = new P8ClientPlayConnection()) {
            var opened = state.onConnectionOpened(
                    selected.connection(), selected.listener());

            var absent = state.onLoggedOut(null, null);
            var partial = state.onLoggedOut(selected.connection(), null);
            var mismatched = state.onLoggedOut(
                    selected.connection(), unrelated.listener());

            assertAll(
                    () -> assertEquals(1L, opened.publishedGeneration()),
                    () -> assertNoMaintenance(absent),
                    () -> assertNoMaintenance(partial),
                    () -> assertNoMaintenance(mismatched),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(1L)),
                    () -> assertTrue(state.connected()),
                    () -> assertEquals(0, execution.clearAllCalls()));
        }
    }

    @Test
    void differentLiveTransportAmbiguityRejectsNetworkWithoutBlockingLegalReplacementPaths() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var selected = new P8ClientPlayConnection();
                var ambiguous = new P8ClientPlayConnection();
                var mainReplacement = new P8ClientPlayConnection()) {
            assertTrue(state.prepareProfileCatalog(
                            selected.connection(), selected.listener(), catalog(7L))
                    .isEmpty());
            var selectedCharge = state.combinedQueuedCharge();

            assertThrows(
                    P8ClientDispatchUnavailableException.class,
                    () -> state.prepareProfileCatalog(
                            ambiguous.connection(), ambiguous.listener(), catalog(1L)));
            assertAll(
                    () -> assertEquals(7L, state.pendingCatalogGeneration()),
                    () -> assertEquals(selectedCharge, state.combinedQueuedCharge()),
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(0, execution.clearAllCalls()));

            selected.close();
            assertTrue(state.prepareProfileCatalog(
                            ambiguous.connection(), ambiguous.listener(), catalog(2L))
                    .isEmpty());
            var firstPublished = state.onConnectionOpened(
                    ambiguous.connection(), ambiguous.listener());
            assertAll(
                    () -> assertTrue(firstPublished.opened()),
                    () -> assertEquals(1L, firstPublished.publishedGeneration()),
                    () -> assertNoMaintenance(firstPublished.maintenance()),
                    () -> assertTrue(firstPublished.catalogDrain().isPresent()));

            var replacedOnMain = state.onConnectionOpened(
                    mainReplacement.connection(), mainReplacement.listener());
            firstPublished.catalogDrain().orElseThrow().releaseAfterFailedEnqueue();
            assertAll(
                    () -> assertTrue(replacedOnMain.opened()),
                    () -> assertEquals(3L, replacedOnMain.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            replacedOnMain.maintenance().cleanupDisposition()),
                    () -> assertEquals(
                            1L,
                            replacedOnMain.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertTrue(replacedOnMain.catalogDrain().isEmpty()),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(3L)),
                    () -> assertEquals(0L, state.combinedQueuedCharge()),
                    () -> assertEquals(1, execution.clearAllCalls()));
        }
    }

    @Test
    void aClosedSelectedConnectionCanBeRetiredWithoutAListenerWitness() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        var selected = new P8ClientPlayConnection();
        var connection = selected.connection();
        state.onConnectionOpened(connection, selected.listener());
        selected.close();

        var result = state.onLoggedOut(connection, null);

        assertAll(
                () -> assertEquals(
                        P8ClientCleanupDisposition.CLEARED,
                        result.cleanupDisposition()),
                () -> assertEquals(
                        1L, result.invalidatedPublishedGeneration().orElseThrow()),
                () -> assertFalse(state.connected()),
                () -> assertFalse(state.isCurrentPublishedPlayGeneration(1L)),
                () -> assertEquals(1, execution.clearAllCalls()));
    }

    @Test
    void aStaleListenerOnTheSameConnectionCannotRetireTheNewPlayEpoch() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var firstListener = transport.listener();
            state.onConnectionOpened(transport.connection(), firstListener);
            var secondListener = transport.replacePlayListener();
            var secondOpen = state.onConnectionOpened(
                    transport.connection(), secondListener);
            var clearCountBeforeStaleLogout = execution.clearAllCalls();

            var stale = state.onLoggedOut(
                    transport.connection(), firstListener);

            assertAll(
                    () -> assertEquals(3L, secondOpen.publishedGeneration()),
                    () -> assertEquals(
                            1L,
                            secondOpen.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertNoMaintenance(stale),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(3L)),
                    () -> assertEquals(
                            clearCountBeforeStaleLogout,
                            execution.clearAllCalls()));
        }
    }

    @Test
    void openingCleanupBarrierRetainsTheLatestSameEpochCatalog() throws Exception {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection();
                var main = Executors.newSingleThreadExecutor()) {
            state.onConnectionOpened(transport.connection(), transport.listener());
            var secondListener = transport.replacePlayListener();
            execution.blockNextClear();
            var open = main.submit(() -> state.onConnectionOpened(
                    transport.connection(), secondListener));
            try {
                assertTrue(execution.awaitBlockedClear());
                assertTrue(state.prepareProfileCatalog(
                                transport.connection(), secondListener, catalog(2L))
                        .isEmpty());
                assertTrue(state.prepareProfileCatalog(
                                transport.connection(), secondListener, catalog(4L))
                        .isEmpty());
                var latestCharge = state.combinedQueuedCharge();
                assertTrue(state.prepareProfileCatalog(
                                transport.connection(), secondListener, catalog(3L))
                        .isEmpty());
                assertAll(
                        () -> assertEquals(4L, state.pendingCatalogGeneration()),
                        () -> assertEquals(latestCharge, state.combinedQueuedCharge()));
            } finally {
                execution.releaseBlockedClear();
            }

            var result = open.get(5L, TimeUnit.SECONDS);
            assertAll(
                    () -> assertTrue(result.opened()),
                    () -> assertEquals(3L, result.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            result.maintenance().cleanupDisposition()),
                    () -> assertEquals(
                            1L,
                            result.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertTrue(result.catalogDrain().isPresent()));
            result.catalogDrain().orElseThrow().run();
            assertAll(
                    () -> assertEquals(4L, state.installedCatalogGeneration()),
                    () -> assertEquals(0L, state.pendingCatalogGeneration()),
                    () -> assertEquals(1, execution.publishCalls()),
                    () -> assertEquals(1, execution.clearAllCalls()));
        }
    }

    @Test
    void cleanupFailureRetainsItsNotificationAndConsumesTheUnpublishedAttempt()
            throws Exception {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            state.onConnectionOpened(transport.connection(), transport.listener());
            var secondListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), secondListener, catalog(2L));
            var primary = new IllegalStateException("expected cleanup failure");
            execution.failNextClear(primary);

            var thrown = assertThrows(
                    IllegalStateException.class,
                    () -> state.onConnectionOpened(
                            transport.connection(), secondListener));
            assertAll(
                    () -> assertSame(primary, thrown),
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(2L, state.pendingCatalogGeneration()),
                    () -> assertTrue(state.combinedQueuedCharge() > 0L),
                    () -> assertEquals(1, execution.clearAllCalls()));

            var recovered = state.maintainTransportLiveness();
            assertAll(
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            recovered.cleanupDisposition()),
                    () -> assertEquals(
                            1L,
                            recovered.invalidatedPublishedGeneration().orElseThrow()),
                    () -> assertEquals(2, execution.clearAllCalls()),
                    () -> assertEquals(2L, state.pendingCatalogGeneration()));

            var opened = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertTrue(opened.opened()),
                    () -> assertEquals(4L, opened.publishedGeneration()),
                    () -> assertNoMaintenance(opened.maintenance()),
                    () -> assertTrue(opened.catalogDrain().isPresent()));
            opened.catalogDrain().orElseThrow().run();
            assertEquals(2L, state.installedCatalogGeneration());
        }
    }

    @Test
    void closeDuringBlockedOpenNeverPublishesAndConsumesItsAttempt() throws Exception {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection();
                var successor = new P8ClientPlayConnection();
                var main = Executors.newSingleThreadExecutor()) {
            var first = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            assertEquals(1L, first.publishedGeneration());
            var secondListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), secondListener, catalog(2L));
            assertTrue(state.combinedQueuedCharge() > 0L);

            execution.blockNextClear();
            var opening = main.submit(() -> state.onConnectionOpened(
                    transport.connection(), secondListener));
            try {
                assertTrue(execution.awaitBlockedClear());
                transport.close();
            } finally {
                execution.releaseBlockedClear();
            }

            var closedDuringOpen = opening.get(5L, TimeUnit.SECONDS);
            assertAll(
                    () -> assertNoOpen(closedDuringOpen),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            closedDuringOpen.maintenance().cleanupDisposition()),
                    () -> assertEquals(
                            1L,
                            closedDuringOpen.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(1L, state.connectionGeneration()),
                    () -> assertEquals(0L, state.pendingCatalogGeneration()),
                    () -> assertEquals(0L, state.installedCatalogGeneration()),
                    () -> assertEquals(0L, state.combinedQueuedCharge()),
                    () -> assertEquals(0, execution.publishCalls()),
                    () -> assertEquals(1, execution.clearAllCalls()));

            var recovered = state.onConnectionOpened(
                    successor.connection(), successor.listener());
            assertAll(
                    () -> assertTrue(recovered.opened()),
                    () -> assertEquals(4L, recovered.publishedGeneration()),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.SUPERSEDED,
                            recovered.maintenance().cleanupDisposition()),
                    () -> assertTrue(
                            recovered.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .isEmpty()),
                    () -> assertTrue(recovered.catalogDrain().isEmpty()),
                    () -> assertEquals(1, execution.clearAllCalls()));
        }
    }

    @Test
    void reentrantMainPublicationFromClearAllFailsBeforeAnyNewBackendPublication() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var first = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            assertEquals(1L, first.publishedGeneration());
            var secondListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), secondListener, catalog(2L));
            var retainedCharge = state.combinedQueuedCharge();
            var reentrantFailure = new AtomicReference<IllegalStateException>();
            execution.runDuringNextClear(() -> {
                try {
                    state.onConnectionOpened(
                            transport.connection(), secondListener);
                } catch (IllegalStateException failure) {
                    reentrantFailure.set(failure);
                    throw failure;
                }
            });

            var outerFailure = assertThrows(
                    IllegalStateException.class,
                    () -> state.onConnectionOpened(
                            transport.connection(), secondListener));
            assertAll(
                    () -> assertSame(reentrantFailure.get(), outerFailure),
                    () -> assertEquals(
                            "P8 PLAY publication cannot re-enter an active transport cleanup",
                            outerFailure.getMessage()),
                    () -> assertFalse(state.connected()),
                    () -> assertEquals(1L, state.connectionGeneration()),
                    () -> assertEquals(2L, state.pendingCatalogGeneration()),
                    () -> assertEquals(retainedCharge, state.combinedQueuedCharge()),
                    () -> assertEquals(0, execution.publishCalls()),
                    () -> assertEquals(1, execution.clearAllCalls()));

            var maintenance = state.maintainTransportLiveness();
            assertAll(
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            maintenance.cleanupDisposition()),
                    () -> assertEquals(
                            1L,
                            maintenance.invalidatedPublishedGeneration().orElseThrow()),
                    () -> assertEquals(2L, state.pendingCatalogGeneration()),
                    () -> assertEquals(retainedCharge, state.combinedQueuedCharge()),
                    () -> assertEquals(0, execution.publishCalls()),
                    () -> assertEquals(2, execution.clearAllCalls()));

            var recovered = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertTrue(recovered.opened()),
                    () -> assertEquals(4L, recovered.publishedGeneration()),
                    () -> assertNoMaintenance(recovered.maintenance()),
                    () -> assertTrue(recovered.catalogDrain().isPresent()),
                    () -> assertEquals(0, execution.publishCalls()));
            recovered.catalogDrain().orElseThrow().run();
            assertAll(
                    () -> assertEquals(2L, state.installedCatalogGeneration()),
                    () -> assertEquals(0L, state.combinedQueuedCharge()),
                    () -> assertEquals(1, execution.publishCalls()));
        }
    }

    @Test
    void publishedInvalidationMarkersAdvanceExactlyOnceAndDuplicatesAreNoOps() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        try (var transport = new P8ClientPlayConnection()) {
            var first = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            var firstDuplicate = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            var secondListener = transport.replacePlayListener();
            var second = state.onConnectionOpened(
                    transport.connection(), secondListener);
            var secondDuplicate = state.onConnectionOpened(
                    transport.connection(), secondListener);
            var thirdListener = transport.replacePlayListener();
            var third = state.onConnectionOpened(
                    transport.connection(), thirdListener);

            assertAll(
                    () -> assertEquals(1L, first.publishedGeneration()),
                    () -> assertNoOpen(firstDuplicate),
                    () -> assertNoMaintenance(firstDuplicate.maintenance()),
                    () -> assertEquals(3L, second.publishedGeneration()),
                    () -> assertEquals(
                            1L,
                            second.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertNoOpen(secondDuplicate),
                    () -> assertNoMaintenance(secondDuplicate.maintenance()),
                    () -> assertEquals(5L, third.publishedGeneration()),
                    () -> assertEquals(
                            3L,
                            third.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertEquals(2, execution.clearAllCalls()),
                    () -> assertTrue(state.isCurrentPublishedPlayGeneration(5L)));
        }
    }

    @Test
    void connectionCounterExhaustionStillCleansWithoutPublishingOrWrapping() {
        var execution = new RecordingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.setGenerationCountersForTest(Long.MAX_VALUE - 1L, 0L, 0L);
        try (var transport = new P8ClientPlayConnection()) {
            var exhaustedPublication = state.onConnectionOpened(
                    transport.connection(), transport.listener());
            assertEquals(Long.MAX_VALUE, exhaustedPublication.publishedGeneration());

            var secondListener = transport.replacePlayListener();
            state.prepareProfileCatalog(
                    transport.connection(), secondListener, catalog(1L));
            assertTrue(state.combinedQueuedCharge() > 0L);

            var refused = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertNoOpen(refused),
                    () -> assertEquals(
                            P8ClientCleanupDisposition.CLEARED,
                            refused.maintenance().cleanupDisposition()),
                    () -> assertEquals(
                            Long.MAX_VALUE,
                            refused.maintenance()
                                    .invalidatedPublishedGeneration()
                                    .orElseThrow()),
                    () -> assertFalse(state.connected()),
                    () -> assertFalse(
                            state.isCurrentPublishedPlayGeneration(Long.MAX_VALUE)),
                    () -> assertEquals(Long.MAX_VALUE, state.connectionGeneration()),
                    () -> assertEquals(0L, state.pendingCatalogGeneration()),
                    () -> assertEquals(0L, state.combinedQueuedCharge()),
                    () -> assertEquals(1, execution.clearAllCalls()));

            var repeated = state.onConnectionOpened(
                    transport.connection(), secondListener);
            assertAll(
                    () -> assertNoOpen(repeated),
                    () -> assertNoMaintenance(repeated.maintenance()),
                    () -> assertEquals(Long.MAX_VALUE, state.connectionGeneration()),
                    () -> assertEquals(1, execution.clearAllCalls()));
        }
    }

    private static void assertNoOpen(P8ClientConnectionOpenResult result) {
        assertAll(
                () -> assertFalse(result.opened()),
                () -> assertEquals(0L, result.publishedGeneration()),
                () -> assertTrue(result.catalogDrain().isEmpty()));
    }

    private static void assertNoMaintenance(
            P8ClientTransportMaintenanceResult result) {
        assertAll(
                () -> assertEquals(
                        P8ClientCleanupDisposition.NONE,
                        result.cleanupDisposition()),
                () -> assertTrue(result.invalidatedPublishedGeneration().isEmpty()));
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static final class RecordingExecution
            extends P8ClientPresentationExecutionPort {
        private final AtomicInteger clearAllCalls = new AtomicInteger();
        private final AtomicInteger publishCalls = new AtomicInteger();
        private final AtomicBoolean blockNextClear = new AtomicBoolean();
        private final AtomicReference<RuntimeException> nextClearFailure =
                new AtomicReference<>();
        private final AtomicReference<Runnable> nextClearAction =
                new AtomicReference<>();
        private volatile CountDownLatch clearEntered = new CountDownLatch(0);
        private volatile CountDownLatch clearRelease = new CountDownLatch(0);

        private void blockNextClear() {
            clearEntered = new CountDownLatch(1);
            clearRelease = new CountDownLatch(1);
            blockNextClear.set(true);
        }

        private boolean awaitBlockedClear() throws InterruptedException {
            return clearEntered.await(5L, TimeUnit.SECONDS);
        }

        private void releaseBlockedClear() {
            clearRelease.countDown();
        }

        private void failNextClear(RuntimeException failure) {
            nextClearFailure.set(failure);
        }

        private void runDuringNextClear(Runnable action) {
            nextClearAction.set(action);
        }

        private int clearAllCalls() {
            return clearAllCalls.get();
        }

        private int publishCalls() {
            return publishCalls.get();
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
            publishCalls.incrementAndGet();
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
            return P8ClientPresentationHandoffResult.PRESENTED;
        }

        @Override
        void clearActive() {}

        @Override
        void clearActiveForCatalogReplacement() {}

        @Override
        void clearAll() {
            clearAllCalls.incrementAndGet();
            var action = nextClearAction.getAndSet(null);
            if (action != null) {
                action.run();
            }
            var failure = nextClearFailure.getAndSet(null);
            if (failure != null) {
                throw failure;
            }
            if (!blockNextClear.compareAndSet(true, false)) {
                return;
            }
            clearEntered.countDown();
            try {
                if (!clearRelease.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to release P8 cleanup");
                }
            } catch (InterruptedException failureDuringWait) {
                Thread.currentThread().interrupt();
                throw new AssertionError(
                        "P8 cleanup wait was interrupted", failureDuringWait);
            }
        }
    }

    private static final class PreparedCatalog extends P8ClientPreparedCatalog {}
}
