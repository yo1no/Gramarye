package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class P3AErrorIsolationGateTest {
    @Test
    void unsupportedOpsAndMalformedRootsReturnOnlyErrors() {
        var unsupportedRoot = JavaOps.INSTANCE.createMap(Stream.of(Pair.of(
                JavaOps.INSTANCE.createString("value"),
                JavaOps.INSTANCE.createString("unsupported"))));
        var unsupported = new Dynamic<>(JavaOps.INSTANCE, unsupportedRoot);
        var malformedDocumentRoot = new Dynamic<>(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("not-an-object"));
        var malformedDraftRoot = new Dynamic<>(JsonOps.INSTANCE, JsonOps.INSTANCE.createList(Stream.of(
                JsonOps.INSTANCE.createString("not-an-object"))));

        assertAll(
                () -> assertErrorOnly(() -> SkillDocumentReader.read(unsupported)),
                () -> assertErrorOnly(() -> SkillDraftReader.read(unsupported)),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.parse(unsupported)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(unsupported)),
                () -> assertErrorOnly(() -> SkillDocumentReader.read(malformedDocumentRoot)),
                () -> assertErrorOnly(() -> SkillDraftReader.read(malformedDraftRoot)),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.parse(malformedDocumentRoot)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(malformedDraftRoot)),
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(
                        DocumentTestFixtures.document(AppearanceDocument.defaultAppearance()), JavaOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(
                        DocumentTestFixtures.draft(AppearanceDocument.defaultAppearance()), JavaOps.INSTANCE)));
    }

    @Test
    void globalDepthAndNodeCountFailuresNeverThrowOrExposePartial() {
        var depth65Document = documentWithTriggerPayload(nestedJson(61));
        var depth65Draft = draftWithActionPayload(nestedJson(60));
        var tooManyNodesDocument = documentWithNodeCount(MagicSafetyCeilings.MAX_NODES + 1);
        var tooManyNodesDraft = draftWithNodeCount(MagicSafetyCeilings.MAX_NODES + 1);

        assertAll(
                () -> assertErrorOnly(() -> SkillDocumentReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, depth65Document))),
                () -> assertErrorOnly(() -> SkillDraftReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, depth65Draft))),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.parse(JsonOps.INSTANCE, depth65Document)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(JsonOps.INSTANCE, depth65Draft)),
                () -> assertErrorOnly(() -> SkillDocumentReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, tooManyNodesDocument))),
                () -> assertErrorOnly(() -> SkillDraftReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, tooManyNodesDraft))),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.parse(JsonOps.INSTANCE, tooManyNodesDocument)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(JsonOps.INSTANCE, tooManyNodesDraft)));
    }

    @Test
    void outerNodeAndEnvelopeCorruptionNeverEscapesAsAnException() {
        var missingOuter = DocumentTestFixtures.documentJson(new JsonObject());
        missingOuter.remove("skill_id");
        var wrongOuterType = DocumentTestFixtures.documentJson(new JsonObject());
        wrongOuterType.addProperty("nodes", "not-a-list");
        var missingTrigger = DocumentTestFixtures.documentJson(new JsonObject());
        missingTrigger.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("trigger");
        var nullTrigger = DocumentTestFixtures.documentJson(new JsonObject());
        nullTrigger.getAsJsonArray("nodes").get(0).getAsJsonObject().add("trigger", JsonNull.INSTANCE);
        var missingAction = DocumentTestFixtures.documentJson(new JsonObject());
        missingAction.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("action");
        var nullAction = DocumentTestFixtures.documentJson(new JsonObject());
        nullAction.getAsJsonArray("nodes").get(0).getAsJsonObject().add("action", JsonNull.INSTANCE);
        var brokenEnvelope = DocumentTestFixtures.documentJson(new JsonObject());
        brokenEnvelope.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("trigger").remove("payload");

        for (var corrupted : List.of(
                missingOuter,
                wrongOuterType,
                missingTrigger,
                nullTrigger,
                missingAction,
                nullAction,
                brokenEnvelope)) {
            assertErrorOnly(() -> SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, corrupted)));
            assertErrorOnly(() -> SkillDocument.CODEC.parse(JsonOps.INSTANCE, corrupted));
        }

        var draftMissingOuter = DocumentTestFixtures.draftJson(new JsonObject());
        draftMissingOuter.remove("nodes");
        var draftWrongOuter = DocumentTestFixtures.draftJson(new JsonObject());
        draftWrongOuter.addProperty("draft_schema_version", "wrong");
        assertAll(
                () -> assertErrorOnly(() -> SkillDraftReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, draftMissingOuter))),
                () -> assertErrorOnly(() -> SkillDraftReader.read(
                        new Dynamic<>(JsonOps.INSTANCE, draftWrongOuter))),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(JsonOps.INSTANCE, draftMissingOuter)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.parse(JsonOps.INSTANCE, draftWrongOuter)));
    }

    @Test
    void rawSnapshotAndCrossFamilyFailuresAreContained() {
        var unsupportedRaw = JavaOps.INSTANCE.createMap(Stream.of(Pair.of(
                JavaOps.INSTANCE.createString("raw"), JavaOps.INSTANCE.createString("unsupported"))));
        var unsupportedDynamic = new Dynamic<>(JavaOps.INSTANCE, unsupportedRaw);
        var jsonDocument = documentWithAppearance(unparsedJson());
        var nbtDraft = draftWithAppearance(unparsedNbt());

        assertAll(
                () -> assertErrorOnly(() -> AppearanceRawSnapshot.capture(unsupportedDynamic)),
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(jsonDocument, NbtOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(nbtDraft, JsonOps.INSTANCE)));
    }

    @Test
    void writerAndStrictCodecRejectOutputBeyondGlobalDepthWithoutThrowing() {
        var document = documentModelWithTriggerPayload(nestedJson(61));
        var draft = draftModelWithActionPayload(nestedJson(60));

        assertAll(
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(document, JsonOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(draft, JsonOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, document)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.encodeStart(JsonOps.INSTANCE, draft)));
    }

    private static JsonObject documentWithTriggerPayload(JsonElement payload) {
        var document = DocumentTestFixtures.documentJson(new JsonObject());
        document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("trigger").add("payload", payload);
        return document;
    }

    private static JsonObject draftWithActionPayload(JsonElement payload) {
        var draft = DocumentTestFixtures.draftJson(new JsonObject());
        draft.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("action").getAsJsonObject("definition").add("payload", payload);
        return draft;
    }

    private static JsonObject documentWithNodeCount(int count) {
        var document = DocumentTestFixtures.documentJson(new JsonObject());
        var template = document.getAsJsonArray("nodes").get(0).getAsJsonObject();
        var nodes = new JsonArray();
        for (var index = 0; index < count; index++) {
            nodes.add(template.deepCopy());
        }
        document.add("nodes", nodes);
        return document;
    }

    private static JsonObject draftWithNodeCount(int count) {
        var draft = DocumentTestFixtures.draftJson(new JsonObject());
        var template = draft.getAsJsonArray("nodes").get(0).getAsJsonObject();
        var nodes = new JsonArray();
        for (var index = 0; index < count; index++) {
            nodes.add(template.deepCopy());
        }
        draft.add("nodes", nodes);
        return draft;
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

    private static SkillDocument documentModelWithTriggerPayload(JsonElement payload) {
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

    private static SkillDraft draftModelWithActionPayload(JsonElement payload) {
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

    private static AppearanceDocument.Unparsed unparsedJson() {
        var raw = JsonParser.parseString("{\"primary_argb\":\"bad\"}");
        return new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(
                new Dynamic<>(JsonOps.INSTANCE, raw)).getOrThrow());
    }

    private static AppearanceDocument.Unparsed unparsedNbt() {
        var raw = new net.minecraft.nbt.CompoundTag();
        raw.putString("primary_argb", "bad");
        return new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(
                new Dynamic<>(NbtOps.INSTANCE, raw)).getOrThrow());
    }

    private static SkillDocument documentWithAppearance(AppearanceDocument appearance) {
        return DocumentTestFixtures.document(appearance);
    }

    private static SkillDraft draftWithAppearance(AppearanceDocument appearance) {
        return DocumentTestFixtures.draft(appearance);
    }

    private static void assertErrorOnly(Supplier<? extends DataResult<?>> operation) {
        var result = assertDoesNotThrow(operation::get);
        assertAll(
                () -> assertTrue(result.error().isPresent()),
                () -> assertTrue(result.result().isEmpty()),
                () -> assertTrue(result.resultOrPartial().isEmpty()));
    }
}
