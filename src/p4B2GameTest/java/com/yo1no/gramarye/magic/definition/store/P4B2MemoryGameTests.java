package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One dispatcher GameTest; each run property selects exactly one server/world phase. */
@GameTestHolder("gramarye_p4_b2")
@PrefixGameTestTemplate(false)
public final class P4B2MemoryGameTests {
    private static final long IO_LATCH_TIMEOUT_SECONDS = 30;
    private static final SkillOwnerId UNAVAILABLE_PROBE_OWNER =
            new SkillOwnerId(new UUID(0x5044_4232_0000_0000L, 1L));
    private static final SavedData.Factory<SavedData> CACHE_HIT_ONLY_FACTORY =
            new SavedData.Factory<>(
                    () -> {
                        throw new AssertionError("P4-B2 cache-hit constructor was invoked");
                    },
                    (tag, provider) -> {
                        throw new AssertionError("P4-B2 cache-hit deserializer was invoked");
                    });

    private P4B2MemoryGameTests() {
    }

    @GameTest(
            templateNamespace = "gramarye_p4_b2",
            template = "p4_b2_probe",
            timeoutTicks = 12_000)
    public static void executeConfiguredPhase(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        if (!server.isSameThread()) {
            throw new AssertionError("P4-B2 GameTest is not running on the server thread");
        }
        var mode = P4B2RunMode.fromToken(System.getProperty(P4B2RunMode.SYSTEM_PROPERTY, ""));
        var worldRoot = server.getWorldPath(LevelResource.ROOT);
        var manifest = P4B2FixtureManifest.read(worldRoot);
        if (manifest.runMode() != mode || manifest.fixtureCase() != mode.fixtureCase()) {
            throw new AssertionError("P4-B2 run property and world manifest do not match");
        }

        var storage = server.overworld().getDataStorage();
        var adapter = requireExactAdapter(storage);
        P4B2ProbeServerLifecycle.sample(server);

        RunFacts facts = mode.fullSize()
                ? runFull(server, storage, adapter, worldRoot, manifest, mode)
                : runInvalid(server, storage, adapter, worldRoot, manifest, mode);
        P4B2ProbeServerLifecycle.sample(server);
        var metrics = P4B2ProbeServerLifecycle.finish(server);
        System.out.println(new P4B2ProbeSummary(
                mode.token(),
                facts.storeBytes(),
                facts.compressedBytes(),
                facts.histories(),
                facts.revisions(),
                metrics.maximum(),
                metrics.initialCommitted(),
                metrics.sampledPeak(),
                metrics.poolPeakSum(),
                metrics.elapsedMillis(),
                facts.checksumWitness()).line());
        helper.succeed();
    }

    private static RunFacts runFull(
            MinecraftServer server,
            DimensionDataStorage storage,
            GramaryeSkillSavedData adapter,
            Path worldRoot,
            P4B2FixtureManifest manifest,
            P4B2RunMode mode) throws IOException {
        if (!(adapter.state() instanceof SkillSavedDataState.Ready ready)) {
            throw new AssertionError("full P4-B2 primary did not install Ready");
        }
        var carrier = ready.storeCarrier();
        if (carrier.storeByteCount() < P4B2FixtureBuilder.FULL_SIZE_MINIMUM_BYTES
                || carrier.storeByteCount() != manifest.expectedStoreBytes()
                || carrier.historyCount() != manifest.expectedHistories()
                || carrier.revisionCount() != manifest.expectedRevisions()
                || ready.innerCarrier().pending().byteCount() != 0) {
            throw new AssertionError("live full-size Ready shape differs from the manifest");
        }
        var canonicalChecksum = P4B2Hashing.sha256(carrier);
        if (!canonicalChecksum.equals(manifest.canonicalStoreSha256())) {
            throw new AssertionError("live full-size carrier checksum differs from the manifest");
        }
        P4B2FixtureBuilder.requireCarrierDomain(
                carrier,
                ready.store(),
                manifest.expectedHistories(),
                manifest.expectedRevisions());
        requireControlledReads(adapter, carrier);

        var primary = P4B2FixtureManifest.primary(worldRoot);
        if (!mode.restart()) {
            if (mode.fixtureCase() == P4B2ProbeCase.HOSTILE_FNAME) {
                P4B2FixtureBuilder.requireExactMaximumHostileFname(
                        primary,
                        manifest.sourceFnameBytes());
            }
            if (!ready.rewriteRequired() || !adapter.isDirty()) {
                throw new AssertionError("noncanonical full primary did not become dirty Ready");
            }
            var retainedSource = retainSourceRoot(primary, manifest);
            P4B2ProbeServerLifecycle.sample(server);
            saveWithHeldPlatformCopy(server, storage, adapter, retainedSource);
            if (adapter.isDirty() || !ready.rewriteRequired()) {
                throw new AssertionError("first platform save changed the live rewrite contract");
            }
            var savedHash = P4B2Hashing.sha256(primary);
            if (savedHash.equals(manifest.sourcePrimarySha256())) {
                throw new AssertionError("first platform save did not replace source bytes");
            }
            if (mode.fixtureCase() == P4B2ProbeCase.HOSTILE_FNAME) {
                P4B2FixtureBuilder.requireCanonicalGzipWithoutFname(primary);
            }
        } else {
            if (mode.fixtureCase() == P4B2ProbeCase.HOSTILE_FNAME) {
                P4B2FixtureBuilder.requireCanonicalGzipWithoutFname(primary);
            }
            if (ready.rewriteRequired() || adapter.isDirty()) {
                throw new AssertionError("canonical restart was not initially clean");
            }
            var beforeHash = P4B2Hashing.sha256(primary);
            storage.save();
            IOUtilities.waitUntilIOWorkerComplete();
            if (adapter.isDirty() || !beforeHash.equals(P4B2Hashing.sha256(primary))) {
                throw new AssertionError("clean restart invoked an unnecessary write");
            }
            if (mode.fixtureCase() == P4B2ProbeCase.HOSTILE_FNAME) {
                P4B2FixtureBuilder.requireCanonicalGzipWithoutFname(primary);
            }
        }
        return new RunFacts(
                carrier.storeByteCount(),
                Files.size(primary),
                carrier.historyCount(),
                carrier.revisionCount(),
                P4B2Hashing.witness(canonicalChecksum));
    }

    private static RunFacts runInvalid(
            MinecraftServer server,
            DimensionDataStorage storage,
            GramaryeSkillSavedData adapter,
            Path worldRoot,
            P4B2FixtureManifest manifest,
            P4B2RunMode mode) throws IOException {
        if (!(adapter.state() instanceof SkillSavedDataState.Quarantined quarantined)
                || adapter.isDirty()
                || !matchesFailure(quarantined.failure(), mode.fixtureCase())) {
            throw new AssertionError("invalid P4-B2 primary did not install exact Quarantined");
        }
        var unavailable = adapter.committedSkillCount(UNAVAILABLE_PROBE_OWNER);
        if (!(unavailable instanceof SkillSubsystemResult.Unavailable<Integer> result)
                || result.reason().state()
                        != SkillSubsystemUnavailableReason.State.QUARANTINED
                || result.reason().code() != expectedUnavailableCode(mode.fixtureCase())) {
            throw new AssertionError("controlled API did not expose exact quarantine reason");
        }

        var primary = P4B2FixtureManifest.primary(worldRoot);
        var oldPrimary = P4B2FixtureManifest.oldPrimary(worldRoot);
        var primaryHash = P4B2Hashing.sha256(primary);
        var oldHash = P4B2Hashing.sha256(oldPrimary);
        if (!primaryHash.equals(manifest.expectedPrimarySha256())
                || !oldHash.equals(manifest.expectedOldSha256())) {
            throw new AssertionError("invalid fixture changed before its clean save check");
        }
        storage.save();
        IOUtilities.waitUntilIOWorkerComplete();
        P4B2ProbeServerLifecycle.sample(server);
        if (adapter.isDirty()
                || adapter.state() != quarantined
                || !primaryHash.equals(P4B2Hashing.sha256(primary))
                || !oldHash.equals(P4B2Hashing.sha256(oldPrimary))) {
            throw new AssertionError("Quarantined save/shutdown path modified fixture bytes");
        }
        return new RunFacts(
                0,
                Files.size(primary),
                0,
                0,
                P4B2Hashing.witness(primaryHash));
    }

    private static GramaryeSkillSavedData requireExactAdapter(DimensionDataStorage storage) {
        var first = storage.get(CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        var second = storage.get(CACHE_HIT_ONLY_FACTORY, SkillDefinitionStoreService.SAVED_DATA_NAME);
        if (!(first instanceof GramaryeSkillSavedData adapter) || second != adapter) {
            throw new AssertionError("P4-B2 Overworld cache lost exact adapter identity");
        }
        return adapter;
    }

    private static void requireControlledReads(
            GramaryeSkillSavedData adapter,
            EncodedSkillStoreCarrier carrier) {
        for (var history : carrier.histories()) {
            if (!(adapter.ownerOf(history.skillId())
                            instanceof SkillSubsystemResult.Available<Optional<com.yo1no.gramarye
                                    .magic.api.id.SkillOwnerId>> owner)
                    || owner.value().isEmpty()
                    || !owner.value().orElseThrow().equals(history.owner())
                    || !(adapter.latestReference(history.skillId())
                            instanceof SkillSubsystemResult.Available<Optional<com.yo1no.gramarye
                                    .magic.definition.document.SkillReference>> latest)
                    || latest.value().isEmpty()
                    || !latest.value().orElseThrow().equals(history.latestReference())) {
                throw new AssertionError("controlled owner/latest read differs from Ready");
            }
            for (var revision : history.revisions()) {
                var found = adapter.find(revision.reference());
                if (!(found instanceof SkillSubsystemResult.Available<Optional<com.yo1no.gramarye
                                .magic.definition.document.SkillDocument>> available)
                        || available.value().isEmpty()) {
                    throw new AssertionError("controlled document read missed a retained revision");
                }
            }
        }
    }

    private static RetainedSourceRoot retainSourceRoot(
            Path primary,
            P4B2FixtureManifest manifest) throws IOException {
        if (!P4B2Hashing.sha256(primary).equals(manifest.sourcePrimarySha256())) {
            throw new AssertionError("first-run source primary hash differs from the manifest");
        }
        CompoundTag root = NbtIo.readCompressed(
                primary,
                NbtAccounter.create(
                        SkillSavedDataPersistenceSchema.FINITE_WHOLE_ROOT_NBT_QUOTA));
        if (!root.getAllKeys().equals(Set.of(
                        SkillSavedDataPersistenceSchema.DATA_FIELD,
                        SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD))
                || NbtUtils.getDataVersion(root, -1)
                        != SharedConstants.getCurrentVersion().getDataVersion().getVersion()) {
            throw new AssertionError("source root is not current platform whole-root framing");
        }
        var data = root.getCompound(SkillSavedDataPersistenceSchema.DATA_FIELD);
        if (!data.getAllKeys().equals(Set.of(
                        SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                        SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                        SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD))) {
            throw new AssertionError("source inner carrier fields are not exact");
        }
        var storeBytes = data.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD);
        if (!P4B2Hashing.sha256(storeBytes).equals(manifest.sourceStoreSha256())
                || manifest.sourceStoreSha256().equals(manifest.canonicalStoreSha256())) {
            throw new AssertionError("source Store is not the intended noncanonical bytes");
        }
        return new RetainedSourceRoot(root, storeBytes, manifest.sourceStoreSha256());
    }

    private static void saveWithHeldPlatformCopy(
            MinecraftServer server,
            DimensionDataStorage storage,
            GramaryeSkillSavedData adapter,
            RetainedSourceRoot retainedSource) {
        var workerEntered = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        IOUtilities.withIOWorker(() -> {
            workerEntered.countDown();
            try {
                releaseWorker.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("P4-B2 IO hold was interrupted");
            }
        });
        try {
            if (!workerEntered.await(IO_LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("P4-B2 IO hold did not start");
            }
            storage.save();
            if (adapter.isDirty()) {
                throw new AssertionError("platform save did not consume dirty state");
            }
            P4B2ProbeServerLifecycle.sample(server);
            retainedSource.requireUnchanged();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("P4-B2 server thread was interrupted");
        } finally {
            releaseWorker.countDown();
        }
        IOUtilities.waitUntilIOWorkerComplete();
        retainedSource.requireUnchanged();
    }

    private static boolean matchesFailure(
            SkillSavedDataPrimaryFailure failure,
            P4B2ProbeCase fixtureCase) {
        return switch (fixtureCase) {
            case MALFORMED_GZIP -> failure instanceof SkillSavedDataPrimaryFailure.MalformedGzip;
            case COMPRESSED_TRAILING ->
                    failure instanceof SkillSavedDataPrimaryFailure.CompressedTrailingData;
            case SECOND_MEMBER ->
                    failure instanceof SkillSavedDataPrimaryFailure.MultipleGzipMembers;
            case FULL, HOSTILE_FNAME -> false;
        };
    }

    private static SkillSubsystemUnavailableReason.Code expectedUnavailableCode(
            P4B2ProbeCase fixtureCase) {
        return switch (fixtureCase) {
            case MALFORMED_GZIP -> SkillSubsystemUnavailableReason.Code.MALFORMED_GZIP;
            case COMPRESSED_TRAILING ->
                    SkillSubsystemUnavailableReason.Code.COMPRESSED_TRAILING_DATA;
            case SECOND_MEMBER ->
                    SkillSubsystemUnavailableReason.Code.MULTIPLE_GZIP_MEMBERS;
            case FULL, HOSTILE_FNAME ->
                    throw new IllegalArgumentException("full fixture is not unavailable");
        };
    }

    private record RetainedSourceRoot(
            CompoundTag root,
            byte[] storeBytes,
            String expectedStoreSha256) {
        private RetainedSourceRoot {
            java.util.Objects.requireNonNull(root, "root");
            java.util.Objects.requireNonNull(storeBytes, "storeBytes");
            P4B2Hashing.requireSha256(expectedStoreSha256);
        }

        void requireUnchanged() {
            if (!P4B2Hashing.sha256(storeBytes).equals(expectedStoreSha256)
                    || root.getCompound(SkillSavedDataPersistenceSchema.DATA_FIELD)
                            .getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD)
                            != storeBytes) {
                throw new AssertionError("retained source root changed during platform copy");
            }
        }
    }

    private record RunFacts(
            int storeBytes,
            long compressedBytes,
            int histories,
            int revisions,
            String checksumWitness) {
    }
}
