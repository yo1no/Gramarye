package com.yo1no.gramarye.magic.definition.tree;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class SupportedDynamicTreesTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void directContextsDistinguishJsonCompressionAndNbt() {
        assertAll(
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, false),
                        SupportedDynamicTrees.contextOf(JsonOps.INSTANCE).getOrThrow()),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, false, true),
                        SupportedDynamicTrees.contextOf(JsonOps.COMPRESSED).getOrThrow()),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.NBT, false, false),
                        SupportedDynamicTrees.contextOf(NbtOps.INSTANCE).getOrThrow()));
    }

    @Test
    void registryContextsRetainParentFamilyAndJsonCompression() {
        var json = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var compressedJson = RegistryOps.create(JsonOps.COMPRESSED, EMPTY_PROVIDER);
        var nbt = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);

        assertAll(
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, false),
                        SupportedDynamicTrees.contextOf(json).getOrThrow()),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                        SupportedDynamicTrees.contextOf(compressedJson).getOrThrow()),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.NBT, true, false),
                        SupportedDynamicTrees.contextOf(nbt).getOrThrow()));
    }

    @Test
    void dynamicInspectionRejectsMismatchedOrUnsupportedContexts() {
        var unsupported = new Dynamic<>(JavaOps.INSTANCE, Map.of("value", 1));

        assertAll(
                () -> assertTrue(SupportedDynamicTrees.contextOf(JavaOps.INSTANCE).error().isPresent()),
                () -> assertTrue(SupportedDynamicTrees.contextOf(unsupported).error().isPresent()),
                () -> assertTrue(SupportedDynamicTrees.defensiveCopy(unsupported).error().isPresent()));
    }

    @Test
    void jsonDefensiveCopyIsDeepAndRetainsExactOpsInstance() {
        var nested = new JsonObject();
        nested.addProperty("value", "original");
        var items = new JsonArray();
        items.add(nested);
        var source = new JsonObject();
        source.add("items", items);
        var ops = RegistryOps.create(JsonOps.COMPRESSED, EMPTY_PROVIDER);
        var copiedDynamic = SupportedDynamicTrees.defensiveCopy(new Dynamic<>(ops, source)).getOrThrow();
        var copied = assertInstanceOf(JsonObject.class, copiedDynamic.getValue());

        source.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("value", "source-mutated");
        assertEquals("original", copied.getAsJsonArray("items").get(0)
                .getAsJsonObject().get("value").getAsString());
        copied.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("copy", true);

        assertAll(
                () -> assertSame(ops, copiedDynamic.getOps()),
                () -> assertNotSame(source, copied),
                () -> assertTrue(!source.getAsJsonArray("items").get(0).getAsJsonObject().has("copy")),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.JSON, true, true),
                        SupportedDynamicTrees.contextOf(copiedDynamic).getOrThrow()));
    }

    @Test
    void nbtDefensiveCopyIsDeepIncludingCollectionsAndPrimitiveArrays() {
        var nested = new CompoundTag();
        nested.putString("value", "original");
        var list = new ListTag();
        list.add(nested);
        var source = new CompoundTag();
        source.put("items", list);
        source.putByteArray("bytes", new byte[] {1, 2});
        var ops = RegistryOps.create(NbtOps.INSTANCE, EMPTY_PROVIDER);
        var copiedDynamic = SupportedDynamicTrees.defensiveCopy(new Dynamic<>(ops, source)).getOrThrow();
        var copied = assertInstanceOf(CompoundTag.class, copiedDynamic.getValue());

        ((CompoundTag) ((ListTag) source.get("items")).get(0)).putString("value", "source-mutated");
        source.putByteArray("bytes", new byte[] {9});
        ((CompoundTag) ((ListTag) copied.get("items")).get(0)).putString("copy", "mutated");

        assertAll(
                () -> assertSame(ops, copiedDynamic.getOps()),
                () -> assertNotSame(source, copied),
                () -> assertEquals("original",
                        ((CompoundTag) ((ListTag) copied.get("items")).get(0)).getString("value")),
                () -> assertEquals(new ByteArrayTag(new byte[] {1, 2}), copied.get("bytes")),
                () -> assertTrue(!((CompoundTag) ((ListTag) source.get("items")).get(0)).contains("copy")),
                () -> assertEquals(
                        new SerializedTreeContext(SerializedTreeFamily.NBT, true, false),
                        SupportedDynamicTrees.contextOf(copiedDynamic).getOrThrow()));
    }

    @Test
    void contextRejectsImpossibleNbtCompressionAndNullFamily() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new SerializedTreeContext(null, false, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SerializedTreeContext(SerializedTreeFamily.NBT, false, true)));
    }
}
