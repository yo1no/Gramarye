package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DocumentBoundsAndReportTest {
    @Test
    void globalDocumentDepth64PassesAnd65FailsForStrictReaderAndWriter() {
        var depth64 = documentWithTriggerPayload(nestedJson(60));
        var depth65 = documentWithTriggerPayload(nestedJson(61));

        assertAll(
                () -> assertTrue(SkillDocument.CODEC.parse(JsonOps.INSTANCE, depth64).isSuccess()),
                () -> assertCompleteError(SkillDocument.CODEC.parse(JsonOps.INSTANCE, depth65)),
                () -> assertTrue(SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, depth64)).isSuccess()),
                () -> assertCompleteError(SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, depth65))),
                () -> assertTrue(SkillDocumentWriter.write(modelWithPayload(nestedJson(60)), JsonOps.INSTANCE)
                        .isSuccess()),
                () -> assertCompleteError(SkillDocumentWriter.write(
                        modelWithPayload(nestedJson(61)), JsonOps.INSTANCE)));
    }

    @Test
    void globalDraftDepth64PassesAnd65FailsForStrictReaderAndWriter() {
        var depth64 = draftWithActionPayload(nestedJson(59));
        var depth65 = draftWithActionPayload(nestedJson(60));

        assertAll(
                () -> assertTrue(SkillDraft.CODEC.parse(JsonOps.INSTANCE, depth64).isSuccess()),
                () -> assertCompleteError(SkillDraft.CODEC.parse(JsonOps.INSTANCE, depth65)),
                () -> assertTrue(SkillDraftReader.read(new Dynamic<>(JsonOps.INSTANCE, depth64)).isSuccess()),
                () -> assertCompleteError(SkillDraftReader.read(new Dynamic<>(JsonOps.INSTANCE, depth65))),
                () -> assertTrue(SkillDraftWriter.write(draftModelWithPayload(nestedJson(59)), JsonOps.INSTANCE)
                        .isSuccess()),
                () -> assertCompleteError(SkillDraftWriter.write(
                        draftModelWithPayload(nestedJson(60)), JsonOps.INSTANCE)));
    }

    @Test
    void appearanceRelativeDepth32PassesAnd33BecomesRejected() {
        var atLimit = read(DocumentTestFixtures.documentJson(nestedUnknownAppearance(32)));
        var overLimit = read(DocumentTestFixtures.documentJson(nestedUnknownAppearance(33)));
        var rejected = assertInstanceOf(AppearanceDocument.Rejected.class, overLimit.document().appearance());
        var written = SkillDocumentWriter.write(overLimit.document(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Default.class, atLimit.document().appearance()),
                () -> assertEquals(AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED, rejected.reason()),
                () -> assertEquals(new JsonObject(), written.getAsJsonObject().get("appearance")));
    }

    @Test
    void appearanceNodeCount1024PassesAnd1025BecomesRejectedWithoutRaw() {
        var atLimit = appearanceArrayWithTotalNodes(1_024);
        var overLimit = appearanceArrayWithTotalNodes(1_025);
        var accepted = read(DocumentTestFixtures.documentJson(atLimit));
        var rejectedResult = read(DocumentTestFixtures.documentJson(overLimit));
        var rejected = assertInstanceOf(
                AppearanceDocument.Rejected.class,
                rejectedResult.document().appearance());

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Default.class, accepted.document().appearance()),
                () -> assertEquals(AppearanceRejectionCode.NODE_LIMIT_EXCEEDED, rejected.reason()),
                () -> assertFalse(List.of(rejected.getClass().getRecordComponents()).stream()
                        .anyMatch(component -> component.getName().toLowerCase().contains("raw"))));
    }

    @Test
    void readReportCapsAt1024IsImmutableDeterministicAndNotPersisted() {
        var input = documentWithManyLegacyFacts();
        var first = read(input);
        var second = read(input.deepCopy());
        var written = SkillDocumentWriter.write(first.document(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertEquals(MagicSafetyCeilings.MAX_READ_REPORT_FACTS, first.report().facts().size()),
                () -> assertTrue(first.report().truncated()),
                () -> assertEquals(first.report(), second.report()),
                () -> assertThrows(UnsupportedOperationException.class, () -> first.report().facts().clear()),
                () -> assertFalse(written.toString().contains("INTENSITY_CLAMPED")),
                () -> assertFalse(written.toString().contains("truncated")),
                () -> assertEquals(first.document(), second.document()));
    }

    @Test
    void hardAndDefaultLimitsHaveDistinctBoundedSemantics() {
        assertAll(
                () -> assertTrue(MagicSafetyCeilings.DEFAULT_UNPARSED_APPEARANCE_DEPTH
                        <= MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_DEPTH),
                () -> assertTrue(MagicSafetyCeilings.DEFAULT_UNPARSED_APPEARANCE_NODES
                        <= MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_NODES),
                () -> assertTrue(MagicSafetyCeilings.DEFAULT_SKILL_DOCUMENT_DEPTH
                        <= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH),
                () -> assertTrue(MagicSafetyCeilings.DEFAULT_SKILL_DOCUMENT_BYTES
                        <= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES),
                () -> assertEquals(1_024, MagicSafetyCeilings.MAX_READ_REPORT_FACTS),
                () -> assertTrue(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES
                        > MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES));
    }

    private static SkillDocumentReadResult read(JsonElement input) {
        return SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow();
    }

    private static JsonObject documentWithTriggerPayload(JsonElement payload) {
        var document = DocumentTestFixtures.documentJson(new JsonObject());
        document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("trigger").add("payload", payload);
        return document;
    }

    private static SkillDocument modelWithPayload(JsonElement payload) {
        var trigger = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", "trigger"),
                0,
                new Dynamic<>(JsonOps.INSTANCE, payload));
        var node = new NodeDocument(
                trigger,
                DocumentTestFixtures.envelope("action"),
                AppearanceOverrideDocument.none());
        return new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new SkillRevision(0),
                List.of(node),
                AppearanceDocument.defaultAppearance());
    }

    private static JsonObject draftWithActionPayload(JsonElement payload) {
        var draft = DocumentTestFixtures.draftJson(new JsonObject());
        draft.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("action").getAsJsonObject("definition").add("payload", payload);
        return draft;
    }

    private static SkillDraft draftModelWithPayload(JsonElement payload) {
        var action = new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", "action"),
                0,
                new Dynamic<>(JsonOps.INSTANCE, payload));
        var node = new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.present(action),
                AppearanceOverrideDocument.none());
        return new SkillDraft(
                0,
                DocumentTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(node),
                AppearanceDocument.defaultAppearance());
    }

    private static JsonElement nestedJson(int depth) {
        JsonElement value = JsonNull.INSTANCE;
        for (var currentDepth = 1; currentDepth < depth; currentDepth++) {
            var object = new JsonObject();
            object.add("next", value);
            value = object;
        }
        return value;
    }

    private static JsonObject nestedUnknownAppearance(int depth) {
        var root = nestedJson(depth);
        if (root instanceof JsonObject object) {
            var renamed = new JsonObject();
            renamed.add("future", object.get("next"));
            return renamed;
        }
        throw new IllegalArgumentException("depth must exceed one");
    }

    private static JsonObject appearanceArrayWithTotalNodes(int totalNodes) {
        var array = new JsonArray();
        for (var index = 0; index < totalNodes - 2; index++) {
            array.add(index);
        }
        var appearance = new JsonObject();
        appearance.add("future", array);
        return appearance;
    }

    private static JsonObject documentWithManyLegacyFacts() {
        var template = DocumentTestFixtures.documentJson(new JsonObject());
        var nodeTemplate = template.getAsJsonArray("nodes").get(0).getAsJsonObject();
        var legacy = new JsonObject();
        legacy.add("primary_argb", JsonNull.INSTANCE);
        legacy.add("secondary_argb", JsonNull.INSTANCE);
        legacy.add("sound_profile", JsonNull.INSTANCE);
        legacy.add("particle_profile", JsonNull.INSTANCE);
        legacy.add("trail_profile", JsonNull.INSTANCE);
        legacy.add("intensity_milli", JsonNull.INSTANCE);
        legacy.addProperty("future", true);
        var nodes = new JsonArray();
        for (var index = 0; index < MagicSafetyCeilings.MAX_NODES; index++) {
            var node = nodeTemplate.deepCopy();
            node.add("appearance_override", legacy.deepCopy());
            nodes.add(node);
        }
        template.add("nodes", nodes);
        return template;
    }

    private static void assertCompleteError(com.mojang.serialization.DataResult<?> result) {
        assertTrue(result.error().isPresent());
        assertTrue(result.result().isEmpty());
    }
}
