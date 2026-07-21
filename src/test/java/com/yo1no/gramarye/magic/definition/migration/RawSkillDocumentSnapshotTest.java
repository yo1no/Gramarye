package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class RawSkillDocumentSnapshotTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void jsonSourceAndEveryAccessorCopyAreDeeplyIsolated() {
        var source = complexJson();
        var expected = source.deepCopy();
        var snapshot = RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, source));

        source.addProperty("root", "source-mutated");
        source.getAsJsonObject("nested").addProperty("value", "source-nested-mutated");
        source.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("value", "source-item-mutated");
        var first = (JsonObject) snapshot.copyRawDocument().getValue();
        first.addProperty("root", "accessor-mutated");
        first.getAsJsonObject("nested").addProperty("value", "accessor-nested-mutated");
        first.getAsJsonArray("items").add("accessor-added");
        var second = (JsonObject) snapshot.copyRawDocument().getValue();

        assertNotSame(source, first);
        assertNotSame(first, second);
        assertEquals(expected, second);
        assertEquals("source-mutated", source.get("root").getAsString());
        assertEquals("accessor-mutated", first.get("root").getAsString());
    }

    @Test
    void nbtSourceAndEveryAccessorCopyAreDeeplyIsolatedIncludingArrays() {
        var source = complexNbt();
        var expected = source.copy();
        var snapshot = RawSkillDocumentSnapshot.of(new Dynamic<>(NbtOps.INSTANCE, source));

        source.putString("root", "source-mutated");
        source.getCompound("nested").putString("value", "source-nested-mutated");
        ((CompoundTag) ((ListTag) source.get("items")).get(0)).putString("value", "source-item-mutated");
        source.putByteArray("bytes", new byte[] {9});
        var first = (CompoundTag) snapshot.copyRawDocument().getValue();
        first.putString("root", "accessor-mutated");
        first.getCompound("nested").putString("value", "accessor-nested-mutated");
        ((ListTag) first.get("items")).add(compound("accessor-added"));
        first.putIntArray("ints", new int[] {9});
        var second = (CompoundTag) snapshot.copyRawDocument().getValue();

        assertNotSame(source, first);
        assertNotSame(first, second);
        assertEquals(expected, second);
        assertEquals(new ByteArrayTag(new byte[] {1, 2}), second.get("bytes"));
        assertEquals(new IntArrayTag(new int[] {3, 4}), second.get("ints"));
        assertEquals(new LongArrayTag(new long[] {5L, 6L}), second.get("longs"));
    }

    @Test
    void registryOpsWrappersArePreservedWithoutParticipatingInStructuralEquality() {
        var firstJsonOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var secondJsonOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var jsonSource = complexJson();
        var firstJson = RawSkillDocumentSnapshot.of(new Dynamic<>(firstJsonOps, jsonSource));
        var secondJson = RawSkillDocumentSnapshot.of(new Dynamic<>(secondJsonOps, complexJson()));
        var firstNbtOps = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var secondNbtOps = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var nbtSource = complexNbt();
        var firstNbt = RawSkillDocumentSnapshot.of(new Dynamic<>(firstNbtOps, nbtSource));
        var secondNbt = RawSkillDocumentSnapshot.of(new Dynamic<>(secondNbtOps, complexNbt()));

        jsonSource.getAsJsonObject("nested").addProperty("value", "mutated");
        var jsonAccessor = (JsonObject) firstJson.copyRawDocument().getValue();
        jsonAccessor.getAsJsonArray("items").add("mutated");
        nbtSource.getCompound("nested").putString("value", "mutated");
        var nbtAccessor = (CompoundTag) firstNbt.copyRawDocument().getValue();
        ((ListTag) nbtAccessor.get("items")).add(compound("mutated"));

        assertInstanceOf(RegistryOps.class, firstJson.copyRawDocument().getOps());
        assertInstanceOf(RegistryOps.class, firstNbt.copyRawDocument().getOps());
        assertSame(firstJsonOps, firstJson.copyRawDocument().getOps());
        assertSame(firstNbtOps, firstNbt.copyRawDocument().getOps());
        assertEquals(firstJson, secondJson);
        assertEquals(firstJson.hashCode(), secondJson.hashCode());
        assertEquals(firstNbt, secondNbt);
        assertEquals(firstNbt.hashCode(), secondNbt.hashCode());
        assertEquals("nested-original", ((JsonObject) firstJson.copyRawDocument().getValue())
                .getAsJsonObject("nested").get("value").getAsString());
        assertEquals(1, ((JsonObject) firstJson.copyRawDocument().getValue())
                .getAsJsonArray("items").size());
        assertEquals("nested-original", ((CompoundTag) firstNbt.copyRawDocument().getValue())
                .getCompound("nested").getString("value"));
        assertEquals(1, ((ListTag) ((CompoundTag) firstNbt.copyRawDocument().getValue())
                .get("items")).size());
    }

    @Test
    void equalityUsesFamilyAndStructuralTreeOnly() {
        var jsonA = RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, complexJson()));
        var jsonB = RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, complexJson()));
        var changedJson = complexJson();
        changedJson.addProperty("root", "different");
        var jsonC = RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, changedJson));
        var nbt = RawSkillDocumentSnapshot.of(new Dynamic<>(NbtOps.INSTANCE, complexNbt()));

        assertEquals(jsonA, jsonB);
        assertEquals(jsonA.hashCode(), jsonB.hashCode());
        assertNotEquals(jsonA, jsonC);
        assertNotEquals(jsonA, nbt);
    }

    @Test
    void unsupportedFamilyIsTypedAtPipelineBoundaryAndThrowsForDirectMisuse() {
        var source = new Dynamic<>(JavaOps.INSTANCE, Map.of("schema_version", 0));
        var failed = assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Failure.class,
                RawSkillDocumentSnapshot.capture(source));

        assertEquals(SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY, failed.failure().code());
        assertThrows(IllegalArgumentException.class, () -> RawSkillDocumentSnapshot.of(source));
    }

    @Test
    void globalDepthAllows64AndRejects65() {
        assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Success.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(JsonOps.INSTANCE, nestedJson(64))));
        var failed = assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Failure.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(JsonOps.INSTANCE, nestedJson(65))));

        assertEquals(SkillMigrationFailure.Code.GLOBAL_DEPTH_EXCEEDED, failed.failure().code());
    }

    @Test
    void globalTreeNodesAllow65536AndReject65537() {
        var atLimit = flatArray(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES - 1);
        var overLimit = flatArray(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);

        assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Success.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(JsonOps.INSTANCE, atLimit)));
        var failed = assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Failure.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(JsonOps.INSTANCE, overLimit)));
        assertEquals(
                SkillMigrationFailure.Code.GLOBAL_TREE_NODE_LIMIT_EXCEEDED,
                failed.failure().code());
    }

    @Test
    void nbtPrimitiveArrayElementsUseTheSameNodeCountingConvention() {
        var atLimit = new ByteArrayTag(new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES - 1]);
        var overLimit = new ByteArrayTag(new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES]);

        assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Success.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(NbtOps.INSTANCE, atLimit)));
        var failed = assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Failure.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(NbtOps.INSTANCE, overLimit)));
        assertEquals(
                SkillMigrationFailure.Code.GLOBAL_TREE_NODE_LIMIT_EXCEEDED,
                failed.failure().code());
    }

    @Test
    void mapKeysRemainSubjectToTheTechnicalStringCeiling() {
        var root = new JsonObject();
        root.addProperty("x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH + 1), true);
        var failed = assertInstanceOf(
                RawSkillDocumentSnapshot.CaptureResult.Failure.class,
                RawSkillDocumentSnapshot.capture(new Dynamic<>(JsonOps.INSTANCE, root)));

        assertEquals(SkillMigrationFailure.Code.GLOBAL_KEY_LENGTH_EXCEEDED, failed.failure().code());
    }

    @Test
    void toStringExposesFamilyButNeverRawTree() {
        var source = complexJson();
        source.addProperty("secret", "unique-p3-b1-secret");
        var text = RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, source)).toString();

        assertTrue(text.contains("json"));
        assertFalse(text.contains("unique-p3-b1-secret"));
        assertFalse(text.contains("payload"));
        assertTrue(text.length() < 128);
    }

    private static JsonObject complexJson() {
        var nested = new JsonObject();
        nested.addProperty("value", "nested-original");
        var item = new JsonObject();
        item.addProperty("value", "item-original");
        var items = new JsonArray();
        items.add(item);
        var root = new JsonObject();
        root.addProperty("schema_version", 0);
        root.addProperty("root", "root-original");
        root.add("nested", nested);
        root.add("items", items);
        return root;
    }

    private static CompoundTag complexNbt() {
        var nested = new CompoundTag();
        nested.putString("value", "nested-original");
        var items = new ListTag();
        items.add(compound("item-original"));
        var root = new CompoundTag();
        root.putInt("schema_version", 0);
        root.putString("root", "root-original");
        root.put("nested", nested);
        root.put("items", items);
        root.putByteArray("bytes", new byte[] {1, 2});
        root.putIntArray("ints", new int[] {3, 4});
        root.putLongArray("longs", new long[] {5L, 6L});
        return root;
    }

    private static CompoundTag compound(String value) {
        var compound = new CompoundTag();
        compound.putString("value", value);
        return compound;
    }

    private static JsonElement nestedJson(int depth) {
        JsonElement value = JsonNull.INSTANCE;
        for (var index = 1; index < depth; index++) {
            var parent = new JsonObject();
            parent.add("next", value);
            value = parent;
        }
        return value;
    }

    private static JsonArray flatArray(int elements) {
        var array = new JsonArray(elements);
        for (var index = 0; index < elements; index++) {
            array.add(index);
        }
        return array;
    }
}
