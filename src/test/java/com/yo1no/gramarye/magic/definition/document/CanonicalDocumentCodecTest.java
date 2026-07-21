package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

class CanonicalDocumentCodecTest {
    @Test
    void skillRevisionUsesIntBoundariesAndCodec() {
        assertAll(
                () -> assertEquals(0, roundTrip(new SkillRevision(0)).value()),
                () -> assertEquals(1, roundTrip(new SkillRevision(1)).value()),
                () -> assertEquals(Integer.MAX_VALUE, roundTrip(new SkillRevision(Integer.MAX_VALUE)).value()),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkillRevision(-1)),
                () -> assertTrue(SkillRevision.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createInt(-1))
                        .error().isPresent()),
                () -> assertEquals(int.class, List.of(SkillRevision.class.getRecordComponents()).stream()
                        .map(RecordComponent::getType).findFirst().orElseThrow()));
    }

    @Test
    void canonicalDocumentRoundTripsExactShapeAcrossJsonAndNbt() {
        var appearance = new AppearanceDefinition(
                OptionalInt.of(0xFF3366CC),
                OptionalInt.empty(),
                ProfileSelection.specified(ResourceLocation.fromNamespaceAndPath("gramarye", "arcane_cast")),
                ProfileSelection.disabled(),
                ProfileSelection.inherit(),
                OptionalInt.of(1_000));
        var expected = DocumentTestFixtures.document(AppearanceDocument.decoded(appearance));

        var json = SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        var decodedJson = SkillDocument.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        var nbt = SkillDocument.CODEC.encodeStart(NbtOps.INSTANCE, expected).getOrThrow();
        var decodedNbt = SkillDocument.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        var jsonAgain = SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, decodedNbt).getOrThrow();

        assertAll(
                () -> assertEquals(expected, decodedJson),
                () -> assertEquals(expected.appearance(), decodedNbt.appearance()),
                () -> assertEquals(expected.nodes().get(0).trigger().typeId(),
                        decodedNbt.nodes().get(0).trigger().typeId()),
                () -> assertEquals(json, jsonAgain),
                () -> assertEquals("0xFF3366CC", json.getAsJsonObject()
                        .getAsJsonObject("appearance").get("primary_argb").getAsString()),
                () -> assertTrue(json.getAsJsonObject().has("schema_version")),
                () -> assertTrue(json.getAsJsonObject().has("skill_id")),
                () -> assertTrue(json.getAsJsonObject().has("revision")),
                () -> assertTrue(json.getAsJsonObject().has("nodes")),
                () -> assertTrue(json.getAsJsonObject().has("appearance")));
    }

    @Test
    void strictCodecRejectsLegacyNullClampAndMalformedAppearanceWithoutPartial() {
        var legacyNull = DocumentTestFixtures.documentJson(JsonNull.INSTANCE);
        var clamped = DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"intensity_milli\":12000}"));
        var floating = DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"intensity_milli\":1000.0}"));
        var malformed = DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"primary_argb\":\"bad\"}"));
        var unknownField = DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"future_field\":true}"));
        var draftLegacyNull = DocumentTestFixtures.draftJson(JsonNull.INSTANCE);

        assertAll(
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, legacyNull)),
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, clamped)),
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, floating)),
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, malformed)),
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, unknownField)),
                () -> assertCompleteError(SkillDraft.CODEC.parse(JsonOps.INSTANCE, draftLegacyNull)));
    }

    @Test
    void nodeHasNoIndexAndStrictEnvelopeRequirements() {
        var node = DocumentTestFixtures.node();
        var encoded = NodeDocument.CODEC.encodeStart(JsonOps.INSTANCE, node).getOrThrow();
        var decoded = NodeDocument.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var missingTrigger = JsonParser.parseString("""
                {"action":{"type":"test:action","schema_version":0,"payload":{}}}
                """);
        var nullAction = JsonParser.parseString("""
                {"trigger":{"type":"test:trigger","schema_version":0,"payload":{}},"action":null}
                """);
        var nullOverride = JsonParser.parseString("""
                {
                  "trigger":{"type":"test:trigger","schema_version":0,"payload":{}},
                  "action":{"type":"test:action","schema_version":0,"payload":{}},
                  "appearance_override":null
                }
                """);

        assertAll(
                () -> assertEquals(node, decoded),
                () -> assertFalse(encoded.getAsJsonObject().has("index")),
                () -> assertFalse(encoded.getAsJsonObject().has("appearance_override")),
                () -> assertTrue(List.of(NodeDocument.class.getRecordComponents()).stream()
                        .noneMatch(component -> component.getName().toLowerCase().contains("index"))),
                () -> assertThrows(NullPointerException.class, () -> new NodeDocument(
                        null, DocumentTestFixtures.envelope("action"), AppearanceOverrideDocument.none())),
                () -> assertThrows(NullPointerException.class, () -> new NodeDocument(
                        DocumentTestFixtures.envelope("trigger"), null, AppearanceOverrideDocument.none())),
                () -> assertCompleteError(NodeDocument.CODEC.parse(JsonOps.INSTANCE, missingTrigger)),
                () -> assertCompleteError(NodeDocument.CODEC.parse(JsonOps.INSTANCE, nullAction)),
                () -> assertCompleteError(NodeDocument.CODEC.parse(JsonOps.INSTANCE, nullOverride)));
    }

    @Test
    void decodedAppearanceOverrideRoundTripsAsOptionalCanonicalObject() {
        var override = new AppearanceOverride(
                OptionalInt.of(0x80112233),
                OptionalInt.empty(),
                ProfileSelection.inherit(),
                ProfileSelection.disabled(),
                ProfileSelection.inherit(),
                OptionalInt.of(500));
        var node = new NodeDocument(
                DocumentTestFixtures.envelope("trigger"),
                DocumentTestFixtures.envelope("action"),
                AppearanceOverrideDocument.decoded(override));
        var encoded = NodeDocument.CODEC.encodeStart(JsonOps.INSTANCE, node).getOrThrow();

        assertAll(
                () -> assertTrue(encoded.getAsJsonObject().has("appearance_override")),
                () -> assertEquals(node, NodeDocument.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()));
    }

    @Test
    void canonicalDraftPreservesMissingAndPresentSlotsAndOmitsAbsentRevision() {
        var draft = DocumentTestFixtures.draft(AppearanceDocument.defaultAppearance());
        var encoded = SkillDraft.CODEC.encodeStart(JsonOps.INSTANCE, draft).getOrThrow();
        var decoded = SkillDraft.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertAll(
                () -> assertEquals(draft, decoded),
                () -> assertFalse(encoded.getAsJsonObject().has("base_revision")),
                () -> assertEquals("missing", encoded.getAsJsonObject().getAsJsonArray("nodes")
                        .get(0).getAsJsonObject().getAsJsonObject("trigger").get("state").getAsString()),
                () -> assertInstanceOf(DraftActionSlot.Present.class, decoded.nodes().get(0).action()),
                () -> assertTrue(List.of(SkillDraft.class.getRecordComponents()).stream()
                        .noneMatch(component -> component.getName().equals("revision"))));
    }

    @Test
    void skillReferenceRoundTripsWithoutStoreLookup() {
        var reference = new SkillReference(DocumentTestFixtures.SKILL_ID, new SkillRevision(42));
        var encoded = SkillReference.CODEC.encodeStart(JsonOps.INSTANCE, reference).getOrThrow();
        assertEquals(reference, SkillReference.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void tolerantDocumentPreservesUnknownEnvelopeWithoutRegistryLookup() {
        var input = DocumentTestFixtures.documentJson(new com.google.gson.JsonObject());
        var trigger = input.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("trigger");
        trigger.addProperty("type", "future_mod:unknown_trigger");
        trigger.add("payload", JsonParser.parseString("{\"nested\":{\"value\":7}}"));

        var read = SkillDocumentReader.read(new com.mojang.serialization.Dynamic<>(JsonOps.INSTANCE, input))
                .getOrThrow();
        var written = SkillDocumentWriter.write(read.document(), JsonOps.INSTANCE).getOrThrow();

        assertEquals(trigger, written.getAsJsonObject().getAsJsonArray("nodes")
                .get(0).getAsJsonObject().get("trigger"));
    }

    @Test
    void draftSnapshotEditDoesNotMutateOriginal() {
        var original = DocumentTestFixtures.draft(AppearanceDocument.defaultAppearance());
        var edited = original.withNodes(List.of());
        assertAll(
                () -> assertEquals(1, original.nodes().size()),
                () -> assertTrue(edited.nodes().isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class, () -> original.nodes().clear()));
    }

    @Test
    void documentAndDraftDefensivelyCopyNodesAndEnforceOnlyHardCountInvariant() {
        var source = new ArrayList<NodeDocument>();
        source.add(DocumentTestFixtures.node());
        var document = new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(0),
                source,
                AppearanceDocument.defaultAppearance());
        source.clear();

        assertAll(
                () -> assertEquals(1, document.nodes().size()),
                () -> assertThrows(UnsupportedOperationException.class, () -> document.nodes().clear()),
                () -> assertTrue(new SkillDocument(
                                0,
                                DocumentTestFixtures.SKILL_ID,
                                new SkillRevision(0),
                                List.of(),
                                AppearanceDocument.defaultAppearance())
                        .nodes().isEmpty()),
                () -> assertThrows(IllegalArgumentException.class, () -> new SkillDocument(
                        0,
                        DocumentTestFixtures.SKILL_ID,
                        new SkillRevision(0),
                        Collections.nCopies(MagicSafetyCeilings.MAX_NODES + 1, DocumentTestFixtures.node()),
                        AppearanceDocument.defaultAppearance())));
    }

    private static SkillRevision roundTrip(SkillRevision revision) {
        var encoded = SkillRevision.CODEC.encodeStart(JsonOps.INSTANCE, revision).getOrThrow();
        return SkillRevision.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    private static void assertCompleteError(com.mojang.serialization.DataResult<?> result) {
        assertTrue(result.error().isPresent());
        assertTrue(result.result().isEmpty());
    }
}
