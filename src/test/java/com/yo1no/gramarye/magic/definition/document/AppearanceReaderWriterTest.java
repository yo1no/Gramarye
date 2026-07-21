package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class AppearanceReaderWriterTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER = HolderLookup.Provider.create(Stream.empty());

    @Test
    void missingNullAndEmptyAppearanceBecomeDefaultAndWriterUsesCanonicalEmptyObject() {
        var missing = read(DocumentTestFixtures.documentJson(null));
        var legacyNull = read(DocumentTestFixtures.documentJson(JsonNull.INSTANCE));
        var empty = read(DocumentTestFixtures.documentJson(new JsonObject()));
        var written = SkillDocumentWriter.write(missing.document(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Default.class, missing.document().appearance()),
                () -> assertInstanceOf(AppearanceDocument.Default.class, legacyNull.document().appearance()),
                () -> assertInstanceOf(AppearanceDocument.Default.class, empty.document().appearance()),
                () -> assertTrue(missing.report().facts().isEmpty()),
                () -> assertEquals(
                        ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED,
                        legacyNull.report().facts().getFirst().code()),
                () -> assertEquals(new JsonObject(), written.getAsJsonObject().get("appearance")));
    }

    @Test
    void unknownAppearanceFieldsAreReportedWithoutNamesAndStrippedByCanonicalWrite() {
        var appearance = JsonParser.parseString("""
                {
                  "primary_argb":"#ff3366cc",
                  "future_secret_field":{"secret":"must-not-enter-report"}
                }
                """);
        var result = read(DocumentTestFixtures.documentJson(appearance));
        var written = SkillDocumentWriter.write(result.document(), JsonOps.INSTANCE).getOrThrow();
        var reread = read(written);

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Decoded.class, result.document().appearance()),
                () -> assertEquals(
                        ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED,
                        result.report().facts().getFirst().code()),
                () -> assertFalse(result.report().toString().contains("future_secret_field")),
                () -> assertFalse(result.report().toString().contains("must-not-enter-report")),
                () -> assertFalse(written.getAsJsonObject().getAsJsonObject("appearance")
                        .has("future_secret_field")),
                () -> assertTrue(reread.report().facts().isEmpty()));
    }

    @Test
    void eachProfileSupportsInheritDisabledSpecifiedAndLegacyNull() {
        for (var field : List.of("sound_profile", "particle_profile", "trail_profile")) {
            var disabled = read(DocumentTestFixtures.documentJson(JsonParser.parseString(
                    "{\"" + field + "\":{\"mode\":\"disabled\"}}")));
            var specified = read(DocumentTestFixtures.documentJson(JsonParser.parseString(
                    "{\"" + field + "\":{\"mode\":\"specified\",\"id\":\"gramarye:test\"}}")));
            var legacyNull = read(DocumentTestFixtures.documentJson(JsonParser.parseString(
                    "{\"" + field + "\":null}")));

            assertInstanceOf(AppearanceDocument.Decoded.class, disabled.document().appearance());
            assertInstanceOf(AppearanceDocument.Decoded.class, specified.document().appearance());
            assertInstanceOf(AppearanceDocument.Decoded.class, legacyNull.document().appearance());
            assertEquals(ReadFactCode.LEGACY_NULL_PROFILE_NORMALIZED,
                    legacyNull.report().facts().getFirst().code());
        }
    }

    @Test
    void invalidProfileTaggedStateMakesWholeBlobUnparsed() {
        var malformed = read(DocumentTestFixtures.documentJson(JsonParser.parseString("""
                {"primary_argb":"0xFF112233","sound_profile":{"mode":"specified"}}
                """)));
        assertInstanceOf(AppearanceDocument.Unparsed.class, malformed.document().appearance());
    }

    @Test
    void tolerantArgbAcceptsLegacyAndSignedUnsignedFormsThenWritesUppercaseBitPattern() {
        var inputs = List.of(
                "\"0xFFFFFFFF\"",
                "\"#ffffffff\"",
                "\"ffffffff\"",
                "-1",
                "4294967295",
                "4294967295.0");
        for (var input : inputs) {
            var result = read(DocumentTestFixtures.documentJson(
                    JsonParser.parseString("{\"primary_argb\":" + input + "}")));
            var written = SkillDocumentWriter.write(result.document(), JsonOps.INSTANCE).getOrThrow();
            assertEquals("0xFFFFFFFF", written.getAsJsonObject()
                    .getAsJsonObject("appearance").get("primary_argb").getAsString());
            assertTrue(result.report().facts().stream()
                    .noneMatch(fact -> fact.code() == ReadFactCode.INTENSITY_CLAMPED_LOW
                            || fact.code() == ReadFactCode.INTENSITY_CLAMPED_HIGH));
        }
    }

    @Test
    void argbOutOfRangeAndFractionAreUnparsedRatherThanClamped() {
        for (var input : List.of("-2147483649", "4294967296", "1.5")) {
            var result = read(DocumentTestFixtures.documentJson(
                    JsonParser.parseString("{\"primary_argb\":" + input + "}")));
            assertInstanceOf(AppearanceDocument.Unparsed.class, result.document().appearance());
            assertTrue(result.report().facts().isEmpty());
        }
    }

    @Test
    void exactIntegralIntensityNormalizesAndClampsWithFacts() {
        assertIntensity("1000", 1_000, null);
        assertIntensity("1000.0", 1_000, null);
        assertIntensity("12000", 10_000, ReadFactCode.INTENSITY_CLAMPED_HIGH);
        assertIntensity("12000.0", 10_000, ReadFactCode.INTENSITY_CLAMPED_HIGH);
        assertIntensity("-1", 0, ReadFactCode.INTENSITY_CLAMPED_LOW);
        assertIntensity("-1.0", 0, ReadFactCode.INTENSITY_CLAMPED_LOW);

        var fractional = read(DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"intensity_milli\":1000.5}")));
        var nanAppearance = new JsonObject();
        nanAppearance.add("intensity_milli", new JsonPrimitive(Double.NaN));
        var infinityAppearance = new JsonObject();
        infinityAppearance.add("intensity_milli", new JsonPrimitive(Double.POSITIVE_INFINITY));

        assertAll(
                () -> assertInstanceOf(AppearanceDocument.Unparsed.class, fractional.document().appearance()),
                () -> assertInstanceOf(AppearanceDocument.Unparsed.class,
                        read(DocumentTestFixtures.documentJson(nanAppearance)).document().appearance()),
                () -> assertInstanceOf(AppearanceDocument.Unparsed.class,
                        read(DocumentTestFixtures.documentJson(infinityAppearance)).document().appearance()));
    }

    @Test
    void malformedJsonAppearanceIsDeepSnapshottedAndSameFamilyPreserved() {
        var appearance = JsonParser.parseString("""
                {"primary_argb":"bad","nested":{"value":"original"}}
                """).getAsJsonObject();
        var input = DocumentTestFixtures.documentJson(appearance);
        var result = read(input);
        var unparsed = assertInstanceOf(AppearanceDocument.Unparsed.class, result.document().appearance());

        appearance.addProperty("root_mutation", true);
        appearance.getAsJsonObject("nested").addProperty("value", "source-mutated");
        var first = (JsonObject) unparsed.copyRawAppearance().getValue();
        first.addProperty("accessor_mutation", true);
        first.getAsJsonObject("nested").addProperty("value", "accessor-mutated");
        var second = (JsonObject) unparsed.copyRawAppearance().getValue();
        var written = SkillDocumentWriter.write(result.document(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertNotSame(first, second),
                () -> assertFalse(unparsed.toString().contains("original")),
                () -> assertFalse(second.has("root_mutation")),
                () -> assertFalse(second.has("accessor_mutation")),
                () -> assertEquals("original", second.getAsJsonObject("nested").get("value").getAsString()),
                () -> assertEquals(second, written.getAsJsonObject().get("appearance")),
                () -> assertTrue(SkillDocumentWriter.write(result.document(), NbtOps.INSTANCE).error().isPresent()));
    }

    @Test
    void registryOpsWrappedNbtUnparsedSnapshotRetainsWrapperAndIsolation() {
        var ops = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var appearance = new CompoundTag();
        appearance.putString("primary_argb", "bad");
        var nested = new CompoundTag();
        nested.putString("value", "original");
        appearance.put("nested", nested);
        var root = SkillDocument.CODEC.encodeStart(ops,
                DocumentTestFixtures.document(AppearanceDocument.defaultAppearance())).getOrThrow();
        ((CompoundTag) root).put("appearance", appearance);
        var result = SkillDocumentReader.read(new Dynamic<>(ops, root)).getOrThrow();
        var unparsed = assertInstanceOf(AppearanceDocument.Unparsed.class, result.document().appearance());

        appearance.getCompound("nested").putString("value", "source-mutated");
        var first = (CompoundTag) unparsed.copyRawAppearance().getValue();
        first.getCompound("nested").putString("value", "accessor-mutated");
        var secondDynamic = unparsed.copyRawAppearance();
        var second = (CompoundTag) secondDynamic.getValue();
        var written = SkillDocumentWriter.write(result.document(), ops).getOrThrow();

        assertAll(
                () -> assertInstanceOf(RegistryOps.class, secondDynamic.getOps()),
                () -> assertEquals("original", second.getCompound("nested").getString("value")),
                () -> assertEquals(second, ((CompoundTag) written).get("appearance")));
    }

    private static SkillDocumentReadResult read(com.google.gson.JsonElement input) {
        return SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, input)).getOrThrow();
    }

    private static void assertIntensity(String input, int expected, ReadFactCode expectedFact) {
        var result = read(DocumentTestFixtures.documentJson(
                JsonParser.parseString("{\"intensity_milli\":" + input + "}")));
        var decoded = assertInstanceOf(AppearanceDocument.Decoded.class, result.document().appearance());
        assertEquals(OptionalInt.of(expected), decoded.definition().intensityMilli());
        var written = SkillDocumentWriter.write(result.document(), JsonOps.INSTANCE).getOrThrow();
        var encodedIntensity = written.getAsJsonObject()
                .getAsJsonObject("appearance").get("intensity_milli");
        assertTrue(encodedIntensity.getAsJsonPrimitive().isNumber());
        assertFalse(encodedIntensity.getAsString().contains("."));
        if (expectedFact == null) {
            assertTrue(result.report().facts().isEmpty());
        } else {
            assertEquals(expectedFact, result.report().facts().getFirst().code());
        }
    }
}
