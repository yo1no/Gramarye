package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe;
import java.lang.ref.Reference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;

/** Narrow public test-only lifecycle seam for the isolated P4-D3 GameTest source set. */
public final class P4D3ProbeSupport {
    private static final long IO_LATCH_TIMEOUT_SECONDS = 30;
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("P4-D3 cache-hit constructor was invoked");
                    },
                    (tag, provider) -> {
                        throw new AssertionError("P4-D3 cache-hit deserializer was invoked");
                    });

    private P4D3ProbeSupport() {
    }

    public static P4D3RunMode runMode() {
        return P4D3RunMode.fromSystemProperty();
    }

    public static UUID selectedPlayerId() {
        return P4D3StoreJournalFixture.selectedOwner().value();
    }

    public static UUID submissionPlayerId() {
        return P4D3StoreJournalFixture.submissionOwner().value();
    }

    public static SkillId skillId(int index) {
        return P4D3StoreJournalFixture.skillId(index);
    }

    public static SkillReference target(int skillIndex, int revision) {
        return P4D3StoreJournalFixture.target(skillIndex, revision);
    }

    public static SkillReference submissionTarget() {
        return P4D3StoreJournalFixture.submissionTarget();
    }

    public static ManifestView readManifest(MinecraftServer server) throws IOException {
        requireServerThread(server);
        return view(P4D3FixtureManifest.read(worldRoot(server)));
    }

    public static ManifestView readManifest(Path worldRoot) throws IOException {
        return view(P4D3FixtureManifest.read(worldRoot));
    }

    public static LiveFacts requireLive(
            MinecraftServer server, ManifestView expected) {
        requireServerThread(server);
        var live = live(server);
        if (live.storeBytes() != expected.storeBytes()
                || live.histories() != expected.histories()
                || live.revisions() != expected.revisions()
                || !live.storeChecksum().equals(expected.storeChecksum())
                || expected.probeCase() != P4D3ProbeCase.J1
                        && (live.journalEntries() != expected.journalEntries()
                                || live.rootCount() != expected.rootCount())
                || live.journalReady() != (expected.probeCase() != P4D3ProbeCase.J1)) {
            throw new AssertionError("P4-D3 live Store/journal differs from manifest");
        }
        return live;
    }

    public static LiveFacts observeLive(MinecraftServer server) {
        requireServerThread(server);
        return live(server);
    }

    /**
     * Installs one test-owned lifecycle service from the same full primary while retaining the
     * startup adapter. The replacement remains in the Overworld cache after close so normal
     * shutdown saves the state published through the returned production submission port.
     */
    public static CombinedStoreContext installCombinedStore(
            MinecraftServer server, ManifestView expected) {
        requireServerThread(server);
        if (expected.probeCase() != P4D3ProbeCase.COMBINED) {
            throw new IllegalArgumentException("P4-D3 combined context requires combined world");
        }
        var retainedStartup = liveInternal(server);
        requireLive(server, expected);
        var isolated = new SkillDefinitionStoreService();
        isolated.install(server);
        var bootstrapped = isolated.submissionPort().bootstrapJournal(server);
        if (!(bootstrapped
                        instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready ready)
                || ready.entryCount()
                        != P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES) {
            throw new AssertionError("P4-D3 isolated full journal bootstrap failed");
        }
        var installed = liveInternal(server);
        if (installed.adapter() == retainedStartup.adapter()) {
            throw new AssertionError("P4-D3 isolated install reused the startup adapter");
        }
        requireLive(server, expected);
        return new CombinedStoreContext(
                server, isolated, isolated.submissionPort(), retainedStartup);
    }

    /** Proves J1 leaves the public controlled Store read and pin services available. */
    public static void requireJ1ControlledStoreAccess(MinecraftServer server) {
        requireServerThread(server);
        var isolated = new SkillDefinitionStoreService();
        isolated.install(server);
        try {
            if (!(isolated.submissionPort().bootstrapJournal(server)
                    instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Unavailable)) {
                throw new AssertionError("P4-D3 J1 isolated journal unexpectedly bootstrapped");
            }
            var target = target(0, 0);
            if (!(isolated.find(server, target)
                            instanceof SkillSubsystemResult.Available<?> found)
                    || !(found.value() instanceof Optional<?> foundValue)
                    || foundValue.isEmpty()) {
                throw new AssertionError("P4-D3 J1 controlled Store read became unavailable");
            }
            if (!(isolated.pin(server, target)
                            instanceof SkillSubsystemResult.Available<?> pinned)
                    || !(pinned.value() instanceof Optional<?> pinnedValue)
                    || pinnedValue.isEmpty()) {
                throw new AssertionError("P4-D3 J1 controlled Store pin became unavailable");
            }
            ((ControlledSkillPin) pinnedValue.orElseThrow()).close();
        } finally {
            isolated.uninstall(server);
        }
    }

    /**
     * Starts an actual DimensionDataStorage save while the shared IO worker is held after the
     * platform has created its whole-root deep copy.
     */
    public static HeldSavedDataSave beginHeldSavedDataSave(
            MinecraftServer server, ManifestView expected) throws IOException {
        requireServerThread(server);
        var live = liveInternal(server);
        requireLive(server, expected);
        if (!live.ready().rewriteRequired() || !live.adapter().isDirty()) {
            throw new AssertionError("P4-D3 held save requires dirty noncanonical Ready state");
        }
        var workerEntered = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        IOUtilities.withIOWorker(() -> {
            workerEntered.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("P4-D3 IO hold was interrupted");
            }
        });
        var started = false;
        try {
            if (!workerEntered.await(IO_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("P4-D3 IO hold did not start");
            }
            live.storage().save();
            if (live.adapter().isDirty()) {
                throw new AssertionError("P4-D3 held save did not consume dirty state");
            }
            started = true;
            return new HeldSavedDataSave(server, releaseWorker);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("P4-D3 server thread was interrupted");
        } finally {
            if (!started) {
                releaseWorker.countDown();
                IOUtilities.waitUntilIOWorkerComplete();
            }
        }
    }

    public static void saveSavedDataAndWait(MinecraftServer server) {
        requireServerThread(server);
        server.overworld().getDataStorage().save();
        IOUtilities.waitUntilIOWorkerComplete();
    }

    public static P4D3PlayerProbe.Tuple playerTuple(
            ServerPlayer player, SkillId skillId) {
        return P4D3PlayerProbe.tuple(player, skillId);
    }

    public static void requirePlayerdataUnchanged(
            MinecraftServer server, ManifestView before) throws IOException {
        requireServerThread(server);
        var disk = P4D3FileVerifier.inspect(worldRoot(server), before.probeCase());
        if (!disk.selectedPlayerdataChecksum().equals(before.selectedPlayerdataChecksum())
                || disk.selectedPlayerdataBytes() != before.selectedPlayerdataBytes()) {
            throw new AssertionError("P4-D3 playerdata changed across a no-write crash window");
        }
    }

    /** Writes and force-flushes one bounded manifest immediately before success or hard halt. */
    public static ManifestView publishPhase(
            MinecraftServer server,
            P4D3RunMode mode,
            String stateCode,
            String outcomeCode,
            HeapMetrics metrics) throws IOException {
        requireServerThread(server);
        var worldRoot = worldRoot(server);
        var current = P4D3FixtureManifest.read(worldRoot);
        if (current.probeCase() != mode.probeCase()) {
            throw new AssertionError("P4-D3 manifest and run mode case differ");
        }
        var disk = P4D3FileVerifier.inspect(worldRoot, mode.probeCase());
        var updated = current.withPhaseAndDisk(
                mode.completionPhase(), stateCode, disk, outcomeCode,
                metrics.heapMax(), metrics.initialCommitted(), metrics.sampledPeak(),
                metrics.poolPeakSum(), metrics.elapsedMillis());
        updated.write(worldRoot);
        return view(updated);
    }

    public static String summary(
            P4D3RunMode mode,
            ManifestView manifest,
            LiveFacts live,
            HeapMetrics metrics) {
        var line = "P4D3_HEAP_OK"
                + " case=" + mode.probeCase().token()
                + " phase=" + mode.token()
                + " heap_max=" + metrics.heapMax()
                + " initial_committed=" + metrics.initialCommitted()
                + " sampled_peak=" + metrics.sampledPeak()
                + " pool_peak_sum=" + metrics.poolPeakSum()
                + " elapsed_ms=" + metrics.elapsedMillis()
                + " store_bytes=" + live.storeBytes()
                + " histories=" + live.histories()
                + " revisions=" + live.revisions()
                + " journal_bytes=" + live.journalBytes()
                + " journal_entries=" + live.journalEntries()
                + " roots=" + live.rootCount()
                + " attachment_state=" + manifest.expectedStateCode()
                + " checksum=" + P4D3Hashing.witness(live.storeChecksum());
        if (line.length() > 640) {
            throw new IllegalStateException("P4-D3 heap summary is unbounded");
        }
        return line;
    }

    public static Path worldRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }

    private static LiveFacts live(MinecraftServer server) {
        var live = liveInternal(server);
        var ready = live.ready();
        var carrier = ready.storeCarrier();
        var lifecycle = ready.journalLifecycle();
        if (!(lifecycle instanceof PendingAttachmentJournalLifecycle.Installed installed)) {
            throw new AssertionError("P4-D3 journal bootstrap is not installed");
        }
        if (installed.state() instanceof PendingAttachmentJournalState.Unavailable) {
            return new LiveFacts(
                    carrier.storeByteCount(), carrier.historyCount(), carrier.revisionCount(),
                    P4D3Hashing.sha256(carrier), ready.innerCarrier().pending().byteCount(),
                    -1, -1, false, live.adapter().isDirty(), ready.rewriteRequired());
        }
        var journalReady = (PendingAttachmentJournalState.Ready) installed.state();
        return new LiveFacts(
                carrier.storeByteCount(), carrier.historyCount(), carrier.revisionCount(),
                P4D3Hashing.sha256(carrier), journalReady.encoded().byteCount(),
                journalReady.journal().entryCount(),
                journalReady.journal().targetReferences().size(), true,
                live.adapter().isDirty(), ready.rewriteRequired());
    }

    private static LiveStore liveInternal(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        var first = storage.get(
                CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        var second = storage.get(
                CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        if (!(first instanceof GramaryeSkillSavedData adapter) || second != adapter
                || !(adapter.state() instanceof SkillSavedDataState.Ready ready)) {
            throw new AssertionError("P4-D3 Overworld cache is not exact Ready SavedData");
        }
        return new LiveStore(storage, adapter, ready);
    }

    private static ManifestView view(P4D3FixtureManifest manifest) {
        return new ManifestView(
                manifest.probeCase(), manifest.phase(), manifest.expectedStateCode(),
                manifest.selectedPlayerChecksum(), manifest.submissionPlayerChecksum(),
                manifest.storeBytes(), manifest.histories(), manifest.revisions(),
                manifest.storeChecksum(), manifest.journalBytes(), manifest.journalEntries(),
                manifest.rootCount(), manifest.journalChecksum(),
                manifest.selectedAttachmentChecksum(), manifest.selectedPlayerdataChecksum(),
                manifest.selectedPlayerdataBytes(), manifest.submissionAttachmentChecksum(),
                manifest.submissionPlayerdataChecksum(), manifest.submissionPlayerdataBytes(),
                manifest.outcomeCode());
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("P4-D3 probe requires the server thread");
        }
    }

    public record ManifestView(
            P4D3ProbeCase probeCase,
            String phase,
            String expectedStateCode,
            String selectedPlayerChecksum,
            String submissionPlayerChecksum,
            int storeBytes,
            int histories,
            int revisions,
            String storeChecksum,
            int journalBytes,
            int journalEntries,
            int rootCount,
            String journalChecksum,
            String selectedAttachmentChecksum,
            String selectedPlayerdataChecksum,
            long selectedPlayerdataBytes,
            String submissionAttachmentChecksum,
            String submissionPlayerdataChecksum,
            long submissionPlayerdataBytes,
            String outcomeCode) {
    }

    public record LiveFacts(
            int storeBytes,
            int histories,
            int revisions,
            String storeChecksum,
            int journalBytes,
            int journalEntries,
            int rootCount,
            boolean journalReady,
            boolean dirty,
            boolean rewriteRequired) {
    }

    public record HeapMetrics(
            long heapMax,
            long initialCommitted,
            long sampledPeak,
            long poolPeakSum,
            long elapsedMillis) {
        public HeapMetrics {
            if (heapMax <= 0 || initialCommitted < 0 || sampledPeak < 0
                    || poolPeakSum < 0 || elapsedMillis < 0) {
                throw new IllegalArgumentException("P4-D3 heap metrics are invalid");
            }
        }
    }

    public static final class HeldSavedDataSave implements AutoCloseable {
        private final MinecraftServer server;
        private final CountDownLatch releaseWorker;
        private boolean closed;

        private HeldSavedDataSave(
                MinecraftServer server, CountDownLatch releaseWorker) {
            this.server = server;
            this.releaseWorker = releaseWorker;
        }

        @Override
        public void close() {
            requireServerThread(server);
            if (closed) {
                throw new IllegalStateException("P4-D3 held save closed twice");
            }
            closed = true;
            releaseWorker.countDown();
            IOUtilities.waitUntilIOWorkerComplete();
        }
    }

    /** Opaque owner of the duplicate current Store retained through the D2 peak callback. */
    public static final class CombinedStoreContext implements AutoCloseable {
        private final MinecraftServer server;
        private final SkillDefinitionStoreService isolated;
        private final SkillDefinitionStoreSubmissionPort port;
        private final LiveStore retainedStartup;
        private boolean closed;

        private CombinedStoreContext(
                MinecraftServer server,
                SkillDefinitionStoreService isolated,
                SkillDefinitionStoreSubmissionPort port,
                LiveStore retainedStartup) {
            this.server = server;
            this.isolated = isolated;
            this.port = port;
            this.retainedStartup = retainedStartup;
        }

        public SkillDefinitionStoreSubmissionPort submissionPort() {
            if (closed) {
                throw new IllegalStateException("P4-D3 combined context is closed");
            }
            return port;
        }

        /** Keeps the startup Store/carrier strongly reachable at the measured preparation peak. */
        public void retainAtPeak() {
            if (closed) {
                throw new IllegalStateException("P4-D3 combined context is closed");
            }
            Reference.reachabilityFence(retainedStartup);
        }

        @Override
        public void close() {
            requireServerThread(server);
            if (closed) {
                throw new IllegalStateException("P4-D3 combined context closed twice");
            }
            closed = true;
            isolated.uninstall(server);
            Reference.reachabilityFence(retainedStartup);
        }
    }

    private record LiveStore(
            DimensionDataStorage storage,
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready ready) {
    }
}
