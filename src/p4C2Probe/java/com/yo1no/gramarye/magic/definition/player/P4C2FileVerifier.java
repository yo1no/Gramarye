package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        if (manifest.probeCase() == P4C2ProbeCase.PRESERVED_RAW) {
            storeFacts = P4C2StoreProbe.verifyCanonical(
                    worldRoot, manifest.expectedStore(worldRoot));
        }
        if (!expectedMode.restart()) {
            manifest.afterFirstRun(actualChecksum, actualBytes).write(worldRoot);
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
