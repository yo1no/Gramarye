package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class RawTreeEnvelopeTest {
    private static final HolderLookup.Provider PROVIDER_ONE =
            HolderLookup.Provider.create(Stream.empty());
    private static final HolderLookup.Provider PROVIDER_TWO =
            HolderLookup.Provider.create(Stream.empty());
    private static final SkillDocumentPersistenceLocation LOCATION =
            new SkillDocumentPersistenceLocation.TriggerPayload(0);

    @Test
    void capturesAndRebindsAllSixSupportedContexts() {
        var json = jsonTree();
        var nbt = nbtTree();

        assertRoundTrip(
                new Dynamic<>(JsonOps.INSTANCE, json),
                new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                Optional.empty());
        assertRoundTrip(
                new Dynamic<>(JsonOps.COMPRESSED, json),
                new SerializedTreeContext(SerializedTreeFamily.JSON, false, true),
                Optional.empty());
        assertRoundTrip(
                new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, PROVIDER_ONE), json),
                new SerializedTreeContext(SerializedTreeFamily.JSON, true, false),
                Optional.of(PROVIDER_TWO));
        assertRoundTrip(
                new Dynamic<>(RegistryOps.create(JsonOps.COMPRESSED, PROVIDER_ONE), json),
                new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                Optional.of(PROVIDER_TWO));
        assertRoundTrip(
                new Dynamic<>(NbtOps.INSTANCE, nbt),
                new SerializedTreeContext(SerializedTreeFamily.NBT, false, false),
                Optional.empty());
        assertRoundTrip(
                new Dynamic<>(RegistryOps.create(NbtOps.INSTANCE, PROVIDER_ONE), nbt),
                new SerializedTreeContext(SerializedTreeFamily.NBT, true, false),
                Optional.of(PROVIDER_TWO));
    }

    @Test
    void registryProviderIsRequiredOnlyWhenTheCapturedContextRequiresIt() {
        var registryJson = new Dynamic<>(
                RegistryOps.create(JsonOps.INSTANCE, PROVIDER_ONE),
                jsonTree());
        var registryEnvelope = success(RawTreeEnvelope.capture(registryJson, LOCATION));
        var missingProvider = failure(registryEnvelope.hydrate(Optional.empty(), LOCATION));

        assertInstanceOf(
                SkillDocumentPersistenceFailure.RegistryContextUnavailable.class,
                missingProvider);

        var plainEnvelope = success(RawTreeEnvelope.capture(
                new Dynamic<>(JsonOps.INSTANCE, jsonTree()), LOCATION));
        assertTrue(plainEnvelope.hydrate(Optional.empty(), LOCATION).successValue().isPresent());
    }

    @Test
    void registryProviderIdentityDoesNotParticipateInEnvelopeEquality() {
        var first = success(RawTreeEnvelope.capture(
                new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, PROVIDER_ONE), jsonTree()),
                LOCATION));
        var second = success(RawTreeEnvelope.capture(
                new Dynamic<>(RegistryOps.create(JsonOps.INSTANCE, PROVIDER_TWO), jsonTree()),
                LOCATION));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());

        var compressed = success(RawTreeEnvelope.capture(
                new Dynamic<>(RegistryOps.create(JsonOps.COMPRESSED, PROVIDER_ONE), jsonTree()),
                LOCATION));
        assertNotEquals(first, compressed);
    }

    @Test
    void captureAndByteAccessAreDeeplyIsolated() {
        var source = jsonTree();
        var envelope = success(RawTreeEnvelope.capture(
                new Dynamic<>(JsonOps.INSTANCE, source), LOCATION));
        var before = envelope.copyData();

        source.addProperty("later", "source mutation");
        source.getAsJsonObject("nested").addProperty("later", "nested mutation");
        var exposed = envelope.copyData();
        exposed[0] ^= 0x7f;

        assertArrayEquals(before, envelope.copyData());
        assertNotSame(exposed, envelope.copyData());

        var physical = envelope.encodePhysical();
        var decoded = success(RawTreeEnvelope.decodePhysical(physical, LOCATION));
        var physicalBytes = physical.getByteArray("data");
        physicalBytes[0] ^= 0x7f;
        physical.putByteArray("data", physicalBytes);
        assertArrayEquals(before, decoded.copyData());
    }

    @Test
    void physicalDecodeRejectsInvalidFamilyFlagsShapeAndSize() {
        var unknownFamily = physical("future", false, false, new byte[] {'0'});
        assertInstanceOf(
                SkillDocumentPersistenceFailure.UnsupportedRawFamily.class,
                failure(RawTreeEnvelope.decodePhysical(unknownFamily, LOCATION)));

        var compressedNbt = physical("nbt", false, true, new byte[] {0});
        assertInstanceOf(
                SkillDocumentPersistenceFailure.InvalidRawContext.class,
                failure(RawTreeEnvelope.decodePhysical(compressedNbt, LOCATION)));

        var empty = physical("json", false, false, new byte[0]);
        assertInstanceOf(
                SkillDocumentPersistenceFailure.InvalidRawContext.class,
                failure(RawTreeEnvelope.decodePhysical(empty, LOCATION)));

        var missing = physical("json", false, false, new byte[] {'0'});
        missing.remove("compressed_maps");
        assertInstanceOf(
                SkillDocumentPersistenceFailure.InvalidRawContext.class,
                failure(RawTreeEnvelope.decodePhysical(missing, LOCATION)));

        var atLimit = physical(
                "json",
                false,
                false,
                new byte[MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES]);
        assertTrue(RawTreeEnvelope.decodePhysical(atLimit, LOCATION).successValue().isPresent());

        var overLimit = physical(
                "json",
                false,
                false,
                new byte[MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES + 1]);
        var capacity = assertInstanceOf(
                SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded.class,
                failure(RawTreeEnvelope.decodePhysical(overLimit, LOCATION)));
        assertEquals(MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES + 1L, capacity.observedAtLeast());
        assertEquals(MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES, capacity.maximum());
    }

    @Test
    void malformedStoredTreesProduceFamilySpecificTypedFailures() {
        var malformedJson = success(RawTreeEnvelope.decodePhysical(
                physical("json", false, false, "{".getBytes(StandardCharsets.UTF_8)),
                LOCATION));
        assertInstanceOf(
                SkillDocumentPersistenceFailure.MalformedJsonRaw.class,
                failure(malformedJson.hydrate(Optional.empty(), LOCATION)));

        var malformedNbt = success(RawTreeEnvelope.decodePhysical(
                physical("nbt", false, false, new byte[] {(byte) 127}),
                LOCATION));
        assertInstanceOf(
                SkillDocumentPersistenceFailure.MalformedNbtRaw.class,
                failure(malformedNbt.hydrate(Optional.empty(), LOCATION)));
    }

    @Test
    void captureEnforcesLogicalDepthAndTreeNodeCeilings() {
        var exactNodeTree = new JsonArray();
        for (var index = 1; index < MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES; index++) {
            exactNodeTree.add(0);
        }
        assertTrue(RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, exactNodeTree), LOCATION)
                .successValue()
                .isPresent());

        exactNodeTree.add(0);
        var nodeFailure = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded.class,
                failure(RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, exactNodeTree), LOCATION)));
        assertEquals(
                SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT,
                nodeFailure.kind());

        var exactDepthTree = nestedJsonArrays(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH);
        assertTrue(RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, exactDepthTree), LOCATION)
                .successValue()
                .isPresent());

        var depthFailure = assertInstanceOf(
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded.class,
                failure(RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, nestedJsonArrays(
                                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH + 1)),
                        LOCATION)));
        assertEquals(
                SkillDocumentPersistenceFailure.DocumentBoundKind.DEPTH,
                depthFailure.kind());
    }

    @Test
    void metadataOnlyStringAndFieldsCannotLeakRawTreesOrContextObjects() {
        var secret = "unique-secret-that-must-not-be-logged";
        var source = new JsonPrimitive(secret);
        var envelope = success(RawTreeEnvelope.capture(
                new Dynamic<>(JsonOps.INSTANCE, source), LOCATION));

        var rendered = envelope.toString();
        assertFalse(rendered.contains(secret));
        assertTrue(rendered.length() < 160);
        assertTrue(rendered.contains("json"));
        assertTrue(rendered.contains("byteCount"));

        for (var field : RawTreeEnvelope.class.getDeclaredFields()) {
            assertNotEquals(byte[].class, field.getType());
            assertFalse(Dynamic.class.isAssignableFrom(field.getType()));
            assertFalse(JsonObject.class.isAssignableFrom(field.getType()));
            assertFalse(Tag.class.isAssignableFrom(field.getType()));
            assertFalse(field.getType().getName().contains("DynamicOps"));
            assertFalse(field.getType().getName().contains("HolderLookup"));
        }
    }

    @Test
    void runtimeFailureStoresOnlyItsClassAndErrorsStillPropagate() {
        var malformedNumber = new JsonPrimitive(new LazilyParsedNumber("secret-invalid-number"));
        var runtimeFailure = assertInstanceOf(
                SkillDocumentPersistenceFailure.InternalCodecException.class,
                failure(RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, malformedNumber), LOCATION)));

        assertEquals(IllegalArgumentException.class.getName(), runtimeFailure.exceptionClassName());
        assertFalse(runtimeFailure.toString().contains("secret-invalid-number"));

        var fatalNumber = new JsonPrimitive(new ThrowingNumber());
        assertThrows(
                AssertionError.class,
                () -> RawTreeEnvelope.capture(
                        new Dynamic<>(JsonOps.INSTANCE, fatalNumber), LOCATION));
    }

    private static void assertRoundTrip(
            Dynamic<?> source,
            SerializedTreeContext expectedContext,
            Optional<HolderLookup.Provider> provider) {
        var envelope = success(RawTreeEnvelope.capture(source, LOCATION));
        var hydrated = success(envelope.hydrate(provider, LOCATION));

        assertEquals(expectedContext, envelope.context());
        assertEquals(expectedContext, SupportedDynamicTrees.contextOf(hydrated).result().orElseThrow());
        assertEquals(source.getValue(), hydrated.getValue());
        assertNotSame(source.getValue(), hydrated.getValue());

        var physicalCopy = envelope.encodePhysical();
        var decoded = success(RawTreeEnvelope.decodePhysical(physicalCopy, LOCATION));
        assertEquals(envelope, decoded);
        assertEquals(envelope.hashCode(), decoded.hashCode());
    }

    private static JsonObject jsonTree() {
        var array = new JsonArray();
        array.add("first");
        array.add("second");
        var nested = new JsonObject();
        nested.addProperty("number", 1);
        nested.add("array", array);
        var root = new JsonObject();
        root.add("nested", nested);
        return root;
    }

    private static CompoundTag nbtTree() {
        var list = new ListTag();
        list.add(StringTag.valueOf("first"));
        list.add(StringTag.valueOf("second"));
        var nested = new CompoundTag();
        nested.put("number", IntTag.valueOf(1));
        nested.put("list", list);
        var root = new CompoundTag();
        root.put("nested", nested);
        return root;
    }

    private static JsonArray nestedJsonArrays(int depth) {
        var root = new JsonArray();
        var current = root;
        for (var index = 1; index < depth; index++) {
            var child = new JsonArray();
            current.add(child);
            current = child;
        }
        return root;
    }

    private static CompoundTag physical(
            String family,
            boolean registryContext,
            boolean compressedMaps,
            byte[] data) {
        var result = new CompoundTag();
        result.putString("family", family);
        result.putByte("registry_context", (byte) (registryContext ? 1 : 0));
        result.putByte("compressed_maps", (byte) (compressedMaps ? 1 : 0));
        result.put("data", new ByteArrayTag(data));
        return result;
    }

    private static <T> T success(SkillDocumentPersistenceResult<T> result) {
        return result.successValue().orElseThrow();
    }

    private static SkillDocumentPersistenceFailure failure(
            SkillDocumentPersistenceResult<?> result) {
        return result.failureValue().orElseThrow();
    }

    private static final class ThrowingNumber extends Number {
        @Override
        public int intValue() {
            return 0;
        }

        @Override
        public long longValue() {
            return 0;
        }

        @Override
        public float floatValue() {
            return 0;
        }

        @Override
        public double doubleValue() {
            return 0;
        }

        @Override
        public String toString() {
            throw new AssertionError("fatal-secret");
        }
    }
}
