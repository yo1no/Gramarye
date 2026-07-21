package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class P3AFinalStorageGateTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER_ONE = HolderLookup.Provider.create(Stream.empty());
    private static final HolderLookup.Provider EMPTY_PROVIDER_TWO = HolderLookup.Provider.create(Stream.empty());

    @Test
    void strictCodecsRejectUnparsedWithoutExceptionSuccessOrPartial() {
        var document = document(unparsedJsonAppearance(jsonRaw("document-secret")),
                AppearanceOverrideDocument.none());
        var draft = draft(unparsedNbtAppearance(nbtRaw("draft-secret")),
                AppearanceOverrideDocument.none());

        assertAll(
                () -> assertErrorOnly(() -> SkillDocument.CODEC.encodeStart(JsonOps.INSTANCE, document)),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.encodeStart(NbtOps.INSTANCE, draft)));
    }

    @Test
    void strictCodecsRejectRejectedTopAndOverrideInsteadOfWritingFallback() {
        var rejectedTop = new AppearanceDocument.Rejected(AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var rejectedOverride = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED);

        assertAll(
                () -> assertErrorOnly(() -> SkillDocument.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        document(rejectedTop, AppearanceOverrideDocument.none()))),
                () -> assertErrorOnly(() -> SkillDocument.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        document(AppearanceDocument.defaultAppearance(), rejectedOverride))),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        draft(rejectedTop, AppearanceOverrideDocument.none()))),
                () -> assertErrorOnly(() -> SkillDraft.CODEC.encodeStart(
                        JsonOps.INSTANCE,
                        draft(AppearanceDocument.defaultAppearance(), rejectedOverride))));
    }

    @Test
    void storageWritersPreserveUnparsedJsonAndNbtForDocumentAndDraft() {
        var documentJsonRaw = jsonRaw("document-json-secret");
        var documentNbtRaw = nbtRaw("document-nbt-secret");
        var draftJsonRaw = jsonRaw("draft-json-secret");
        var draftNbtRaw = nbtRaw("draft-nbt-secret");

        var documentJson = document(unparsedJsonAppearance(documentJsonRaw), AppearanceOverrideDocument.none());
        var documentNbt = document(unparsedNbtAppearance(documentNbtRaw), AppearanceOverrideDocument.none());
        var draftJson = draft(unparsedJsonAppearance(draftJsonRaw), AppearanceOverrideDocument.none());
        var draftNbt = draft(unparsedNbtAppearance(draftNbtRaw), AppearanceOverrideDocument.none());

        var encodedDocumentJson = assertDoesNotThrow(
                () -> SkillDocumentWriter.write(documentJson, JsonOps.INSTANCE)).getOrThrow();
        var encodedDocumentNbt = assertDoesNotThrow(
                () -> SkillDocumentWriter.write(documentNbt, NbtOps.INSTANCE)).getOrThrow();
        var encodedDraftJson = assertDoesNotThrow(
                () -> SkillDraftWriter.write(draftJson, JsonOps.INSTANCE)).getOrThrow();
        var encodedDraftNbt = assertDoesNotThrow(
                () -> SkillDraftWriter.write(draftNbt, NbtOps.INSTANCE)).getOrThrow();

        assertAll(
                () -> assertEquals(documentJsonRaw, encodedDocumentJson.getAsJsonObject().get("appearance")),
                () -> assertEquals(documentNbtRaw, ((CompoundTag) encodedDocumentNbt).get("appearance")),
                () -> assertEquals(draftJsonRaw, encodedDraftJson.getAsJsonObject().get("appearance")),
                () -> assertEquals(draftNbtRaw, ((CompoundTag) encodedDraftNbt).get("appearance")));
    }

    @Test
    void storageWritersPreserveUnparsedOverridesInTheirOriginalFamily() {
        var jsonRaw = jsonRaw("document-json-override");
        var nbtRaw = nbtRaw("draft-nbt-override");
        var document = document(
                AppearanceDocument.defaultAppearance(),
                unparsedJsonOverride(jsonRaw));
        var draft = draft(
                AppearanceDocument.defaultAppearance(),
                unparsedNbtOverride(nbtRaw));

        var encodedDocument = assertDoesNotThrow(
                () -> SkillDocumentWriter.write(document, JsonOps.INSTANCE)).getOrThrow();
        var encodedDraft = assertDoesNotThrow(
                () -> SkillDraftWriter.write(draft, NbtOps.INSTANCE)).getOrThrow();

        assertAll(
                () -> assertEquals(jsonRaw, encodedDocument.getAsJsonObject().getAsJsonArray("nodes")
                        .get(0).getAsJsonObject().get("appearance_override")),
                () -> assertEquals(nbtRaw, ((CompoundTag) ((CompoundTag) encodedDraft)
                        .getList("nodes", net.minecraft.nbt.Tag.TAG_COMPOUND).get(0))
                        .get("appearance_override")));
    }

    @Test
    void storageWritersRejectEveryUnparsedCrossFamilyDirectionWithoutPartial() {
        var jsonDocument = document(unparsedJsonAppearance(jsonRaw("json-document")),
                AppearanceOverrideDocument.none());
        var nbtDocument = document(unparsedNbtAppearance(nbtRaw("nbt-document")),
                AppearanceOverrideDocument.none());
        var jsonDraft = draft(unparsedJsonAppearance(jsonRaw("json-draft")),
                AppearanceOverrideDocument.none());
        var nbtDraft = draft(unparsedNbtAppearance(nbtRaw("nbt-draft")),
                AppearanceOverrideDocument.none());
        var jsonOverrideDocument = document(
                AppearanceDocument.defaultAppearance(),
                unparsedJsonOverride(jsonRaw("json-override-document")));
        var nbtOverrideDraft = draft(
                AppearanceDocument.defaultAppearance(),
                unparsedNbtOverride(nbtRaw("nbt-override-draft")));

        assertAll(
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(jsonDocument, NbtOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(nbtDocument, JsonOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(jsonDraft, NbtOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(nbtDraft, JsonOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDocumentWriter.write(jsonOverrideDocument, NbtOps.INSTANCE)),
                () -> assertErrorOnly(() -> SkillDraftWriter.write(nbtOverrideDraft, JsonOps.INSTANCE)));
    }

    @Test
    void storageWritersUseCanonicalRejectedFallbackWithoutDiagnostic() {
        var rejectedTop = new AppearanceDocument.Rejected(AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var rejectedOverride = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED);
        var encodedDocument = assertDoesNotThrow(() -> SkillDocumentWriter.write(
                document(rejectedTop, rejectedOverride), JsonOps.INSTANCE)).getOrThrow();
        var encodedDraft = assertDoesNotThrow(() -> SkillDraftWriter.write(
                draft(rejectedTop, rejectedOverride), JsonOps.INSTANCE)).getOrThrow();

        var documentRoot = encodedDocument.getAsJsonObject();
        var draftRoot = encodedDraft.getAsJsonObject();
        assertAll(
                () -> assertEquals(new JsonObject(), documentRoot.get("appearance")),
                () -> assertFalse(documentRoot.getAsJsonArray("nodes")
                        .get(0).getAsJsonObject().has("appearance_override")),
                () -> assertEquals(new JsonObject(), draftRoot.get("appearance")),
                () -> assertFalse(draftRoot.getAsJsonArray("nodes")
                        .get(0).getAsJsonObject().has("appearance_override")),
                () -> assertFalse(encodedDocument.toString().contains("DEPTH_LIMIT_EXCEEDED")),
                () -> assertFalse(encodedDocument.toString().contains("NODE_LIMIT_EXCEEDED")),
                () -> assertFalse(encodedDraft.toString().contains("DEPTH_LIMIT_EXCEEDED")),
                () -> assertFalse(encodedDraft.toString().contains("NODE_LIMIT_EXCEEDED")));
    }

    @Test
    void unparsedJsonAndNbtEqualityUsesFamilyAndStructureNotIdentity() {
        var firstJson = jsonRaw("same-json");
        var secondJson = firstJson.deepCopy();
        var firstNbt = nbtRaw("same-nbt");
        var secondNbt = firstNbt.copy();
        var jsonAppearanceOne = unparsedJsonAppearance(firstJson);
        var jsonAppearanceTwo = unparsedJsonAppearance(secondJson);
        var jsonOverrideOne = unparsedJsonOverride(firstJson);
        var jsonOverrideTwo = unparsedJsonOverride(secondJson);
        var nbtAppearanceOne = unparsedNbtAppearance(firstNbt);
        var nbtAppearanceTwo = unparsedNbtAppearance(secondNbt);
        var nbtOverrideOne = unparsedNbtOverride(firstNbt);
        var nbtOverrideTwo = unparsedNbtOverride(secondNbt);

        assertAll(
                () -> assertEquals(jsonAppearanceOne, jsonAppearanceTwo),
                () -> assertEquals(jsonAppearanceOne.hashCode(), jsonAppearanceTwo.hashCode()),
                () -> assertEquals(jsonOverrideOne, jsonOverrideTwo),
                () -> assertEquals(jsonOverrideOne.hashCode(), jsonOverrideTwo.hashCode()),
                () -> assertEquals(nbtAppearanceOne, nbtAppearanceTwo),
                () -> assertEquals(nbtAppearanceOne.hashCode(), nbtAppearanceTwo.hashCode()),
                () -> assertEquals(nbtOverrideOne, nbtOverrideTwo),
                () -> assertEquals(nbtOverrideOne.hashCode(), nbtOverrideTwo.hashCode()),
                () -> assertNotEquals(jsonAppearanceOne, unparsedNbtAppearance(nbtEquivalentJsonShape())),
                () -> assertNotEquals(jsonOverrideOne, unparsedNbtOverride(nbtEquivalentJsonShape())));
    }

    @Test
    void registryOpsWrapperIdentityDoesNotAffectUnparsedEqualityOrHashCode() {
        var jsonOpsOne = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER_ONE);
        var jsonOpsTwo = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER_TWO);
        var nbtOpsOne = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER_ONE);
        var nbtOpsTwo = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER_TWO);
        var jsonValue = jsonRaw("registry-json");
        var nbtValue = nbtRaw("registry-nbt");

        var jsonAppearanceOne = unparsedAppearance(new Dynamic<>(jsonOpsOne, jsonValue));
        var jsonAppearanceTwo = unparsedAppearance(new Dynamic<>(jsonOpsTwo, jsonValue.deepCopy()));
        var jsonOverrideOne = unparsedOverride(new Dynamic<>(jsonOpsOne, jsonValue));
        var jsonOverrideTwo = unparsedOverride(new Dynamic<>(jsonOpsTwo, jsonValue.deepCopy()));
        var nbtAppearanceOne = unparsedAppearance(new Dynamic<>(nbtOpsOne, nbtValue));
        var nbtAppearanceTwo = unparsedAppearance(new Dynamic<>(nbtOpsTwo, nbtValue.copy()));
        var nbtOverrideOne = unparsedOverride(new Dynamic<>(nbtOpsOne, nbtValue));
        var nbtOverrideTwo = unparsedOverride(new Dynamic<>(nbtOpsTwo, nbtValue.copy()));

        assertAll(
                () -> assertEquals(jsonAppearanceOne, jsonAppearanceTwo),
                () -> assertEquals(jsonAppearanceOne.hashCode(), jsonAppearanceTwo.hashCode()),
                () -> assertEquals(jsonOverrideOne, jsonOverrideTwo),
                () -> assertEquals(jsonOverrideOne.hashCode(), jsonOverrideTwo.hashCode()),
                () -> assertEquals(nbtAppearanceOne, nbtAppearanceTwo),
                () -> assertEquals(nbtAppearanceOne.hashCode(), nbtAppearanceTwo.hashCode()),
                () -> assertEquals(nbtOverrideOne, nbtOverrideTwo),
                () -> assertEquals(nbtOverrideOne.hashCode(), nbtOverrideTwo.hashCode()));
    }

    @Test
    void constructorAndAccessorMutationCannotChangeEqualityHashOrWriterOutput() {
        var source = jsonRaw("immutable-secret");
        var pristine = source.deepCopy();
        var appearance = unparsedJsonAppearance(source);
        var equivalent = unparsedJsonAppearance(pristine);
        var originalHash = appearance.hashCode();
        var before = SkillDocumentWriter.write(
                document(appearance, AppearanceOverrideDocument.none()), JsonOps.INSTANCE).getOrThrow();

        source.addProperty("root_mutation", true);
        source.getAsJsonObject("nested").addProperty("value", "source-mutated");
        var accessor = (JsonObject) appearance.copyRawAppearance().getValue();
        accessor.addProperty("accessor_mutation", true);
        accessor.getAsJsonObject("nested").addProperty("value", "accessor-mutated");

        var nextAccessor = (JsonObject) appearance.copyRawAppearance().getValue();
        var after = SkillDocumentWriter.write(
                document(appearance, AppearanceOverrideDocument.none()), JsonOps.INSTANCE).getOrThrow();
        assertAll(
                () -> assertEquals(equivalent, appearance),
                () -> assertEquals(originalHash, appearance.hashCode()),
                () -> assertNotSame(accessor, nextAccessor),
                () -> assertEquals(pristine, nextAccessor),
                () -> assertEquals(before, after));
    }

    @Test
    void nbtOverrideConstructorAndAccessorMutationCannotChangeEqualityHashOrWriterOutput() {
        var source = nbtRaw("immutable-nbt-secret");
        var pristine = source.copy();
        var override = unparsedNbtOverride(source);
        var equivalent = unparsedNbtOverride(pristine);
        var originalHash = override.hashCode();
        var before = SkillDocumentWriter.write(
                document(AppearanceDocument.defaultAppearance(), override), NbtOps.INSTANCE).getOrThrow();

        source.putString("root_mutation", "changed");
        source.getCompound("nested").putString("value", "source-mutated");
        var accessor = (CompoundTag) override.copyRawAppearance().getValue();
        accessor.putString("accessor_mutation", "changed");
        accessor.getCompound("nested").putString("value", "accessor-mutated");

        var nextAccessor = (CompoundTag) override.copyRawAppearance().getValue();
        var after = SkillDocumentWriter.write(
                document(AppearanceDocument.defaultAppearance(), override), NbtOps.INSTANCE).getOrThrow();
        assertAll(
                () -> assertEquals(equivalent, override),
                () -> assertEquals(originalHash, override.hashCode()),
                () -> assertNotSame(accessor, nextAccessor),
                () -> assertEquals(pristine, nextAccessor),
                () -> assertEquals(before, after));
    }

    @Test
    void unparsedToStringIsBoundedMetadataOnlyForJsonAndNbtStates() {
        var secret = "UNIQUE_P3A_GATE_SECRET";
        var values = List.of(
                unparsedJsonAppearance(jsonRaw(secret)),
                unparsedJsonOverride(jsonRaw(secret)),
                unparsedNbtAppearance(nbtRaw(secret)),
                unparsedNbtOverride(nbtRaw(secret)));

        for (var value : values) {
            var rendered = value.toString();
            assertFalse(rendered.contains(secret));
            assertFalse(rendered.contains("nested"));
            assertTrue(rendered.length() <= 256);
        }
    }

    private static SkillDocument document(
            AppearanceDocument appearance,
            AppearanceOverrideDocument override) {
        var node = new NodeDocument(
                DocumentTestFixtures.envelope("trigger"),
                DocumentTestFixtures.envelope("action"),
                override);
        return new SkillDocument(
                0,
                DocumentTestFixtures.SKILL_ID,
                new com.yo1no.gramarye.magic.api.id.SkillRevision(0),
                List.of(node),
                appearance);
    }

    private static SkillDraft draft(
            AppearanceDocument appearance,
            AppearanceOverrideDocument override) {
        var node = new DraftNode(
                DraftTriggerSlot.missing(),
                DraftActionSlot.present(DocumentTestFixtures.envelope("action")),
                override);
        return new SkillDraft(
                0,
                DocumentTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(node),
                appearance);
    }

    private static AppearanceDocument.Unparsed unparsedJsonAppearance(JsonObject value) {
        return unparsedAppearance(new Dynamic<>(JsonOps.INSTANCE, value));
    }

    private static AppearanceOverrideDocument.Unparsed unparsedJsonOverride(JsonObject value) {
        return unparsedOverride(new Dynamic<>(JsonOps.INSTANCE, value));
    }

    private static AppearanceDocument.Unparsed unparsedNbtAppearance(CompoundTag value) {
        return unparsedAppearance(new Dynamic<>(NbtOps.INSTANCE, value));
    }

    private static AppearanceOverrideDocument.Unparsed unparsedNbtOverride(CompoundTag value) {
        return unparsedOverride(new Dynamic<>(NbtOps.INSTANCE, value));
    }

    private static AppearanceDocument.Unparsed unparsedAppearance(Dynamic<?> value) {
        return new AppearanceDocument.Unparsed(AppearanceRawSnapshot.capture(value).getOrThrow());
    }

    private static AppearanceOverrideDocument.Unparsed unparsedOverride(Dynamic<?> value) {
        return new AppearanceOverrideDocument.Unparsed(AppearanceRawSnapshot.capture(value).getOrThrow());
    }

    private static JsonObject jsonRaw(String secret) {
        return JsonParser.parseString("""
                {
                  "primary_argb":"malformed",
                  "secret":"%s",
                  "nested":{"value":"original"},
                  "items":[{"value":1},2,3]
                }
                """.formatted(secret)).getAsJsonObject();
    }

    private static CompoundTag nbtRaw(String secret) {
        var value = new CompoundTag();
        value.putString("primary_argb", "malformed");
        value.putString("secret", secret);
        var nested = new CompoundTag();
        nested.putString("value", "original");
        value.put("nested", nested);
        var items = new ListTag();
        items.add(net.minecraft.nbt.IntTag.valueOf(1));
        items.add(net.minecraft.nbt.IntTag.valueOf(2));
        value.put("items", items);
        value.putByteArray("bytes", new byte[] {1, 2, 3});
        value.putIntArray("ints", new int[] {4, 5});
        value.putLongArray("longs", new long[] {6L, 7L});
        return value;
    }

    private static CompoundTag nbtEquivalentJsonShape() {
        var value = new CompoundTag();
        value.putString("primary_argb", "malformed");
        value.putString("secret", "same-json");
        var nested = new CompoundTag();
        nested.putString("value", "original");
        value.put("nested", nested);
        return value;
    }

    private static void assertErrorOnly(Supplier<? extends DataResult<?>> operation) {
        var result = assertDoesNotThrow(operation::get);
        assertAll(
                () -> assertTrue(result.error().isPresent()),
                () -> assertTrue(result.result().isEmpty()),
                () -> assertTrue(result.resultOrPartial().isEmpty()));
    }
}
