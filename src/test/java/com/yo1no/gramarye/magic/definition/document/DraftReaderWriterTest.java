package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class DraftReaderWriterTest {
    @Test
    void tolerantDraftNormalizesLegacyAppearanceAndOverrideNullThenWritesCanonicalShape() {
        var input = DocumentTestFixtures.draftJson(JsonNull.INSTANCE);
        input.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .add("appearance_override", JsonNull.INSTANCE);
        var result = SkillDraftReader.read(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow();
        var written = SkillDraftWriter.write(result.draft(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Default.class, result.draft().appearance()),
                () -> assertInstanceOf(
                        AppearanceOverrideDocument.None.class,
                        result.draft().nodes().get(0).appearanceOverride()),
                () -> assertEquals(ReadFactCode.LEGACY_NULL_OVERRIDE_NORMALIZED,
                        result.report().facts().get(0).code()),
                () -> assertEquals(ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED,
                        result.report().facts().get(1).code()),
                () -> assertFalse(written.getAsJsonObject().getAsJsonArray("nodes")
                        .get(0).getAsJsonObject().has("appearance_override")),
                () -> assertEquals(new JsonObject(), written.getAsJsonObject().get("appearance")),
                () -> assertTrue(SkillDraft.CODEC.parse(JsonOps.INSTANCE, written).isSuccess()));
    }

    @Test
    void malformedDraftAppearanceUsesSameFamilyStorageBoundary() {
        var input = DocumentTestFixtures.draftJson(
                JsonParser.parseString("{\"intensity_milli\":1000.5}"));
        var result = SkillDraftReader.read(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow();
        var written = SkillDraftWriter.write(result.draft(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Unparsed.class, result.draft().appearance()),
                () -> assertEquals(input.get("appearance"), written.getAsJsonObject().get("appearance")),
                () -> assertTrue(SkillDraftWriter.write(result.draft(), net.minecraft.nbt.NbtOps.INSTANCE)
                        .error().isPresent()));
    }
}
