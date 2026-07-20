package com.yo1no.gramarye.magic.definition.envelope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.math.BigDecimal;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefinitionEnvelopeSnapshotTest {
    private static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("example", "snapshot");
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void constructorSnapshotAndAccessorTreesHaveDistinctIdentity() {
        var sourceValue = complexJsonPayload();
        var source = new Dynamic<>(JsonOps.INSTANCE, sourceValue);
        var envelope = new DefinitionEnvelope(TYPE_ID, 2, source);

        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(source));

        var first = envelope.copyRawPayload();
        var second = envelope.copyRawPayload();
        assertNotSame(sourceValue, first.getValue());
        assertNotSame(first.getValue(), second.getValue());
        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(first));
        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(second));
        assertEquals(sourceValue, first.getValue());
        assertEquals(sourceValue, second.getValue());
    }

    @Test
    void modifyingFirstJsonAccessorIncludingNestedArrayDoesNotAffectSecondAccessor() {
        var original = complexJsonPayload();
        var envelope = new DefinitionEnvelope(TYPE_ID, 2, new Dynamic<>(JsonOps.INSTANCE, original));
        var first = (JsonObject) envelope.copyRawPayload().getValue();

        first.addProperty("root", "mutated");
        first.getAsJsonObject("nested").addProperty("value", "nested-mutated");
        first.getAsJsonArray("items").add("array-mutated");
        first.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("inside", "item-mutated");
        assertEquals("mutated", first.get("root").getAsString());
        assertEquals(2, first.getAsJsonArray("items").size());

        var second = (JsonObject) envelope.copyRawPayload().getValue();
        assertEquals(original, second);
        assertEquals(1, second.getAsJsonArray("items").size());
        assertEquals("original-item", second.getAsJsonArray("items").get(0)
                .getAsJsonObject().get("inside").getAsString());
    }

    @Test
    void modifyingFirstNbtAccessorIncludingNestedListAndArraysDoesNotAffectSecondAccessor() {
        var original = complexNbtPayload();
        var source = new Dynamic<>(NbtOps.INSTANCE, original);
        var envelope = new DefinitionEnvelope(TYPE_ID, 2, source);
        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(source));
        var firstDynamic = envelope.copyRawPayload();
        var first = (CompoundTag) firstDynamic.getValue();
        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(firstDynamic));

        first.putString("root", "mutated");
        first.getCompound("nested").putString("value", "nested-mutated");
        var firstList = (ListTag) first.get("items");
        firstList.add(compoundWithValue("added"));
        ((CompoundTag) firstList.get(0)).putString("value", "item-mutated");
        first.putByteArray("bytes", new byte[] {9});
        first.putIntArray("ints", new int[] {9});
        first.putLongArray("longs", new long[] {9L});
        assertEquals("mutated", first.getString("root"));
        assertEquals(2, firstList.size());

        var secondDynamic = envelope.copyRawPayload();
        var second = (CompoundTag) secondDynamic.getValue();
        assertNotSame(first, second);
        assertFalse(envelope.rawPayloadSnapshot().sharesValueReference(secondDynamic));
        assertEquals(original, second);
        assertEquals(1, ((ListTag) second.get("items")).size());
        assertEquals("original-item", ((CompoundTag) ((ListTag) second.get("items")).get(0)).getString("value"));
        assertEquals(new ByteArrayTag(new byte[] {1, 2}), second.get("bytes"));
        assertEquals(new IntArrayTag(new int[] {3, 4}), second.get("ints"));
        assertEquals(new LongArrayTag(new long[] {5L, 6L}), second.get("longs"));
    }

    @Test
    void jsonSameOpsRoundTripPreservesExactTreeWithoutNumericCanonicalization() {
        var payload = complexJsonPayload();
        payload.addProperty("scaled_decimal", new BigDecimal("1.00"));
        var envelope = new DefinitionEnvelope(TYPE_ID, 5, new Dynamic<>(JsonOps.INSTANCE, payload));

        var encoded = DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, envelope).getOrThrow();
        var decoded = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var reencoded = DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();

        assertEquals(encoded, reencoded);
        assertEquals(encoded.toString(), reencoded.toString());
        assertTrue(reencoded.toString().contains("1.00"));
    }

    @Test
    void nbtSameOpsRoundTripPreservesCollectionsArraysAndNumericLeafTypes() {
        var payload = complexNbtPayload();
        payload.putByte("byte", (byte) 1);
        payload.putShort("short", (short) 2);
        payload.putInt("int", 3);
        payload.putLong("long", 4L);
        payload.putFloat("float", 5.5F);
        payload.putDouble("double", 6.5D);
        var envelope = new DefinitionEnvelope(TYPE_ID, 5, new Dynamic<>(NbtOps.INSTANCE, payload));

        var encoded = DefinitionEnvelope.CODEC.encodeStart(NbtOps.INSTANCE, envelope).getOrThrow();
        var decoded = DefinitionEnvelope.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        var reencoded = DefinitionEnvelope.CODEC.encodeStart(NbtOps.INSTANCE, decoded).getOrThrow();

        assertEquals(encoded, reencoded);
        var roundTrippedPayload = ((CompoundTag) reencoded).getCompound("payload");
        assertInstanceOf(ByteTag.class, roundTrippedPayload.get("byte"));
        assertInstanceOf(ShortTag.class, roundTrippedPayload.get("short"));
        assertInstanceOf(IntTag.class, roundTrippedPayload.get("int"));
        assertInstanceOf(LongTag.class, roundTrippedPayload.get("long"));
        assertInstanceOf(FloatTag.class, roundTrippedPayload.get("float"));
        assertInstanceOf(DoubleTag.class, roundTrippedPayload.get("double"));
        assertInstanceOf(ByteArrayTag.class, roundTrippedPayload.get("bytes"));
        assertInstanceOf(IntArrayTag.class, roundTrippedPayload.get("ints"));
        assertInstanceOf(LongArrayTag.class, roundTrippedPayload.get("longs"));
    }

    @Test
    void modifyingJsonCodecInputAfterDecodeDoesNotAffectEnvelope() {
        var input = JsonParser.parseString("""
                {
                  "type": "example:snapshot",
                  "schema_version": 8,
                  "payload": {
                    "root": "original",
                    "nested": {"value": "original-nested"}
                  }
                }
                """).getAsJsonObject();
        var before = input.deepCopy();
        var decoded = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, input).getOrThrow();

        input.getAsJsonObject("payload").addProperty("root", "mutated");
        input.getAsJsonObject("payload").getAsJsonObject("nested").addProperty("value", "nested-mutated");
        assertEquals("mutated", input.getAsJsonObject("payload").get("root").getAsString());

        var after = DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
        assertEquals(before, after);
    }

    @Test
    void registryOpsWrappedJsonAndNbtValuesRetainContextAndMutationIsolation() {
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var jsonSource = complexJsonPayload();
        var jsonEnvelope = new DefinitionEnvelope(TYPE_ID, 1, new Dynamic<>(jsonOps, jsonSource));
        jsonSource.addProperty("root", "source-mutated");
        var firstJson = (JsonObject) jsonEnvelope.copyRawPayload().getValue();
        firstJson.getAsJsonObject("nested").addProperty("value", "accessor-mutated");
        var secondJson = jsonEnvelope.copyRawPayload();
        assertInstanceOf(RegistryOps.class, secondJson.getOps());
        assertEquals(jsonOps, secondJson.getOps());
        assertEquals("original-root", ((JsonObject) secondJson.getValue()).get("root").getAsString());
        assertEquals("original-nested", ((JsonObject) secondJson.getValue())
                .getAsJsonObject("nested").get("value").getAsString());

        var nbtOps = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var nbtSource = complexNbtPayload();
        var nbtEnvelope = new DefinitionEnvelope(TYPE_ID, 1, new Dynamic<>(nbtOps, nbtSource));
        nbtSource.putString("root", "source-mutated");
        var firstNbt = (CompoundTag) nbtEnvelope.copyRawPayload().getValue();
        firstNbt.getCompound("nested").putString("value", "accessor-mutated");
        var secondNbt = nbtEnvelope.copyRawPayload();
        assertInstanceOf(RegistryOps.class, secondNbt.getOps());
        assertEquals(nbtOps, secondNbt.getOps());
        assertEquals("original-root", ((CompoundTag) secondNbt.getValue()).getString("root"));
        assertEquals("original-nested", ((CompoundTag) secondNbt.getValue())
                .getCompound("nested").getString("value"));
    }

    @Test
    void registryOpsCodecDecodeSnapshotsWrappedJsonAndNbtInputs() {
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var jsonInput = envelopeJson(complexJsonPayload());
        var jsonBefore = jsonInput.deepCopy();
        var jsonEnvelope = DefinitionEnvelope.CODEC.parse(jsonOps, jsonInput).getOrThrow();
        jsonInput.getAsJsonObject("payload").addProperty("root", "mutated");
        var jsonAfter = DefinitionEnvelope.CODEC.encodeStart(jsonOps, jsonEnvelope).getOrThrow();
        assertEquals(jsonBefore, jsonAfter);

        var nbtOps = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var nbtInput = envelopeNbt(complexNbtPayload());
        var nbtBefore = nbtInput.copy();
        var nbtEnvelope = DefinitionEnvelope.CODEC.parse(nbtOps, nbtInput).getOrThrow();
        nbtInput.getCompound("payload").putString("root", "mutated");
        var nbtAfter = DefinitionEnvelope.CODEC.encodeStart(nbtOps, nbtEnvelope).getOrThrow();
        assertEquals(nbtBefore, nbtAfter);
    }

    @Test
    void unsupportedRawRepresentationFailsAtConstructionAndCodecDecodeBoundary() {
        var unsupportedPayload = JavaOps.INSTANCE.createMap(Stream.of(
                Pair.of(JavaOps.INSTANCE.createString("value"), JavaOps.INSTANCE.createString("unsupported"))));
        var unsupportedDynamic = new Dynamic<>(JavaOps.INSTANCE, unsupportedPayload);
        assertThrows(
                IllegalArgumentException.class,
                () -> new DefinitionEnvelope(TYPE_ID, 0, unsupportedDynamic));

        var encodedEnvelope = JavaOps.INSTANCE.createMap(Stream.of(
                Pair.of(JavaOps.INSTANCE.createString("type"), JavaOps.INSTANCE.createString("example:snapshot")),
                Pair.of(JavaOps.INSTANCE.createString("schema_version"), JavaOps.INSTANCE.createInt(0)),
                Pair.of(JavaOps.INSTANCE.createString("payload"), unsupportedPayload)));
        var result = DefinitionEnvelope.CODEC.parse(JavaOps.INSTANCE, encodedEnvelope);

        assertTrue(result.error().isPresent());
        var diagnostic = result.error().orElseThrow().message();
        assertTrue(diagnostic.contains("Unsupported raw payload representation"));
        assertTrue(diagnostic.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH);
    }

    private static JsonObject complexJsonPayload() {
        return JsonParser.parseString("""
                {
                  "root": "original-root",
                  "nested": {"value": "original-nested"},
                  "items": [{"inside": "original-item"}],
                  "numbers": [1, 2.5, 9007199254740991]
                }
                """).getAsJsonObject();
    }

    private static CompoundTag complexNbtPayload() {
        var payload = new CompoundTag();
        payload.putString("root", "original-root");
        payload.put("nested", compoundWithValue("original-nested"));
        var items = new ListTag();
        items.add(compoundWithValue("original-item"));
        payload.put("items", items);
        payload.putByteArray("bytes", new byte[] {1, 2});
        payload.putIntArray("ints", new int[] {3, 4});
        payload.putLongArray("longs", new long[] {5L, 6L});
        return payload;
    }

    private static CompoundTag compoundWithValue(String value) {
        var compound = new CompoundTag();
        compound.putString("value", value);
        return compound;
    }

    private static JsonObject envelopeJson(JsonElement payload) {
        var envelope = new JsonObject();
        envelope.addProperty("type", "example:snapshot");
        envelope.addProperty("schema_version", 1);
        envelope.add("payload", payload);
        return envelope;
    }

    private static CompoundTag envelopeNbt(CompoundTag payload) {
        var envelope = new CompoundTag();
        envelope.putString("type", "example:snapshot");
        envelope.putInt("schema_version", 1);
        envelope.put("payload", payload);
        return envelope;
    }
}
