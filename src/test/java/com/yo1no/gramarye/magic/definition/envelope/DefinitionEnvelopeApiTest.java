package com.yo1no.gramarye.magic.definition.envelope;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefinitionEnvelopeApiTest {
    private static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("example", "api_contract");
    private static final String SECRET = "unique-secret-that-must-never-be-logged-98f41d";

    @Test
    void jsonEqualityAndHashCodeUsePayloadStructureRatherThanIdentity() {
        var firstPayload = json("{\"nested\":{\"value\":7},\"items\":[1,2]}");
        var secondPayload = json("{\"nested\":{\"value\":7},\"items\":[1,2]}");
        assertNotSame(firstPayload, secondPayload);

        var first = jsonEnvelope(firstPayload);
        var second = jsonEnvelope(secondPayload);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, jsonEnvelope(json("{\"nested\":{\"value\":8},\"items\":[1,2]}")));
    }

    @Test
    void nbtEqualityUsesPayloadStructureRatherThanIdentity() {
        var firstPayload = nbtPayload("same");
        var secondPayload = nbtPayload("same");
        assertNotSame(firstPayload, secondPayload);

        var first = nbtEnvelope(firstPayload);
        var second = nbtEnvelope(secondPayload);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, nbtEnvelope(nbtPayload("different")));
    }

    @Test
    void constructorAndAccessorMutationDoNotChangeEqualityOrHashCode() {
        var source = json("{\"root\":\"same\",\"nested\":{\"value\":1}}");
        var envelope = jsonEnvelope(source);
        var equalPeer = jsonEnvelope(json("{\"root\":\"same\",\"nested\":{\"value\":1}}"));
        var originalHashCode = envelope.hashCode();

        source.addProperty("root", "constructor-source-mutated");
        source.getAsJsonObject("nested").addProperty("value", 2);
        assertEquals(equalPeer, envelope);
        assertEquals(originalHashCode, envelope.hashCode());

        var accessorCopy = (JsonObject) envelope.copyRawPayload().getValue();
        accessorCopy.addProperty("root", "accessor-copy-mutated");
        accessorCopy.getAsJsonObject("nested").addProperty("value", 3);
        assertEquals(equalPeer, envelope);
        assertEquals(originalHashCode, envelope.hashCode());
    }

    @Test
    void equivalentRegistryOpsJsonWrappersDoNotAffectEnvelopeEquality() {
        var firstOps = RegistryOps.create(JsonOps.INSTANCE, emptyProvider());
        var secondOps = RegistryOps.create(JsonOps.INSTANCE, emptyProvider());
        assertNotSame(firstOps, secondOps);
        assertNotEquals(firstOps, secondOps);

        var first = new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(firstOps, json("{\"value\":1}")));
        var second = new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(secondOps, json("{\"value\":1}")));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equivalentRegistryOpsNbtWrappersDoNotAffectEnvelopeEquality() {
        var firstOps = RegistryOps.create(NbtOps.INSTANCE, emptyProvider());
        var secondOps = RegistryOps.create(NbtOps.INSTANCE, emptyProvider());
        assertNotSame(firstOps, secondOps);
        assertNotEquals(firstOps, secondOps);

        var first = new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(firstOps, nbtPayload("same")));
        var second = new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(secondOps, nbtPayload("same")));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void toStringIsBoundedMetadataOnlyAndNeverContainsRawPayload() {
        var payload = new JsonObject();
        var current = payload;
        for (var index = 0; index < 40; index++) {
            current.addProperty("level_" + index, "large-value-" + index + "-" + "x".repeat(100));
            var nested = new JsonObject();
            current.add("nested_" + index, nested);
            current = nested;
        }
        current.addProperty("secret", SECRET);

        var jsonText = jsonEnvelope(payload).toString();
        assertFalse(jsonText.contains(SECRET));
        assertFalse(jsonText.contains("level_0"));
        assertFalse(jsonText.contains("nested_0"));
        assertTrue(jsonText.contains(TYPE_ID.toString()));
        assertTrue(jsonText.contains("schemaVersion=4"));
        assertTrue(jsonText.contains("payloadFamily=json"));
        assertTrue(jsonText.length() <= DefinitionEnvelope.MAX_TO_STRING_LENGTH);

        var nbtText = nbtEnvelope(nbtPayload(SECRET)).toString();
        assertFalse(nbtText.contains(SECRET));
        assertTrue(nbtText.contains("payloadFamily=nbt"));
        assertTrue(nbtText.length() <= DefinitionEnvelope.MAX_TO_STRING_LENGTH);

        var longTypeId = ResourceLocation.fromNamespaceAndPath("example", "a".repeat(1000));
        var longTypeText = new DefinitionEnvelope(
                longTypeId,
                Integer.MAX_VALUE,
                new Dynamic<>(JsonOps.INSTANCE, payload)).toString();
        assertTrue(longTypeText.startsWith("DefinitionEnvelope[typeId=example:"));
        assertTrue(longTypeText.contains("schemaVersion=" + Integer.MAX_VALUE));
        assertTrue(longTypeText.contains("payloadFamily=json"));
        assertTrue(longTypeText.length() <= DefinitionEnvelope.MAX_TO_STRING_LENGTH);
    }

    @Test
    void unsupportedRepresentationIsContainedByCodecWithoutPartialResultOrPayloadLeak() {
        var unsupportedPayload = JavaOps.INSTANCE.createMap(Stream.of(
                Pair.of(JavaOps.INSTANCE.createString("secret"), JavaOps.INSTANCE.createString(SECRET)),
                Pair.of(JavaOps.INSTANCE.createString("large"), JavaOps.INSTANCE.createString("x".repeat(6000)))));
        var unsupportedDynamic = new Dynamic<>(JavaOps.INSTANCE, unsupportedPayload);

        var constructorError = assertThrows(
                IllegalArgumentException.class,
                () -> new DefinitionEnvelope(TYPE_ID, 0, unsupportedDynamic));
        assertTrue(constructorError.getMessage().contains("Unsupported raw payload representation"));
        assertFalse(constructorError.getMessage().contains(SECRET));

        var encodedEnvelope = JavaOps.INSTANCE.createMap(Stream.of(
                Pair.of(JavaOps.INSTANCE.createString("type"), JavaOps.INSTANCE.createString(TYPE_ID.toString())),
                Pair.of(JavaOps.INSTANCE.createString("schema_version"), JavaOps.INSTANCE.createInt(0)),
                Pair.of(JavaOps.INSTANCE.createString("payload"), unsupportedPayload)));

        var parseResult = assertDoesNotThrow(() -> DefinitionEnvelope.CODEC.parse(JavaOps.INSTANCE, encodedEnvelope));
        var decodeResult = assertDoesNotThrow(() -> DefinitionEnvelope.CODEC.decode(JavaOps.INSTANCE, encodedEnvelope));
        assertContainedError(parseResult);
        assertContainedError(decodeResult);
    }

    private static void assertContainedError(DataResult<?> result) {
        assertTrue(result.error().isPresent());
        assertTrue(result.result().isEmpty());
        var error = result.error().orElseThrow();
        assertTrue(error.partialValue().isEmpty());
        assertTrue(error.message().length() <= MagicSafetyCeilings.MAX_STRING_LENGTH);
        assertFalse(error.message().contains(SECRET));
        assertFalse(error.message().contains("x".repeat(100)));
    }

    private static DefinitionEnvelope jsonEnvelope(JsonObject payload) {
        return new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static DefinitionEnvelope nbtEnvelope(CompoundTag payload) {
        return new DefinitionEnvelope(TYPE_ID, 4, new Dynamic<>(NbtOps.INSTANCE, payload));
    }

    private static JsonObject json(String input) {
        return JsonParser.parseString(input).getAsJsonObject();
    }

    private static CompoundTag nbtPayload(String value) {
        var nested = new CompoundTag();
        nested.putString("value", value);
        var payload = new CompoundTag();
        payload.putString("root", "same");
        payload.put("nested", nested);
        return payload;
    }

    private static HolderLookup.Provider emptyProvider() {
        return HolderLookup.Provider.create(Stream.empty());
    }
}
