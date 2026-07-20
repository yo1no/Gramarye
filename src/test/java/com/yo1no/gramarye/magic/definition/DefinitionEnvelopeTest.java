package com.yo1no.gramarye.magic.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefinitionEnvelopeTest {
    private static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("example", "nested_payload");

    @Test
    void jsonCodecUsesExactFieldNamesAndPreservesNestedPayload() {
        var rawPayload = json("""
                {
                  "nested": {"enabled": true, "values": [1, 2, 3]},
                  "label": "kept"
                }
                """);
        var envelope = jsonEnvelope(4, rawPayload);

        var encoded = DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, envelope).getOrThrow();
        assertEquals(Set.of("type", "schema_version", "payload"), encoded.getAsJsonObject().keySet());
        assertEquals("example:nested_payload", encoded.getAsJsonObject().get("type").getAsString());
        assertEquals(4, encoded.getAsJsonObject().get("schema_version").getAsInt());
        assertEquals(rawPayload, encoded.getAsJsonObject().get("payload"));

        var decoded = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(TYPE_ID, decoded.typeId());
        assertEquals(4, decoded.schemaVersion());
        assertEquals(rawPayload, decoded.copyRawPayload().getValue());
    }

    @Test
    void nbtCodecPreservesTypeSchemaAndPayload() {
        var rawPayload = new CompoundTag();
        rawPayload.putString("label", "kept");
        var values = new ListTag();
        values.add(StringTag.valueOf("one"));
        values.add(StringTag.valueOf("two"));
        rawPayload.put("values", values);
        var envelope = new DefinitionEnvelope(TYPE_ID, 7, new Dynamic<>(NbtOps.INSTANCE, rawPayload));

        var encoded = DefinitionEnvelope.CODEC.encodeStart(NbtOps.INSTANCE, envelope).getOrThrow();
        var encodedCompound = (CompoundTag) encoded;
        assertEquals(Set.of("type", "schema_version", "payload"), encodedCompound.getAllKeys());

        var decoded = DefinitionEnvelope.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(TYPE_ID, decoded.typeId());
        assertEquals(7, decoded.schemaVersion());
        assertEquals(rawPayload, decoded.copyRawPayload().getValue());
    }

    @Test
    void simplePayloadCanConvertBetweenJsonAndNbtOps() {
        var rawPayload = json("{\"count\": 3, \"name\": \"simple\"}");
        var envelope = jsonEnvelope(1, rawPayload);

        var nbt = DefinitionEnvelope.CODEC.encodeStart(NbtOps.INSTANCE, envelope).getOrThrow();
        var fromNbt = DefinitionEnvelope.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
        var json = DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, fromNbt).getOrThrow();
        var roundTripped = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(TYPE_ID, roundTripped.typeId());
        assertEquals(1, roundTripped.schemaVersion());
        assertEquals(rawPayload, roundTripped.copyRawPayload().getValue());
    }

    @Test
    void constructorRejectsNullsAndNegativeSchemaVersion() {
        var payload = new Dynamic<>(JsonOps.INSTANCE, json("{}"));
        assertThrows(NullPointerException.class, () -> new DefinitionEnvelope(null, 0, payload));
        assertThrows(NullPointerException.class, () -> new DefinitionEnvelope(TYPE_ID, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new DefinitionEnvelope(TYPE_ID, -1, payload));

        var encodedNegative = json("""
                {
                  "type": "example:nested_payload",
                  "schema_version": -1,
                  "payload": {}
                }
                """);
        assertTrue(DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, encodedNegative).error().isPresent());
    }

    @Test
    void definitionFailureBoundsDiagnosticsAndRejectsNulls() {
        var longDiagnostic = "x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH + 50);
        var failure = DefinitionFailure.of(DefinitionFailure.Code.CODEC_EXCEPTION, longDiagnostic);

        assertEquals(MagicSafetyCeilings.MAX_STRING_LENGTH, failure.diagnostic().length());
        assertThrows(NullPointerException.class, () -> DefinitionFailure.of(null, "diagnostic"));
        assertThrows(NullPointerException.class, () -> DefinitionFailure.of(
                DefinitionFailure.Code.UNKNOWN_TYPE,
                null));
        assertThrows(IllegalArgumentException.class, () -> new DefinitionFailure(
                DefinitionFailure.Code.CODEC_EXCEPTION,
                longDiagnostic));
    }

    private static DefinitionEnvelope jsonEnvelope(int schemaVersion, JsonElement rawPayload) {
        return new DefinitionEnvelope(TYPE_ID, schemaVersion, new Dynamic<>(JsonOps.INSTANCE, rawPayload));
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
