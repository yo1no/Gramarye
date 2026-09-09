package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.ArrayList;
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
import net.minecraft.resources.ResourceLocation;
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

        var network = Executors.newSingleThreadExecutor();
        NetworkWork networkWork;
        var second = catalog(2L);
        try {
            networkWork = network.submit(() -> new NetworkWork(
                            state.prepareProfileCatalog(second).orElseThrow(),
                            state.preparePresentationEvent(event(1L, 1L)).orElseThrow()))
                    .get(1L, TimeUnit.SECONDS);
            assertEquals(
                    catalogCharge(first)
                            + catalogCharge(second)
                            + eventCharge(event(1L, 1L)),
                    state.combinedQueuedCharge());
            networkWork.newCatalogDrain().releaseAfterFailedEnqueue();
            networkWork.newCatalogDrain().releaseAfterFailedEnqueue();
            assertEquals(
                    catalogCharge(first) + eventCharge(event(1L, 1L)),
                    state.combinedQueuedCharge());
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
    void fullCatalogOverlapRetainsTheInFlightChargeAndDropsCapacityExcess()
            throws Exception {
        var execution = new BlockingExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var first = maximumEnvelopeCatalog(1L);
        var second = maximumEnvelopeCatalog(2L);
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
        assertEquals(catalogCharge(first), state.combinedQueuedCharge());

        var network = Executors.newSingleThreadExecutor();
        try {
            assertTrue(network.submit(() -> state.prepareProfileCatalog(second))
                    .get(1L, TimeUnit.SECONDS)
                    .isEmpty());
            assertEquals(0L, state.pendingCatalogGeneration());
            assertEquals(catalogCharge(first), state.combinedQueuedCharge());
        } finally {
            execution.release();
            network.shutdownNow();
        }

        drainThread.join(TimeUnit.SECONDS.toMillis(5L));
        assertFalse(drainThread.isAlive(), "full catalog drain did not terminate");
        assertNull(drainFailure.get());
        assertEquals(1L, state.installedCatalogGeneration());
        assertEquals(0L, state.combinedQueuedCharge());
    }

    @Test
    void connectionChangeAfterCatalogBindPreventsStaleCatalogPublication() {
        var execution = new ReentrantExecution();
        var state = new P8ClientPresentationState(() -> true, execution);
        state.onResourceIndexApplied(P8ClientResourceIndex.empty());
        state.onConnectionOpened();
        state.onWorldLoaded();
        var stale = catalog(1L);
        var staleDrain = state.prepareProfileCatalog(stale).orElseThrow();
        execution.onNextReplace(state::onLoggedOut);

        staleDrain.run();

        assertFalse(state.connected());
        assertEquals(0L, state.installedCatalogGeneration());
        assertNull(state.installedCatalogSnapshot());
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

    private static final class ReentrantExecution extends TestExecution {
        private Runnable onNextReplace;
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
            if (callback != null) {
                callback.run();
            }
            return prepared(catalog, resources);
        }

        private void onNextReplace(Runnable callback) {
            onNextReplace = Objects.requireNonNull(callback, "callback");
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
