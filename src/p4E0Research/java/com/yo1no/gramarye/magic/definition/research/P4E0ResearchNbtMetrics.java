package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import java.util.Objects;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Checked, multi-dimensional logical-tree metrics; it deliberately defines no weighted work. */
record P4E0ResearchNbtMetrics(
        long maxContainerDepth,
        long compoundCount,
        long compoundEntryCount,
        long listCount,
        long listElementCount,
        long scalarTagCount,
        long byteArrayCount,
        long byteArrayElements,
        long intArrayCount,
        long intArrayElements,
        long longArrayCount,
        long longArrayElements,
        long stringCount,
        long modifiedUtf8Bytes,
        long tagCountTotal,
        long valueElementsTotal) {

    static P4E0ResearchNbtMetrics zero() {
        return new P4E0ResearchNbtMetrics(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    static P4E0ResearchNbtMetrics measure(Tag root) {
        Objects.requireNonNull(root, "root");
        var mutable = new Mutable();
        mutable.walk(root, 0L);
        return mutable.freeze();
    }

    static long modifiedUtf8Length(String value) {
        Objects.requireNonNull(value, "value");
        var bytes = 0L;
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            bytes = add(bytes, character >= 0x0001 && character <= 0x007f
                    ? 1L : character <= 0x07ff ? 2L : 3L);
        }
        return bytes;
    }

    static long add(long left, long right) {
        return Math.addExact(left, right);
    }

    P4E0ResearchNbtMetrics plus(P4E0ResearchNbtMetrics other) {
        Objects.requireNonNull(other, "other");
        return new P4E0ResearchNbtMetrics(
                Math.max(maxContainerDepth, other.maxContainerDepth),
                add(compoundCount, other.compoundCount),
                add(compoundEntryCount, other.compoundEntryCount),
                add(listCount, other.listCount),
                add(listElementCount, other.listElementCount),
                add(scalarTagCount, other.scalarTagCount),
                add(byteArrayCount, other.byteArrayCount),
                add(byteArrayElements, other.byteArrayElements),
                add(intArrayCount, other.intArrayCount),
                add(intArrayElements, other.intArrayElements),
                add(longArrayCount, other.longArrayCount),
                add(longArrayElements, other.longArrayElements),
                add(stringCount, other.stringCount),
                add(modifiedUtf8Bytes, other.modifiedUtf8Bytes),
                add(tagCountTotal, other.tagCountTotal),
                add(valueElementsTotal, other.valueElementsTotal));
    }

    JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("max_container_depth", maxContainerDepth);
        json.addProperty("compound_count", compoundCount);
        json.addProperty("compound_entry_count", compoundEntryCount);
        json.addProperty("list_count", listCount);
        json.addProperty("list_element_count", listElementCount);
        json.addProperty("scalar_tag_count", scalarTagCount);
        json.addProperty("byte_array_count", byteArrayCount);
        json.addProperty("byte_array_elements", byteArrayElements);
        json.addProperty("int_array_count", intArrayCount);
        json.addProperty("int_array_elements", intArrayElements);
        json.addProperty("long_array_count", longArrayCount);
        json.addProperty("long_array_elements", longArrayElements);
        json.addProperty("string_count", stringCount);
        json.addProperty("modified_utf8_bytes", modifiedUtf8Bytes);
        json.addProperty("tag_count_total", tagCountTotal);
        json.addProperty("value_elements_total", valueElementsTotal);
        return json;
    }

    private static final class Mutable {
        private long maxContainerDepth;
        private long compoundCount;
        private long compoundEntryCount;
        private long listCount;
        private long listElementCount;
        private long scalarTagCount;
        private long byteArrayCount;
        private long byteArrayElements;
        private long intArrayCount;
        private long intArrayElements;
        private long longArrayCount;
        private long longArrayElements;
        private long stringCount;
        private long modifiedUtf8Bytes;
        private long tagCountTotal;
        private long valueElementsTotal;

        private void walk(Tag tag, long parentContainerDepth) {
            tagCountTotal = add(tagCountTotal, 1L);
            if (tag instanceof CompoundTag compound) {
                var depth = add(parentContainerDepth, 1L);
                maxContainerDepth = Math.max(maxContainerDepth, depth);
                compoundCount = add(compoundCount, 1L);
                var keys = compound.getAllKeys();
                compoundEntryCount = add(compoundEntryCount, keys.size());
                valueElementsTotal = add(valueElementsTotal, keys.size());
                for (var key : keys) {
                    modifiedUtf8Bytes = add(
                            modifiedUtf8Bytes, modifiedUtf8Length(key));
                    var child = compound.get(key);
                    if (child == null) {
                        throw new IllegalStateException("Compound key had no value");
                    }
                    walk(child, depth);
                }
            } else if (tag instanceof ListTag list) {
                var depth = add(parentContainerDepth, 1L);
                maxContainerDepth = Math.max(maxContainerDepth, depth);
                listCount = add(listCount, 1L);
                listElementCount = add(listElementCount, list.size());
                valueElementsTotal = add(valueElementsTotal, list.size());
                for (var index = 0; index < list.size(); index++) {
                    walk(list.get(index), depth);
                }
            } else if (tag instanceof ByteArrayTag array) {
                byteArrayCount = add(byteArrayCount, 1L);
                byteArrayElements = add(byteArrayElements, array.size());
                valueElementsTotal = add(valueElementsTotal, array.size());
            } else if (tag instanceof IntArrayTag array) {
                intArrayCount = add(intArrayCount, 1L);
                intArrayElements = add(intArrayElements, array.size());
                valueElementsTotal = add(valueElementsTotal, array.size());
            } else if (tag instanceof LongArrayTag array) {
                longArrayCount = add(longArrayCount, 1L);
                longArrayElements = add(longArrayElements, array.size());
                valueElementsTotal = add(valueElementsTotal, array.size());
            } else if (tag instanceof StringTag string) {
                scalarTagCount = add(scalarTagCount, 1L);
                stringCount = add(stringCount, 1L);
                modifiedUtf8Bytes = add(
                        modifiedUtf8Bytes, modifiedUtf8Length(string.getAsString()));
            } else if (tag instanceof NumericTag) {
                scalarTagCount = add(scalarTagCount, 1L);
            }
        }

        private P4E0ResearchNbtMetrics freeze() {
            return new P4E0ResearchNbtMetrics(
                    maxContainerDepth,
                    compoundCount,
                    compoundEntryCount,
                    listCount,
                    listElementCount,
                    scalarTagCount,
                    byteArrayCount,
                    byteArrayElements,
                    intArrayCount,
                    intArrayElements,
                    longArrayCount,
                    longArrayElements,
                    stringCount,
                    modifiedUtf8Bytes,
                    tagCountTotal,
                    valueElementsTotal);
        }
    }
}
