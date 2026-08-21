package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.P4E2QualificationObservation;
import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Fixed-heap external verifier for the synchronous save and clean shutdown result. */
final class P4C2FileVerifier {
    private static final long PLAYERDATA_QUOTA_BYTES = 64L * 1_024L * 1_024L;
    private static final long READY_ATTACHMENT_BYTES = 1_199L;
    private static final String READY_ATTACHMENT_CHECKSUM =
            "10a2b5a1171a62773a40edb6ae47d642abf3934a523564ad83edee1eb42c43a1";
    private static final int READY_STORE_BYTES = 2_090;
    private static final String READY_STORE_CHECKSUM =
            "b479472b555ea58f1a761043827be928953f112ef0a7df76e949da4da289a0fe";

    private P4C2FileVerifier() {
    }

    static Verification verify(Path gameDirectory, P4C2RunMode expectedMode)
            throws IOException {
        var worldRoot = P4C2FixtureManifest.worldRoot(gameDirectory);
        var manifest = P4C2FixtureManifest.read(worldRoot);
        if (manifest.runMode() != expectedMode
                || manifest.probeCase() != expectedMode.probeCase()) {
            throw new AssertionError("P4-C2 verifier mode and manifest differ");
        }
        P4E2QualificationObservation directObservation = null;
        if (manifest.probeCase() == P4C2ProbeCase.READY) {
            requireDirectFileCount(gameDirectory, 1);
            directObservation = P4E2QualificationObservation.readDirectFrom(gameDirectory);
            directObservation.requireReady(
                    expectedMode.restart()
                            ? P4E2QualificationObservation.Phase.RESTART
                            : P4E2QualificationObservation.Phase.FIRST,
                    manifest.probeCase().playerId());
        } else {
            requireDirectFileCount(gameDirectory, 0);
        }
        var playerdata = P4C2FixtureManifest.playerdata(
                worldRoot, manifest.probeCase());
        var root = NbtIo.readCompressed(
                playerdata, NbtAccounter.create(PLAYERDATA_QUOTA_BYTES));
        if (!(root.get("NeoForgeData") instanceof CompoundTag persistent)
                || persistent.getInt(P4C2FixtureManifest.WITNESS_KEY)
                        != manifest.expectedWitness()) {
            throw new AssertionError("P4-C2 independent playerdata witness is absent");
        }
        var attachment = attachment(root);
        verifyAttachment(attachment, manifest);
        var actualChecksum = P4C2Hashing.sha256(playerdata);
        var actualBytes = Files.size(playerdata);
        P4C2StoreProbe.StoreFacts storeFacts = null;
        switch (manifest.probeCase()) {
            case READY -> {
                storeFacts = P4C2FixtureBuilder.requireReadyPrimary(worldRoot);
                if (manifest.expectedAttachmentBytes() != READY_ATTACHMENT_BYTES
                        || !manifest.expectedAttachmentChecksum()
                                .equals(READY_ATTACHMENT_CHECKSUM)
                        || storeFacts.storeBytes() != READY_STORE_BYTES
                        || storeFacts.histories() != 2
                        || storeFacts.revisions() != 3
                        || !storeFacts.checksum().equals(READY_STORE_CHECKSUM)) {
                    throw new AssertionError(
                            "P4-C2 READY Attachment/Store fixture truth drifted");
                }
            }
            case PRESERVED_RAW -> storeFacts = P4C2StoreProbe.verifyCanonical(
                        worldRoot, manifest.expectedStore(worldRoot));
            case OVERSIZE -> {
            }
        }
        if (!expectedMode.restart()) {
            manifest.afterFirstRun(actualChecksum, actualBytes).write(worldRoot);
        }
        if (directObservation != null) {
            archiveDirectObservation(gameDirectory, expectedMode, directObservation);
            System.out.println(directSummary(directObservation));
        }
        return new Verification(
                manifest.probeCase().token(),
                expectedMode.token(),
                actualBytes,
                manifest.expectedAttachmentBytes(),
                P4C2Hashing.witness(manifest.expectedAttachmentChecksum()),
                P4C2Hashing.witness(actualChecksum),
                storeFacts == null ? 0 : storeFacts.storeBytes(),
                storeFacts == null ? 0 : storeFacts.histories(),
                storeFacts == null ? 0 : storeFacts.revisions(),
                storeFacts == null ? "none" : P4C2Hashing.witness(storeFacts.checksum()));
    }

    private static void requireDirectFileCount(Path gameDirectory, long expected)
            throws IOException {
        try (var paths = Files.list(gameDirectory)) {
            var count = paths.filter(path -> {
                var name = path.getFileName().toString();
                return name.equals(P4E2QualificationObservation.FILE_NAME)
                        || name.startsWith(".p4-e2-direct-observation-");
            }).count();
            if (count != expected) {
                throw new AssertionError("P4-C2 direct-observation file count differs");
            }
        }
    }

    private static void archiveDirectObservation(
            Path gameDirectory,
            P4C2RunMode mode,
            P4E2QualificationObservation observation) throws IOException {
        var p4C2Root = gameDirectory.toAbsolutePath().normalize().getParent();
        var buildRoot = p4C2Root == null ? null : p4C2Root.getParent();
        if (p4C2Root == null
                || buildRoot == null
                || !"p4-c2".equals(p4C2Root.getFileName().toString())
                || !"build".equals(buildRoot.getFileName().toString())) {
            throw new IOException("P4-C2 game directory escaped its fixed build root");
        }
        if (!Files.isDirectory(buildRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(buildRoot)) {
            throw new IOException("P4-E2 build root is not a regular directory");
        }
        var reports = buildRoot.resolve("reports");
        if (Files.exists(reports, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(reports, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(reports))) {
            throw new IOException("P4-E2 reports root is not a regular directory");
        }
        var reportRoot = reports.resolve("p4-e2-direct-observation");
        Files.createDirectories(reportRoot);
        if (!Files.isDirectory(reportRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(reportRoot)) {
            throw new IOException("P4-E2 generated report root is not a regular directory");
        }
        var source = P4E2QualificationObservation.directPath(gameDirectory);
        var firstEvidence = reportRoot.resolve("P4C2_READY_FIRST.json");
        var target = mode.restart()
                ? reportRoot.resolve("P4C2_READY_RESTART.json")
                : firstEvidence;
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("P4-E2 generated evidence target already exists");
        }
        if (mode.restart()) {
            var first = P4E2QualificationObservation.readEvidence(firstEvidence);
            first.requireReady(
                    P4E2QualificationObservation.Phase.FIRST,
                    mode.probeCase().playerId());
            observation.requireSameSemanticsExceptPhase(first);
        }
        if (!Files.getFileStore(source).equals(Files.getFileStore(reportRoot))) {
            throw new IOException("P4-E2 evidence archive is not on the direct filesystem");
        }
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("P4-E2 direct target remained after atomic archive");
        }
        var archived = P4E2QualificationObservation.readEvidence(target);
        if (!archived.equals(observation)) {
            throw new AssertionError("P4-E2 archived evidence differs from direct bytes");
        }
    }

    private static String directSummary(P4E2QualificationObservation observation) {
        var line = "P4E2_DIRECT_OBSERVATION_OK"
                + " case=" + observation.caseId()
                + " phase=" + observation.phase().token()
                + " player_uuid=" + observation.playerUuid()
                + " recovery_handler_calls=" + observation.recoveryHandlerCalls()
                + " recovery_outcome=" + observation.typedRecoveryOutcome().token()
                + " entries_cleared=" + observation.entriesCleared()
                + " steps_replayed=" + observation.stepsReplayed()
                + " recovery_changed=" + observation.recoveryChanged()
                + " e2_continuation_calls=" + observation.e2ContinuationCalls()
                + " e2_result=" + observation.e2ResultVariant().token()
                + " invalidation=" + observation.invalidationAttempts()
                + "/" + observation.invalidationAccepted()
                + " generation_present=" + observation.invalidationGenerationPresent()
                + " e2_set_data=" + observation.e2SetDataAttempts()
                + "/" + observation.e2SetDataSuccesses()
                + " marker=" + observation.completionMarker();
        if (line.length() > 480) {
            throw new IllegalStateException("P4-E2 direct summary is unbounded");
        }
        return line;
    }

    static Tag attachment(CompoundTag root) {
        if (!(root.get(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                        instanceof CompoundTag attachments)
                || attachments.get(P4C2FixtureManifest.ATTACHMENT_KEY) == null) {
            throw new AssertionError("P4-C2 saved playerdata omitted its Attachment");
        }
        return attachments.get(P4C2FixtureManifest.ATTACHMENT_KEY);
    }

    static void verifyAttachment(
            Tag attachment, P4C2FixtureManifest manifest) {
        var count = P4C2FixtureBuilder.exactCount(attachment);
        if (count != manifest.expectedAttachmentBytes()
                || !P4C2Hashing.sha256(attachment)
                        .equals(manifest.expectedAttachmentChecksum())) {
            throw new AssertionError("P4-C2 saved Attachment bytes/checksum differ");
        }
        switch (manifest.probeCase()) {
            case READY -> verifyReady(attachment, manifest);
            case PRESERVED_RAW -> verifyPreserved(attachment, manifest);
            case OVERSIZE -> {
                if (!PlayerSkillAttachmentMarker.isExact(attachment)
                        || attachment instanceof ByteArrayTag) {
                    throw new AssertionError("oversize save did not publish the exact marker");
                }
            }
        }
    }

    private static void verifyReady(
            Tag attachment, P4C2FixtureManifest manifest) {
        if (!(attachment instanceof CompoundTag compound)) {
            throw new AssertionError("Ready save is not a CompoundTag");
        }
        var loaded = new PlayerSkillAttachmentPersistenceBridge().load(
                compound, Optional.of(RegistryAccess.EMPTY));
        if (!(loaded instanceof PlayerSkillAttachmentPersistenceBridge.Loaded ready)
                || ready.ready().drafts().size() != manifest.expectedDrafts()
                || ready.ready().latestStates().size() != manifest.expectedLatest()
                || ready.ready().equipped().size() != manifest.expectedEquipped()) {
            throw new AssertionError("Ready save did not restore its exact bounded shape");
        }
    }

    private static void verifyPreserved(
            Tag attachment, P4C2FixtureManifest manifest) {
        if (!(attachment instanceof ByteArrayTag bytes)
                || bytes.size() != P4C2FixtureBuilder.PRESERVED_PAYLOAD_BYTES
                || !P4C2Hashing.payloadSha256(bytes).equals(manifest.payloadChecksum())) {
            throw new AssertionError("PreservedRaw payload type/length/checksum changed");
        }
    }

    record Verification(
            String probeCase,
            String phase,
            long playerdataCompressedBytes,
            long attachmentBytes,
            String attachmentChecksum,
            String playerdataChecksum,
            int storeBytes,
            int storeHistories,
            int storeRevisions,
            String storeChecksum) {
        String line() {
            var line = "P4C2_FILE_OK"
                    + " case=" + probeCase
                    + " phase=" + phase
                    + " playerdata_compressed_bytes=" + playerdataCompressedBytes
                    + " attachment_bytes=" + attachmentBytes
                    + " attachment_checksum=" + attachmentChecksum
                    + " playerdata_checksum=" + playerdataChecksum
                    + " store_bytes=" + storeBytes
                    + " store_histories=" + storeHistories
                    + " store_revisions=" + storeRevisions
                    + " store_checksum=" + storeChecksum;
            if (line.length() > 480) {
                throw new IllegalStateException("P4-C2 file summary is unbounded");
            }
            return line;
        }
    }
}
