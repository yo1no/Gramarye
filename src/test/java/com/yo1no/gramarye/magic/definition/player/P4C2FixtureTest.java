package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.P4C2StoreProbe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        var truth = P4C2FixtureBuilder.readyStoreTruth();
        var actualReferences = actualReferences(initial, replacement);
        var documentReferences = truth.documents().stream()
                .map(document -> new SkillReference(document.skillId(), document.revision()))
                .toList();
        var actualHistoryCount = actualReferences.stream()
                .map(SkillReference::skillId)
                .distinct()
                .count();
        var actualRevisionCount = actualReferences.stream().distinct().count();
        var actualNonlatestCount = documentReferences.stream()
                .filter(reference -> documentReferences.stream().anyMatch(candidate ->
                        candidate.skillId().equals(reference.skillId())
                                && candidate.revision().value() > reference.revision().value()))
                .count();
        var initialGenerations = initial.latestStates().stream().collect(
                java.util.stream.Collectors.toMap(
                        PlayerLatestState::skillId,
                        PlayerLatestState::mutationGeneration));
        var finalGenerations = replacement.latestStates().stream().collect(
                java.util.stream.Collectors.toMap(
                        PlayerLatestState::skillId,
                        PlayerLatestState::mutationGeneration));
        var initialEquipped = initial.equipped().stream().collect(
                java.util.stream.Collectors.toMap(
                        EquippedSkillReference::slot,
                        EquippedSkillReference::reference));
        var finalEquipped = replacement.equipped().stream().collect(
                java.util.stream.Collectors.toMap(
                        EquippedSkillReference::slot,
                        EquippedSkillReference::reference));
        var initialLatest = initial.latestStates().stream()
                .filter(latest -> latest.pointer().isPresent())
                .findFirst()
                .orElseThrow()
                .pointer()
                .orElseThrow();
        var finalLatest = replacement.latestStates().stream()
                .filter(latest -> latest.pointer().isPresent())
                .findFirst()
                .orElseThrow()
                .pointer()
                .orElseThrow();

        assertEquals(1, initial.drafts().size());
        assertEquals(2, initial.latestStates().size());
        assertEquals(2, initial.equipped().size());
        assertFalse(P4C2Hashing.sha256(initial.carrier().copyTag())
                .equals(P4C2Hashing.sha256(replacement.carrier().copyTag())));
        assertEquals(
                P4C2Hashing.sha256(replacement.carrier().copyTag()),
                P4C2Hashing.sha256(repeatedReplacement.carrier().copyTag()));
        assertEquals(new SkillOwnerId(P4C2ProbeCase.READY.playerId()), truth.owner());
        assertEquals(actualReferences, truth.referenceOccurrences());
        assertEquals(
                initial.latestStates().size() + replacement.latestStates().size(),
                truth.latestRouteCount());
        assertEquals(
                initial.latestStates().stream().filter(latest -> latest.pointer().isPresent()).count()
                        + replacement.latestStates().stream()
                                .filter(latest -> latest.pointer().isPresent())
                                .count(),
                truth.latestReferenceCount());
        assertEquals(
                initial.equipped().size() + replacement.equipped().size(),
                truth.equippedReferenceCount());
        assertEquals(
                truth.latestRouteCount() - truth.latestReferenceCount(),
                truth.explicitEmptyLatestCount());
        assertEquals(actualHistoryCount, truth.historyCount());
        assertEquals(actualRevisionCount, truth.revisionCount());
        assertEquals(Set.copyOf(actualReferences), Set.copyOf(documentReferences));
        assertEquals(actualNonlatestCount, truth.nonlatestRevisionCount());
        assertTrue(truth.nonlatestRevisionCount() > 0);
        assertEquals(Map.of(
                P4C2FixtureBuilder.READY_DRAFT_ID, 1,
                P4C2FixtureBuilder.READY_EMPTY_ID, 3), initialGenerations);
        assertEquals(Map.of(
                P4C2FixtureBuilder.READY_DRAFT_ID, 2,
                P4C2FixtureBuilder.READY_EMPTY_ID, 3), finalGenerations);
        assertEquals(Set.of(1, 8), initialEquipped.keySet());
        assertEquals(Set.of(1, 8), finalEquipped.keySet());
        assertEquals(initialLatest, initialEquipped.get(1));
        assertEquals(finalLatest, finalEquipped.get(1));
        assertEquals(initialEquipped.get(8), finalEquipped.get(8));
        assertTrue(documentReferences.containsAll(initialEquipped.values()));
        assertTrue(documentReferences.containsAll(finalEquipped.values()));
    }

    @Test
    void readyStorePrimaryIsCanonicalAndCoversEveryDerivedReference(@TempDir Path root)
            throws Exception {
        var truth = P4C2FixtureBuilder.readyStoreTruth();

        var prepared = P4C2StoreProbe.prepareReady(root, truth);
        var reloaded = P4C2StoreProbe.verifyReadyCanonical(root, truth);

        assertEquals(prepared, reloaded);
        assertTrue(prepared.storeBytes() > 0);
        assertEquals(truth.historyCount(), prepared.histories());
        assertEquals(truth.revisionCount(), prepared.revisions());
        assertTrue(truth.nonlatestRevisionCount() > 0,
                "the READY fixture must retain a valid nonlatest reference");
    }

    @Test
    void readyStoreCoverageRejectsAbsentHistoryRevisionAndOwnerMismatch(@TempDir Path root)
            throws Exception {
        var truth = P4C2FixtureBuilder.readyStoreTruth();

        assertThrows(AssertionError.class,
                () -> P4C2StoreProbe.verifyReadyCanonical(root.resolve("absent"), truth));

        var removedHistory = truth.documents().getLast().skillId();
        var withoutHistory = truth.documents().stream()
                .filter(document -> !document.skillId().equals(removedHistory))
                .toList();
        P4C2StoreProbe.writeReadyPrimary(
                root.resolve("missing-history"), truth.owner(), withoutHistory);
        assertThrows(AssertionError.class, () -> P4C2StoreProbe.verifyReadyCanonical(
                root.resolve("missing-history"), truth));

        var removedRevision = truth.documents().stream()
                .filter(document -> truth.documents().stream()
                        .filter(candidate -> candidate.skillId().equals(document.skillId()))
                        .count() > 1)
                .findFirst()
                .orElseThrow();
        var withoutRevision = truth.documents().stream()
                .filter(document -> !document.equals(removedRevision))
                .toList();
        assertEquals(truth.historyCount(), withoutRevision.stream()
                .map(document -> document.skillId())
                .distinct()
                .count());
        P4C2StoreProbe.writeReadyPrimary(
                root.resolve("missing-revision"), truth.owner(), withoutRevision);
        assertThrows(AssertionError.class, () -> P4C2StoreProbe.verifyReadyCanonical(
                root.resolve("missing-revision"), truth));

        var ownerId = truth.owner().value();
        var wrongOwner = new SkillOwnerId(new UUID(
                ownerId.getMostSignificantBits() ^ 1L,
                ownerId.getLeastSignificantBits()));
        P4C2StoreProbe.writeReadyPrimary(
                root.resolve("wrong-owner"), wrongOwner, truth.documents());
        assertThrows(AssertionError.class, () -> P4C2StoreProbe.verifyReadyCanonical(
                root.resolve("wrong-owner"), truth));
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

    private static List<SkillReference> actualReferences(
            PlayerSkillAttachmentReady... states) {
        var references = new ArrayList<SkillReference>();
        for (var state : states) {
            for (var latest : state.latestStates()) {
                latest.pointer().ifPresent(references::add);
            }
            for (var equipped : state.equipped()) {
                references.add(equipped.reference());
            }
        }
        return List.copyOf(references);
    }
}
