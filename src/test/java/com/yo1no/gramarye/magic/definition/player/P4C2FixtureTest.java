package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lightweight fixture proofs; full player lifecycle remains in the fixed-heap gate. */
final class P4C2FixtureTest {
    private static final String PRESERVED_PAYLOAD_SHA256 =
            "92dc83c6b53f0c69c481820911eb246d49d6c88413b511eee342eb92d85210fb";
    private static final String OVERSIZE_PAYLOAD_SHA256 =
            "eb0b9d12a6d36a2ddd0a1dea562bbd67dd58c8256cefc88af7b835381813587b";

    @Test
    void readyFixtureHasDeterministicCurrentAndReplacementCarriers() {
        var initial = P4C2FixtureBuilder.readyState(false);
        var replacement = P4C2FixtureBuilder.readyState(true);
        var repeatedReplacement = P4C2FixtureBuilder.readyState(true);

        assertEquals(1, initial.drafts().size());
        assertEquals(2, initial.latestStates().size());
        assertEquals(2, initial.equipped().size());
        assertFalse(P4C2Hashing.sha256(initial.carrier().copyTag())
                .equals(P4C2Hashing.sha256(replacement.carrier().copyTag())));
        assertEquals(
                P4C2Hashing.sha256(replacement.carrier().copyTag()),
                P4C2Hashing.sha256(repeatedReplacement.carrier().copyTag()));
    }

    @Test
    void readyChecksumIsStableAcrossTheMaterializedNbtBoundary() throws Exception {
        var source = P4C2FixtureBuilder.readyState(false).carrier().copyTag();
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeAnyTag(source, output);
        }
        final net.minecraft.nbt.Tag decoded;
        try (var input = new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = NbtIo.readAnyTag(input, new NbtAccounter(1_000_000, 128));
            assertEquals(-1, input.read());
        }

        assertEquals(source, decoded);
        assertEquals(P4C2Hashing.sha256(source), P4C2Hashing.sha256(decoded));
    }

    @Test
    void exactPreservedPayloadHasAuthoritativeCountAndPatternChecksum() {
        var payload = P4C2FixtureBuilder.payload(
                P4C2FixtureBuilder.PRESERVED_PAYLOAD_BYTES);
        var tag = new ByteArrayTag(payload);

        assertEquals(P4C2FixtureBuilder.PRESERVED_ATTACHMENT_BYTES,
                P4C2FixtureBuilder.exactCount(tag));
        assertEquals(PRESERVED_PAYLOAD_SHA256, P4C2Hashing.sha256(payload));
    }

    @Test
    void exactPlusOnePayloadHasAuthoritativeCountAndPatternChecksum() {
        var payload = P4C2FixtureBuilder.payload(
                P4C2FixtureBuilder.OVERSIZE_PAYLOAD_BYTES);
        var tag = new ByteArrayTag(payload);

        assertEquals(P4C2FixtureBuilder.OVERSIZE_ATTACHMENT_BYTES,
                P4C2FixtureBuilder.exactCount(tag));
        assertEquals(OVERSIZE_PAYLOAD_SHA256, P4C2Hashing.sha256(payload));
    }

    @Test
    void oversizeMarkerIsExactDeterministicAndFarBelowTheCeiling() {
        var first = PlayerSkillAttachmentMarker.freshTag();
        var second = PlayerSkillAttachmentMarker.freshTag();

        assertTrue(PlayerSkillAttachmentMarker.isExact(first));
        assertEquals(142, P4C2FixtureBuilder.exactCount(first));
        assertEquals(P4C2Hashing.sha256(first), P4C2Hashing.sha256(second));
    }

    @Test
    void manifestIsBoundedAndContainsOnlyChecksumsAndScalarFacts(@TempDir Path root)
            throws Exception {
        var checksum = "0".repeat(64);
        var manifest = P4C2FixtureManifest.first(
                P4C2ProbeCase.READY,
                10,
                11,
                checksum,
                checksum,
                checksum,
                1,
                2,
                2,
                checksum,
                12,
                null);

        manifest.write(root);
        var path = root.resolve(P4C2FixtureManifest.FILE_NAME);
        var text = Files.readString(path);
        assertTrue(Files.size(path) <= 4_096);
        assertFalse(text.contains(P4C2ProbeCase.READY.playerId().toString()));
        assertFalse(text.contains("ByteArrayTag"));
        assertEquals(manifest, P4C2FixtureManifest.read(root));
    }
}
