package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.lang.ref.Reference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;

/**
 * Research-only Matrix-F bridge for the exact D3 Store/journal objects and an actual held
 * DimensionDataStorage save. The prospective journal is deliberately not bootstrapped or
 * described as audited against the current Store.
 */
public final class P4E0ResearchCombinedStoreSession {
    private static final long IO_LATCH_TIMEOUT_SECONDS = 30L;
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("research cache-hit constructor was invoked");
                    },
                    (tag, provider) -> {
                        throw new AssertionError("research cache-hit deserializer was invoked");
                    });

    private P4E0ResearchCombinedStoreSession() {
    }

    /** Builds the exact 66,060,348-byte carrier and both D3 journals exactly once. */
    public static Prepared prepare() {
        var fixture = P4D3StoreJournalFixture.build();
        var prospectiveInner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                fixture.carrier(),
                fixture.encodedProspective().pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(
                                fixture.carrier().storeByteCount(),
                                fixture.encodedProspective().byteCount())));

        var latestHistories = fixture.store().snapshot().histories().stream()
                .map(history -> new SkillHistorySnapshot(
                        history.skillId(),
                        history.owner(),
                        List.of(history.revisions().getLast())))
                .toList();
        var latestSnapshot = new SkillDefinitionStoreSnapshot(latestHistories);
        var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(
                fixture.carrier(), latestSnapshot);

        var metrics = new Metrics(
                fixture.carrier().storeByteCount(),
                fixture.carrier().historyCount(),
                fixture.carrier().revisionCount(),
                P4D3Hashing.sha256(fixture.carrier()),
                fixture.encodedCurrent().entryCount(),
                fixture.encodedCurrent().byteCount(),
                fixture.encodedProspective().entryCount(),
                fixture.encodedProspective().byteCount(),
                fixture.prospectiveJournal().targetReferences().size(),
                false,
                filtered.storeByteCount(),
                filtered.historyCount(),
                filtered.revisionCount(),
                P4D3Hashing.sha256(filtered));
        requireExactMetrics(metrics);
        return new Prepared(
                fixture, prospectiveInner, latestSnapshot, filtered, metrics);
    }

    private static void requireExactMetrics(Metrics metrics) {
        if (metrics.storeBytes() != P4D3StoreJournalFixture.STORE_BYTES
                || metrics.storeHistories() != P4D3StoreJournalFixture.HISTORY_COUNT
                || metrics.storeRevisions() != P4D3StoreJournalFixture.REVISION_COUNT
                || metrics.currentJournalEntries()
                        != P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES
                || metrics.currentJournalBytes()
                        != P4D3StoreJournalFixture.CURRENT_JOURNAL_BYTES
                || metrics.prospectiveJournalEntries()
                        != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES
                || metrics.prospectiveJournalBytes()
                        != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES
                || metrics.prospectiveJournalRoots()
                        != P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES
                || metrics.prospectiveTargetsAuditedAgainstCurrentStore()
                || metrics.filteredHistories() != P4D3StoreJournalFixture.HISTORY_COUNT
                || metrics.filteredRevisions() != P4D3StoreJournalFixture.HISTORY_COUNT) {
            throw new AssertionError("research Matrix-F Store/journal shape changed");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("research held save requires the server thread");
        }
    }

    public record Metrics(
            int storeBytes,
            int storeHistories,
            int storeRevisions,
            String storeChecksum,
            int currentJournalEntries,
            int currentJournalBytes,
            int prospectiveJournalEntries,
            int prospectiveJournalBytes,
            int prospectiveJournalRoots,
            boolean prospectiveTargetsAuditedAgainstCurrentStore,
            int filteredCarrierBytes,
            int filteredHistories,
            int filteredRevisions,
            String filteredCarrierChecksum) {
        public Metrics {
            P4D3Hashing.requireSha256(storeChecksum);
            P4D3Hashing.requireSha256(filteredCarrierChecksum);
            if (storeBytes <= 0
                    || storeHistories <= 0
                    || storeRevisions <= 0
                    || currentJournalEntries <= 0
                    || currentJournalBytes <= 0
                    || prospectiveJournalEntries <= 0
                    || prospectiveJournalBytes <= 0
                    || prospectiveJournalRoots <= 0
                    || filteredCarrierBytes <= 0
                    || filteredHistories <= 0
                    || filteredRevisions <= 0) {
                throw new IllegalArgumentException(
                        "research combined Store metrics must be positive");
            }
        }
    }

    /** Opaque owner for all current, prospective, and filtered Store/journal objects. */
    public static final class Prepared {
        private final P4D3StoreJournalFixture.Fixture fixture;
        private final SkillSavedDataInnerCarrier prospectiveInner;
        private final SkillDefinitionStoreSnapshot filteredSnapshot;
        private final EncodedSkillStoreCarrier filteredCarrier;
        private final Metrics metrics;

        private Prepared(
                P4D3StoreJournalFixture.Fixture fixture,
                SkillSavedDataInnerCarrier prospectiveInner,
                SkillDefinitionStoreSnapshot filteredSnapshot,
                EncodedSkillStoreCarrier filteredCarrier,
                Metrics metrics) {
            this.fixture = Objects.requireNonNull(fixture, "fixture");
            this.prospectiveInner = Objects.requireNonNull(
                    prospectiveInner, "prospectiveInner");
            this.filteredSnapshot = Objects.requireNonNull(
                    filteredSnapshot, "filteredSnapshot");
            this.filteredCarrier = Objects.requireNonNull(
                    filteredCarrier, "filteredCarrier");
            this.metrics = Objects.requireNonNull(metrics, "metrics");
        }

        public Metrics metrics() {
            return metrics;
        }

        public List<SkillReference> prospectiveJournalRoots() {
            return List.copyOf(fixture.prospectiveJournal().targetReferences());
        }

        /**
         * Installs a temporary research adapter, triggers the real platform save/deep-copy path,
         * and holds the queued IO write until the returned session is closed.
         */
        public HeldSave beginHeldSave(MinecraftServer server) {
            requireServerThread(server);
            var storage = server.overworld().getDataStorage();
            var original = storage.get(
                    CACHE_HIT_ONLY_FACTORY,
                    SkillDefinitionStoreService.SAVED_DATA_NAME);
            if (!(original instanceof GramaryeSkillSavedData originalAdapter)) {
                throw new AssertionError("research startup SavedData adapter is absent");
            }

            var candidate = SkillSavedDataReadyCandidate.afterCarrierRebuild(
                    fixture.store(),
                    prospectiveInner,
                    new PipelineFactReport(List.of(), false),
                    true);
            var temporary = GramaryeSkillSavedData.ready(candidate);
            temporary.setDirty();
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, temporary);
            if (storage.get(
                            CACHE_HIT_ONLY_FACTORY,
                            SkillDefinitionStoreService.SAVED_DATA_NAME)
                    != temporary) {
                storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, originalAdapter);
                throw new AssertionError("research temporary adapter cache identity changed");
            }

            var workerEntered = new CountDownLatch(1);
            var releaseWorker = new CountDownLatch(1);
            IOUtilities.withIOWorker(() -> {
                workerEntered.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("research IO hold was interrupted");
                }
            });

            var started = false;
            try {
                if (!workerEntered.await(IO_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("research IO hold did not start");
                }
                storage.save();
                if (temporary.isDirty()) {
                    throw new AssertionError(
                            "research platform save did not consume temporary dirty state");
                }
                started = true;
                return new HeldSave(
                        server,
                        storage,
                        originalAdapter,
                        temporary,
                        this,
                        releaseWorker);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("research server thread was interrupted");
            } finally {
                if (!started) {
                    releaseWorker.countDown();
                    IOUtilities.waitUntilIOWorkerComplete();
                    storage.set(
                            SkillDefinitionStoreService.SAVED_DATA_NAME,
                            originalAdapter);
                }
            }
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(fixture);
            Reference.reachabilityFence(prospectiveInner);
            Reference.reachabilityFence(filteredSnapshot);
            Reference.reachabilityFence(filteredCarrier);
        }
    }

    /** Held platform-copy session; closing releases and joins the real IO worker. */
    public static final class HeldSave implements AutoCloseable {
        private final MinecraftServer server;
        private final DimensionDataStorage storage;
        private final GramaryeSkillSavedData original;
        private final GramaryeSkillSavedData temporary;
        private final Prepared prepared;
        private final CountDownLatch releaseWorker;
        private boolean closed;

        private HeldSave(
                MinecraftServer server,
                DimensionDataStorage storage,
                GramaryeSkillSavedData original,
                GramaryeSkillSavedData temporary,
                Prepared prepared,
                CountDownLatch releaseWorker) {
            this.server = server;
            this.storage = storage;
            this.original = original;
            this.temporary = temporary;
            this.prepared = prepared;
            this.releaseWorker = releaseWorker;
        }

        public void retainAtPeak() {
            if (closed) {
                throw new IllegalStateException("research held save is closed");
            }
            prepared.retainAtPeak();
            Reference.reachabilityFence(temporary);
            Reference.reachabilityFence(releaseWorker);
        }

        @Override
        public void close() {
            requireServerThread(server);
            if (closed) {
                throw new IllegalStateException("research held save closed twice");
            }
            closed = true;
            releaseWorker.countDown();
            IOUtilities.waitUntilIOWorkerComplete();
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, original);
            prepared.retainAtPeak();
        }
    }
}
