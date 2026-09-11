package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** Adversarial publication tests for the client-state/execution two-phase boundary. */
final class P8ClientPresentationStateConcurrencyTest {
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
    private static final String MAX_ENVELOPE_JSON = maximumEnvelopeJson();

    @Test
    void supersededInFlightCatalogRemainsExecutableWhenTheNewDrainFailsToEnqueue()
            throws Exception {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();

        var first = catalog(1L);
        var firstDrain = state.prepareProfileCatalog(first).orElseThrow();
        var drainFailure = new AtomicReference<Throwable>();
        var drainThread = new Thread(() -> {
            try {
                firstDrain.run();
            } catch (Throwable failure) {
                drainFailure.set(failure);
            }
        }, "p8-blocked-catalog-bind");
        drainThread.start();
        assertTrue(execution.awaitBlocked(), "catalog bind never reached the blocking seam");
        assertEquals(0L, state.combinedQueuedCharge());
        assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());

        var network = Executors.newSingleThreadExecutor();
        NetworkWork networkWork;
        var second = catalog(2L);
        try {
            networkWork = network.submit(() -> new NetworkWork(
                            state.prepareProfileCatalog(second).orElseThrow(),
                            state.preparePresentationEvent(event(1L, 1L)).orElseThrow()))
                    .get(1L, TimeUnit.SECONDS);
            assertEquals(
                    catalogCharge(second) + eventCharge(event(1L, 1L)),
                    state.combinedQueuedCharge());
            assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());
            networkWork.newCatalogDrain().releaseAfterFailedEnqueue();
            networkWork.newCatalogDrain().releaseAfterFailedEnqueue();
            assertEquals(
                    eventCharge(event(1L, 1L)),
                    state.combinedQueuedCharge());
            assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());
        } finally {
            execution.release();
            network.shutdownNow();
        }

        drainThread.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(drainThread.isAlive(), "blocked catalog drain did not terminate");
        assertNull(drainFailure.get());
        assertEquals(1L, state.installedCatalogGeneration());
        assertSame(first.snapshot(), state.installedCatalogSnapshot());
        assertSame(first.snapshot(), execution.boundCatalog());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(eventCharge(event(1L, 1L)), state.combinedQueuedCharge());

        networkWork.oldEventTask().run();
        assertEquals(1, state.onClientPostTick());
        assertEquals(1, execution.presentationCount());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void blockedLifecycleCleanupDoesNotBlockNetworkPrepare() throws Exception {
        var execution = new BlockingCleanupExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        execution.blockNextClearActive();

        var cleanupFailure = new AtomicReference<Throwable>();
        var cleanupThread = new Thread(() -> {
            try {
                state.onWorldLoaded();
            } catch (Throwable failure) {
                cleanupFailure.set(failure);
            }
        }, "p8-blocked-lifecycle-cleanup");
        cleanupThread.start();
        assertTrue(execution.awaitBlocked(), "lifecycle cleanup never reached the blocking seam");

        var network = Executors.newSingleThreadExecutor();
        P8ClientDispatchTask drain;
        try {
            drain = network.submit(() -> state.prepareProfileCatalog(catalog(1L)))
                    .get(1L, TimeUnit.SECONDS)
                    .orElseThrow();
            drain.releaseAfterFailedEnqueue();
            drain.releaseAfterFailedEnqueue();
        } finally {
            execution.release();
            network.shutdownNow();
        }

        cleanupThread.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(cleanupThread.isAlive(), "blocked lifecycle cleanup did not terminate");
        assertNull(cleanupFailure.get());
        assertTrue(state.worldReady());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void fullCatalogOverlapCoalescesAndInstallsTheLatestWithoutAnotherPacket()
            throws Exception {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var first = maximumEnvelopeCatalog(1L);
        var second = maximumEnvelopeCatalog(2L);
        var third = maximumEnvelopeCatalog(3L);
        assertEquals(PresentationLimits.MAX_PROFILE_INSTANCES, first.entries().size());
        assertEquals(PresentationLimits.MAX_PROFILE_INSTANCES, second.entries().size());
        assertEquals(PresentationLimits.MAX_PROFILE_INSTANCES, third.entries().size());
        assertTrue(first.bodySize()
                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES);
        assertTrue(second.bodySize()
                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES);
        assertTrue(third.bodySize()
                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES);
        assertTrue(
                catalogCharge(first) * 2L
                        > PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES);

        var drain = state.prepareProfileCatalog(first).orElseThrow();
        var drainFailure = new AtomicReference<Throwable>();
        var drainThread = new Thread(() -> {
            try {
                drain.run();
            } catch (Throwable failure) {
                drainFailure.set(failure);
            }
        }, "p8-full-catalog-bind");
        drainThread.start();
        assertTrue(execution.awaitBlocked(), "full catalog bind never reached the blocking seam");
        assertEquals(0L, state.combinedQueuedCharge());
        assertEquals(first.bodySize(), state.inFlightCatalogBodyBytes());
        assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());

        var network = Executors.newSingleThreadExecutor();
        P8ClientDispatchTask latestDrain;
        P8ClientDispatchTask latestEvent;
        try {
            latestDrain = network.submit(() -> state.prepareProfileCatalog(second))
                    .get(1L, TimeUnit.SECONDS)
                    .orElseThrow();
            assertEquals(2L, state.pendingCatalogGeneration());
            assertEquals(catalogCharge(second), state.combinedQueuedCharge());
            assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());

            assertTrue(network.submit(() -> state.prepareProfileCatalog(third))
                    .get(1L, TimeUnit.SECONDS)
                    .isEmpty());
            assertEquals(3L, state.pendingCatalogGeneration());
            assertEquals(catalogCharge(third), state.combinedQueuedCharge());
            assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());
            assertTrue(network.submit(() -> state.prepareProfileCatalog(catalog(3L)))
                    .get(1L, TimeUnit.SECONDS)
                    .isEmpty());
            assertMalformedNewerCatalogDoesNotReplace(state, third);
            assertEquals(3L, state.pendingCatalogGeneration());
            assertEquals(catalogCharge(third), state.combinedQueuedCharge());
            latestEvent = network.submit(() ->
                            state.preparePresentationEvent(event(3L, 1L)).orElseThrow())
                    .get(1L, TimeUnit.SECONDS);
            assertTrue(
                    state.combinedQueuedCharge()
                            <= PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES);
        } finally {
            execution.release();
            network.shutdownNow();
        }

        drainThread.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(drainThread.isAlive(), "full catalog drain did not terminate");
        assertNull(drainFailure.get());
        assertEquals(1L, state.installedCatalogGeneration());
        assertSame(first.snapshot(), state.installedCatalogSnapshot());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(
                catalogCharge(third) + eventCharge(event(3L, 1L)),
                state.combinedQueuedCharge());

        latestDrain.run();

        assertEquals(3L, state.installedCatalogGeneration());
        assertSame(third.snapshot(), state.installedCatalogSnapshot());
        assertSame(third.snapshot(), execution.boundCatalog());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(eventCharge(event(3L, 1L)), state.combinedQueuedCharge());

        latestEvent.run();
        assertEquals(1, state.onClientPostTick());
        assertEquals(1, execution.presentationCount());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void logoutDuringReplacementBindRetainsOnlyTheChargedIncomingCatalog() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var active = maximumEnvelopeCatalog(1L);
        var replacement = maximumEnvelopeCatalog(2L);
        state.prepareProfileCatalog(active).orElseThrow().run();
        assertSame(active.snapshot(), state.installedCatalogSnapshot());

        execution.onNextReplace(() -> {
            assertSame(active.snapshot(), state.installedCatalogSnapshot());
            assertEquals(catalogCharge(replacement), state.inFlightCatalogPacketCharge());
            state.onLoggedOut();
            assertNull(state.installedCatalogSnapshot());
            assertNull(execution.boundCatalog());
            assertEquals(catalogCharge(replacement), state.inFlightCatalogPacketCharge());
            assertEquals(0L, state.combinedQueuedCharge());
            assertCatalogBindAttemptHasNoInstalledCatalogReference();
        });

        state.prepareProfileCatalog(replacement).orElseThrow().run();

        assertFalse(state.connected());
        assertNull(state.installedCatalogSnapshot());
        assertNull(execution.boundCatalog());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void logoutDuringNestedResourceApplyRetainsOnlyTheChargedIncomingCatalog() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var active = maximumEnvelopeCatalog(1L);
        var replacement = maximumEnvelopeCatalog(2L);
        state.prepareProfileCatalog(active).orElseThrow().run();
        assertSame(active.snapshot(), state.installedCatalogSnapshot());

        execution.onNextReplace(() -> {
            execution.onNextReplace(() -> {
                assertSame(active.snapshot(), state.installedCatalogSnapshot());
                assertEquals(catalogCharge(replacement), state.inFlightCatalogPacketCharge());
                state.onLoggedOut();
                assertNull(state.installedCatalogSnapshot());
                assertNull(execution.boundCatalog());
                assertEquals(catalogCharge(replacement), state.inFlightCatalogPacketCharge());
                assertEquals(0L, state.combinedQueuedCharge());
                assertResourceApplyAttemptHasNoPriorInstalledCatalogReference();
            });
            state.onResourceIndexApplied(
                    new P8ClientResourceIndex(List.of(id("nested_resource"))));
        });

        state.prepareProfileCatalog(replacement).orElseThrow().run();

        assertFalse(state.connected());
        assertNull(state.installedCatalogSnapshot());
        assertNull(execution.boundCatalog());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void failedOlderBindReleasesItsRetentionWithoutClearingNewerQueuedWork()
            throws Exception {
        for (var expectedFailure : List.<Throwable>of(
                new IllegalStateException("catalog bind failed"),
                new AssertionError("catalog bind errored"))) {
            var execution = new BlockingFailureExecution(expectedFailure);
            var state = new P8ClientPresentationState(() -> true, execution);
            state.onResourceIndexApplied(P8ClientResourceIndex.empty());
            state.onConnectionOpened();
            state.onWorldLoaded();
            var first = catalog(1L);
            var second = catalog(2L);
            var firstDrain = state.prepareProfileCatalog(first).orElseThrow();
            var observedFailure = new AtomicReference<Throwable>();
            var drainThread = new Thread(() -> {
                try {
                    firstDrain.run();
                } catch (Throwable failure) {
                    observedFailure.set(failure);
                }
            }, "p8-failed-old-catalog-bind");
            drainThread.start();
            assertTrue(execution.awaitBlocked(), "catalog bind never reached failure seam");

            var latestDrain = state.prepareProfileCatalog(second).orElseThrow();
            var latestEvent = state.preparePresentationEvent(event(2L, 1L)).orElseThrow();
            assertEquals(catalogCharge(first), state.inFlightCatalogPacketCharge());
            assertEquals(
                    catalogCharge(second) + eventCharge(event(2L, 1L)),
                    state.combinedQueuedCharge());

            execution.release();
            drainThread.join(TimeUnit.SECONDS.toMillis(5L));

            assertFalse(drainThread.isAlive(), "failed catalog bind did not terminate");
            assertSame(expectedFailure, observedFailure.get());
            assertEquals(0L, state.inFlightCatalogPacketCharge());
            assertEquals(2L, state.pendingCatalogGeneration());
            assertEquals(
                    catalogCharge(second) + eventCharge(event(2L, 1L)),
                    state.combinedQueuedCharge());

            latestDrain.run();
            latestEvent.run();
            assertEquals(1, state.onClientPostTick());
            assertEquals(1, execution.presentationCount());
            assertSame(second.snapshot(), state.installedCatalogSnapshot());
            assertEquals(0L, state.inFlightCatalogPacketCharge());
            assertEquals(0L, state.combinedQueuedCharge());
        }
    }

    @Test
    void failedOldConnectionBindPreservesSameGenerationCatalogAndEventInNewEpoch() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var stale = catalog(1L);
        var staleDrain = state.prepareProfileCatalog(stale).orElseThrow();
        var currentDrain = new AtomicReference<P8ClientDispatchTask>();
        var currentEvent = new AtomicReference<P8ClientDispatchTask>();
        var current = catalog(1L);
        var expectedFailure = new IllegalStateException("expected old-epoch failure");
        execution.onNextReplace(() -> {
            state.onLoggedOut();
            state.onConnectionOpened();
            state.onWorldLoaded();
            currentDrain.set(state.prepareProfileCatalog(current).orElseThrow());
            currentEvent.set(
                    state.preparePresentationEvent(event(1L, 1L)).orElseThrow());
            assertEquals(catalogCharge(stale), state.inFlightCatalogPacketCharge());
            assertEquals(
                    catalogCharge(current) + eventCharge(event(1L, 1L)),
                    state.combinedQueuedCharge());
        });
        execution.failAfterNextReplace(expectedFailure);

        var observedFailure = assertThrows(
                IllegalStateException.class, staleDrain::run);

        assertSame(expectedFailure, observedFailure);
        assertTrue(state.connected());
        assertEquals(0L, state.installedCatalogGeneration());
        assertNull(state.installedCatalogSnapshot());
        assertEquals(1L, state.pendingCatalogGeneration());
        assertEquals(
                catalogCharge(current) + eventCharge(event(1L, 1L)),
                state.combinedQueuedCharge());
        assertEquals(0L, state.inFlightCatalogPacketCharge());

        currentDrain.get().run();
        currentEvent.get().run();

        assertEquals(1, state.onClientPostTick());
        assertEquals(1, execution.presentationCount());
        assertEquals(1L, state.installedCatalogGeneration());
        assertSame(current.snapshot(), state.installedCatalogSnapshot());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void worldChangeDuringCatalogBindDoesNotInvalidateConnectionScopedCatalog() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var catalog = catalog(1L);
        var firstWorld = state.worldGeneration();
        execution.onNextReplace(state::onWorldLoaded);

        state.prepareProfileCatalog(catalog).orElseThrow().run();

        assertTrue(state.worldReady());
        assertTrue(state.worldGeneration() > firstWorld);
        assertEquals(1L, state.installedCatalogGeneration());
        assertSame(catalog.snapshot(), state.installedCatalogSnapshot());
        assertSame(catalog.snapshot(), execution.boundCatalog());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void resourceChangeDuringCatalogBindInstallsItAgainstTheLatestIndexWithoutAnotherPacket() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        var firstResources = new P8ClientResourceIndex(List.of(id("first_resource")));
        var latestResources = new P8ClientResourceIndex(List.of(id("latest_resource")));
        state.onResourceIndexApplied(firstResources);
        state.onConnectionOpened();
        state.onWorldLoaded();
        var latest = catalog(1L);
        execution.onNextReplace(() -> {
            assertEquals(catalogCharge(latest), state.inFlightCatalogPacketCharge());
            assertEquals(0L, state.combinedQueuedCharge());
            state.onResourceIndexApplied(latestResources);
        });

        state.prepareProfileCatalog(latest).orElseThrow().run();

        assertEquals(1L, state.installedCatalogGeneration());
        assertSame(latest.snapshot(), state.installedCatalogSnapshot());
        assertSame(latest.snapshot(), execution.boundCatalog());
        assertSame(latestResources, execution.lastResources());
        assertEquals(state.resourceGeneration(), state.catalogEvaluationResourceGeneration());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void maximumResourceApplyFailureRetainsActiveAndDiscardsInaccessibleCandidate() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        var activeResources = maximumResourceIndex("active_resource");
        var candidateResources = maximumResourceIndex("candidate_resource");
        state.onResourceIndexApplied(activeResources);
        state.onConnectionOpened();
        state.onWorldLoaded();
        var activeCatalog = catalog(1L);
        state.prepareProfileCatalog(activeCatalog).orElseThrow().run();
        var activeResourceGeneration = state.resourceGeneration();
        var expectedFailure = new IllegalStateException("expected maximum resource apply failure");
        execution.onNextReplace(() -> {
            assertSame(activeResources, state.resourceIndexForTesting());
            assertSame(candidateResources, state.resourceApplyCandidateForTesting());
            assertEquals(
                    PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES,
                    state.resourceIndexForTesting().resourceIds().size());
            assertEquals(
                    PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES,
                    state.resourceApplyCandidateForTesting().resourceIds().size());
            assertSame(candidateResources, execution.lastResources());
        });
        execution.failAfterNextReplace(expectedFailure);

        var observed = assertThrows(
                IllegalStateException.class,
                () -> state.onResourceIndexApplied(candidateResources));

        assertSame(expectedFailure, observed);
        assertSame(activeResources, state.resourceIndexForTesting());
        assertNull(state.resourceApplyCandidateForTesting());
        assertEquals(activeResourceGeneration, state.resourceGeneration());
        assertTrue(state.resourceReady());
        assertSame(activeCatalog.snapshot(), state.installedCatalogSnapshot());
        assertSame(activeCatalog.snapshot(), execution.boundCatalog());
    }

    @Test
    void supersededBindErrorPreservesItsPublishedCatalogAndCurrentEvent() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(new P8ClientResourceIndex(List.of(id("first_resource"))));
        state.onConnectionOpened();
        state.onWorldLoaded();
        var latestResources = new P8ClientResourceIndex(List.of(id("latest_resource")));
        var latest = catalog(1L);
        var currentEvent = new AtomicReference<P8ClientDispatchTask>();
        var expectedFailure = new Error("expected superseded bind failure");
        execution.onNextReplace(() -> {
            state.onResourceIndexApplied(latestResources);
            currentEvent.set(
                    state.preparePresentationEvent(event(1L, 1L)).orElseThrow());
            assertEquals(1L, state.installedCatalogGeneration());
            assertEquals(catalogCharge(latest), state.inFlightCatalogPacketCharge());
            assertEquals(eventCharge(event(1L, 1L)), state.combinedQueuedCharge());
        });
        execution.failAfterNextReplace(expectedFailure);

        var observedFailure = assertThrows(
                Error.class,
                () -> state.prepareProfileCatalog(latest).orElseThrow().run());

        assertSame(expectedFailure, observedFailure);
        assertSame(latest.snapshot(), state.installedCatalogSnapshot());
        assertSame(latest.snapshot(), execution.boundCatalog());
        assertSame(latestResources, execution.lastResources());
        assertEquals(state.resourceGeneration(), state.catalogEvaluationResourceGeneration());
        assertEquals(0L, state.inFlightCatalogPacketCharge());
        assertEquals(eventCharge(event(1L, 1L)), state.combinedQueuedCharge());

        currentEvent.get().run();
        assertEquals(1, state.onClientPostTick());
        assertEquals(1, execution.presentationCount());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void connectionChangeAfterResourceBindPreventsStaleResourcePublication() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        var retained = new P8ClientResourceIndex(List.of(id("retained_resource")));
        state.onResourceIndexApplied(retained);
        state.onConnectionOpened();
        state.onWorldLoaded();
        state.prepareProfileCatalog(catalog(1L)).orElseThrow().run();
        var retainedGeneration = state.resourceGeneration();

        var staleReplacement = new P8ClientResourceIndex(List.of(id("stale_resource")));
        execution.onNextReplace(state::onConnectionOpened);
        state.onResourceIndexApplied(staleReplacement);

        assertEquals(retainedGeneration, state.resourceGeneration());
        assertTrue(state.resourceReady());
        assertEquals(0L, state.installedCatalogGeneration());

        state.prepareProfileCatalog(catalog(2L)).orElseThrow().run();
        assertSame(retained, execution.lastResources());
        assertEquals(retainedGeneration, state.catalogEvaluationResourceGeneration());
    }

    @Test
    void catalogReplacementPreservesParticleCreditWhileLifecycleCleanupResetsIt() {
        var execution = new CleanupRoutingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        state.prepareProfileCatalog(catalog(1L)).orElseThrow().run();

        execution.seedActiveWork();
        state.prepareProfileCatalog(catalog(2L)).orElseThrow().run();
        assertEquals(0, execution.activeOwners());
        assertEquals(1L, execution.liveParticleCredits());

        execution.seedActiveWork();
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        assertEquals(0, execution.activeOwners());
        assertEquals(0L, execution.liveParticleCredits());

        execution.seedActiveWork();
        state.onWorldLoaded();
        assertEquals(0, execution.activeOwners());
        assertEquals(0L, execution.liveParticleCredits());

        execution.seedActiveWork();
        state.onLoggedOut();
        assertEquals(0, execution.activeOwners());
        assertEquals(0L, execution.liveParticleCredits());
    }

    private static ProfileCatalogPayload catalog(long generation) {
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
        return new ProfileCatalogPayload(generation, entries);
    }

    private static ProfileCatalogPayload maximumEnvelopeCatalog(long generation) {
        var entries = new ArrayList<P8ProfileCatalogEntry>(
                PresentationLimits.MAX_PROFILE_INSTANCES);
        entries.addAll(catalog(generation).entries());
        for (var channel : ProfileChannel.values()) {
            for (var index = 0;
                    index < PresentationLimits.MAX_PROFILE_INSTANCES_PER_CHANNEL - 1;
                    index++) {
                var type = switch (channel) {
                    case PARTICLE -> PARTICLE_TYPE;
                    case SOUND -> SOUND_TYPE;
                    case TRAIL -> TRAIL_TYPE;
                };
                entries.add(new P8ProfileCatalogEntry(
                        id("bulk_" + channel.wireCode() + "_" + index),
                        type,
                        channel,
                        type,
                        0,
                        MAX_ENVELOPE_JSON));
            }
        }
        entries.sort(Comparator.comparing(P8ProfileCatalogEntry::profileId));
        return new ProfileCatalogPayload(generation, entries);
    }

    private static P8ClientResourceIndex maximumResourceIndex(String prefix) {
        var resources = new ArrayList<ResourceLocation>(
                PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES);
        for (var index = 0;
                index < PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES;
                index++) {
            resources.add(id("%s_%03d".formatted(prefix, index)));
        }
        return new P8ClientResourceIndex(resources);
    }

    private static String maximumEnvelopeJson() {
        var json = new StringBuilder("{");
        for (var index = 0; index < 20; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("\"p");
            if (index < 10) {
                json.append('0');
            }
            json.append(index).append("\":\"").append("a".repeat(90)).append('"');
        }
        return json.append('}').toString();
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
        return payload.bodySize()
                + (long) PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES;
    }

    private static void assertMalformedNewerCatalogDoesNotReplace(
            P8ClientPresentationState state, ProfileCatalogPayload retained) {
        var buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            buffer.writeByte(0);
            buffer.writeLong(retained.catalogGeneration() + 1L);
            buffer.writeVarInt(0);
            assertThrows(
                    DecoderException.class,
                    () -> P8PayloadCodecSupport.decodeProfileCatalog(
                            buffer, P8S2TestFixtures.registry()));
        } finally {
            assertTrue(buffer.release());
        }
    }

    private static void assertCatalogBindAttemptHasNoInstalledCatalogReference() {
        var attemptType = Arrays.stream(P8ClientPresentationState.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CatalogBindAttempt"))
                .findFirst()
                .orElseThrow();
        assertTrue(attemptType.isRecord());
        assertEquals(
                List.of("replacementCatalog"),
                Arrays.stream(attemptType.getRecordComponents())
                        .filter(component -> component.getType().getSimpleName()
                                .equals("InstalledCatalog"))
                        .map(component -> component.getName())
                        .toList());
        assertTrue(Arrays.stream(attemptType.getRecordComponents())
                .anyMatch(component -> component.getType() == long.class
                        && component.getName().equals("priorInstalledCatalogGeneration")));
    }

    private static void assertResourceApplyAttemptHasNoPriorInstalledCatalogReference() {
        var attemptType = Arrays.stream(P8ClientPresentationState.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ResourceApplyAttempt"))
                .findFirst()
                .orElseThrow();
        assertTrue(attemptType.isRecord());
        assertEquals(
                List.of("replacementCatalog"),
                Arrays.stream(attemptType.getRecordComponents())
                        .filter(component -> component.getType().getSimpleName()
                                .equals("InstalledCatalog"))
                        .map(component -> component.getName())
                        .toList());
        assertTrue(Arrays.stream(attemptType.getRecordComponents())
                .anyMatch(component -> component.getType() == long.class
                        && component.getName().equals("priorInstalledCatalogGeneration")));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private abstract static class TestExecution extends P8ClientPresentationExecutionPort {
        private P8ProfileCatalogSnapshot boundCatalog;
        private int presentationCount;

        @Override
        public void onClientTick(
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration,
                long catalogGeneration) {}

        @Override
        public P8ClientPresentationHandoffResult present(
                PresentationEventPayload payload,
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            if (catalog != boundCatalog) {
                throw new AssertionError("state catalog diverged from execution catalog");
            }
            presentationCount++;
            return P8ClientPresentationHandoffResult.PRESENTED;
        }

        @Override
        public void clearActive() {}

        @Override
        void clearActiveForCatalogReplacement() {}

        @Override
        public void clearAll() {
            boundCatalog = null;
        }

        final P8ClientPreparedCatalog prepared(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources) {
            return new TestPreparedCatalog(catalog, resources);
        }

        @Override
        final void publishCatalog(P8ClientPreparedCatalog preparedCatalog) {
            if (preparedCatalog instanceof TestPreparedCatalog prepared
                    && prepared.owner == this) {
                boundCatalog = prepared.catalog;
                Objects.requireNonNull(prepared.resources, "resources");
            }
        }

        final P8ProfileCatalogSnapshot boundCatalog() {
            return boundCatalog;
        }

        final int presentationCount() {
            return presentationCount;
        }

        private final class TestPreparedCatalog extends P8ClientPreparedCatalog {
            private final TestExecution owner;
            private final P8ProfileCatalogSnapshot catalog;
            private final P8ClientResourceIndex resources;

            private TestPreparedCatalog(
                    P8ProfileCatalogSnapshot catalog,
                    P8ClientResourceIndex resources) {
                owner = TestExecution.this;
                this.catalog = Objects.requireNonNull(catalog, "catalog");
                this.resources = Objects.requireNonNull(resources, "resources");
            }
        }
    }

    private static final class BlockingExecution extends TestExecution {
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private boolean blockFirst = true;

        @Override
        public P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(resources, "resources");
            if (!blockFirst) {
                return prepared(catalog, resources);
            }
            blockFirst = false;
            blocked.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release blocked catalog bind");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("blocked catalog bind was interrupted", failure);
            }
            return prepared(catalog, resources);
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5L, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class BlockingCleanupExecution extends TestExecution {
        private CountDownLatch blocked = new CountDownLatch(0);
        private CountDownLatch release = new CountDownLatch(0);
        private boolean blockNext;

        @Override
        public P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            return prepared(catalog, resources);
        }

        @Override
        public void clearActive() {
            if (!blockNext) {
                return;
            }
            blockNext = false;
            blocked.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release blocked lifecycle cleanup");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("blocked lifecycle cleanup was interrupted", failure);
            }
        }

        private void blockNextClearActive() {
            blocked = new CountDownLatch(1);
            release = new CountDownLatch(1);
            blockNext = true;
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5L, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class BlockingFailureExecution extends TestExecution {
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final Throwable failure;
        private boolean failFirst = true;

        private BlockingFailureExecution(Throwable failure) {
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        @Override
        public P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            if (!failFirst) {
                return prepared(catalog, resources);
            }
            failFirst = false;
            blocked.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new AssertionError("test did not release failed catalog bind");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("failed catalog bind was interrupted", interrupted);
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw (Error) failure;
        }

        private boolean awaitBlocked() throws InterruptedException {
            return blocked.await(5L, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class ReentrantExecution extends TestExecution {
        private Runnable onNextReplace;
        private Throwable failureAfterNextReplace;
        private P8ClientResourceIndex lastResources;

        @Override
        public P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            Objects.requireNonNull(catalog, "catalog");
            lastResources = Objects.requireNonNull(resources, "resources");
            var callback = onNextReplace;
            onNextReplace = null;
            var failure = callback == null ? null : failureAfterNextReplace;
            if (callback != null) {
                failureAfterNextReplace = null;
            }
            if (callback != null) {
                callback.run();
            }
            if (failure instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            return prepared(catalog, resources);
        }

        private void onNextReplace(Runnable callback) {
            onNextReplace = Objects.requireNonNull(callback, "callback");
        }

        private void failAfterNextReplace(Throwable failure) {
            failureAfterNextReplace = Objects.requireNonNull(failure, "failure");
        }

        private P8ClientResourceIndex lastResources() {
            return lastResources;
        }
    }

    private static final class CleanupRoutingExecution extends TestExecution {
        private int activeOwners;
        private long liveParticleCredits;

        @Override
        P8ClientPreparedCatalog prepareCatalog(
                P8ProfileCatalogSnapshot catalog,
                P8ClientResourceIndex resources,
                long connectionGeneration,
                long worldGeneration,
                long resourceGeneration) {
            return prepared(catalog, resources);
        }

        @Override
        void clearActiveForCatalogReplacement() {
            activeOwners = 0;
        }

        @Override
        public void clearActive() {
            activeOwners = 0;
            liveParticleCredits = 0L;
        }

        @Override
        public void clearAll() {
            super.clearAll();
            activeOwners = 0;
            liveParticleCredits = 0L;
        }

        private void seedActiveWork() {
            activeOwners = 1;
            liveParticleCredits = 1L;
        }

        private int activeOwners() {
            return activeOwners;
        }

        private long liveParticleCredits() {
            return liveParticleCredits;
        }
    }

    private record NetworkWork(
            P8ClientDispatchTask newCatalogDrain,
            P8ClientDispatchTask oldEventTask) {}
}
