package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.List;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Materialized-tree accounting only; no player, writer, native save limit, or durability claim. */
final class P11StrictNbtSizeTest {
    @Test
    void emptyCompoundUsesUnnamedRootFramingIncludingTerminator() throws Exception {
        var root = new CompoundTag();
        assertEquals(4, golden(root));
        for (long remaining = 0; remaining < 4; remaining++) {
            assertSame(P11StrictNbtSize.Exceeded.INSTANCE,
                    P11StrictNbtSize.measure(root, remaining));
        }
        assertEquals(new P11StrictNbtSize.Fits(4), P11StrictNbtSize.measure(root, 4));
        assertEquals(new P11StrictNbtSize.Fits(4), P11StrictNbtSize.measure(root, Long.MAX_VALUE));
    }

    @Test
    void everyNativePayloadKindMatchesActualUnnamedTagOutput() throws Exception {
        var nonemptyList = new ListTag();
        nonemptyList.add(IntTag.valueOf(1));
        nonemptyList.add(IntTag.valueOf(-1));
        var nested = new CompoundTag();
        nested.putLong("inner", Long.MIN_VALUE);
        for (Tag payload : List.of(
                ByteTag.valueOf((byte) -128), ShortTag.valueOf((short) -32768),
                IntTag.valueOf(Integer.MIN_VALUE), LongTag.valueOf(Long.MAX_VALUE),
                FloatTag.valueOf(Float.NaN), DoubleTag.valueOf(Double.NEGATIVE_INFINITY),
                new ByteArrayTag(new byte[] {0, 1, -1}),
                StringTag.valueOf("A\u0000\u0080\u0800\uD83D\uDE00"),
                new ListTag(), nonemptyList, new CompoundTag(), nested,
                new IntArrayTag(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}),
                new LongArrayTag(new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE}))) {
            var root = new CompoundTag();
            root.put("name\u0000\u0800", payload);
            assertGolden(root);
        }
    }

    @Test
    void arrayWidthsIncludeLengthButNotPerElementTypeHeaders() throws Exception {
        var root = new CompoundTag();
        root.put("b", new ByteArrayTag(new byte[7]));
        root.put("i", new IntArrayTag(new int[7]));
        root.put("l", new LongArrayTag(new long[7]));
        // Root=4; each named field header=4; array payloads=4+7*(1,4,8).
        assertEquals(119, golden(root));
        assertEquals(new P11StrictNbtSize.Fits(119), P11StrictNbtSize.measure(root, 119));
        assertSame(P11StrictNbtSize.Exceeded.INSTANCE, P11StrictNbtSize.measure(root, 118));
        var empty = new CompoundTag();
        empty.put("b", new ByteArrayTag(new byte[0]));
        empty.put("i", new IntArrayTag(new int[0]));
        empty.put("l", new LongArrayTag(new long[0]));
        assertEquals(28, golden(empty));
        assertGolden(empty);
    }

    @Test
    void compoundAndListWidthsDoNotCountHeapHeadersOrInventElementNames() throws Exception {
        var strings = new ListTag();
        strings.add(StringTag.valueOf("a"));
        strings.add(StringTag.valueOf("bb"));
        var lists = new ListTag();
        lists.add(new ListTag());
        lists.add(strings);
        var compounds = new ListTag();
        var one = new CompoundTag();
        one.putByte("v", (byte) 1);
        compounds.add(one);
        compounds.add(new CompoundTag());
        var root = new CompoundTag();
        root.put("lists", lists);
        root.put("compounds", compounds);
        assertGolden(root);
        assertEquals(Tag.TAG_LIST, lists.getElementType());
        assertEquals(Tag.TAG_STRING, strings.getElementType());
        assertEquals(Tag.TAG_COMPOUND, compounds.getElementType());
    }

    @Test
    void modifiedUtfUsesCodeUnitsIncludingNulAndUnpairedSurrogates() throws Exception {
        var root = new CompoundTag();
        root.putString("", "A\u0000\u007F\u0080\u07FF\u0800\uD800\uDC00\uD800");
        // Payload bytes 1+2+1+2+2+3+3+3+3=20, plus UTF length 2, field header 3, root 4.
        assertEquals(29, golden(root));
        assertGolden(root);
        var named = new CompoundTag();
        named.putInt("\u0000\uD83D\uDE00", 1);
        assertEquals(19, golden(named));
        assertGolden(named);
    }

    @Test
    void exactMaximumModifiedUtfStringAndKeyFitButOneMoreByteIsUnencodable() throws Exception {
        for (var text : List.of("a".repeat(65_535), "\u0800".repeat(21_845))) {
            var root = new CompoundTag();
            root.putString("v", text);
            assertGolden(root);
            var keyed = new CompoundTag();
            keyed.putByte(text, (byte) 1);
            assertGolden(keyed);
        }
        for (var text : List.of("a".repeat(65_536), "\u0800".repeat(21_846),
                "\u0000".repeat(32_768))) {
            var root = new CompoundTag();
            root.putString("v", text);
            assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                    P11StrictNbtSize.measure(root, Long.MAX_VALUE));
            assertThrows(UTFDataFormatException.class, () -> golden(root));
            var keyed = new CompoundTag();
            keyed.putByte(text, (byte) 1);
            assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                    P11StrictNbtSize.measure(keyed, Long.MAX_VALUE));
            assertThrows(UTFDataFormatException.class, () -> golden(keyed));
        }
    }

    @Test
    void budgetExhaustionStopsBeforeScanningAnOversizedStringToItsEnd() {
        var root = new CompoundTag();
        root.putString("v", "\u0800".repeat(30_000));
        // The framing already exceeds this budget long before the string's encoding limit.
        assertSame(P11StrictNbtSize.Exceeded.INSTANCE, P11StrictNbtSize.measure(root, 16));
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(root, Long.MAX_VALUE));
    }

    @Test
    void nativeContainerDepth512FitsAnd513RefusesOnlyTheAdditionalSeal() throws Exception {
        assertEquals(512, Tag.MAX_DEPTH);
        var maximum = compoundDepth(Tag.MAX_DEPTH);
        assertGolden(maximum);
        assertSame(P11StrictNbtSize.TooDeep.INSTANCE,
                P11StrictNbtSize.measure(compoundDepth(Tag.MAX_DEPTH + 1), Long.MAX_VALUE));
        // This is a bounded iterative refusal, not recursive descent through the complete graph.
        assertSame(P11StrictNbtSize.TooDeep.INSTANCE,
                P11StrictNbtSize.measure(compoundDepth(10_000), Long.MAX_VALUE));
    }

    @Test
    void scalarAndArrayChildrenDoNotConsumeAnotherContainerDepth() throws Exception {
        var root = new CompoundTag();
        var cursor = root;
        for (int depth = 1; depth < Tag.MAX_DEPTH; depth++) {
            var next = new CompoundTag();
            cursor.put("n", next);
            cursor = next;
        }
        cursor.putInt("scalar", 1);
        cursor.put("array", new LongArrayTag(new long[] {1, 2}));
        assertGolden(root);
        cursor.put("container", new ListTag());
        assertSame(P11StrictNbtSize.TooDeep.INSTANCE,
                P11StrictNbtSize.measure(root, Long.MAX_VALUE));
    }

    @Test
    void listAndCompoundContainersShareTheSameDepthCounter() throws Exception {
        var root = new CompoundTag();
        var first = new ListTag();
        root.put("list", first);
        var cursor = first;
        for (int depth = 2; depth < Tag.MAX_DEPTH; depth++) {
            var next = new ListTag();
            cursor.add(next);
            cursor = next;
        }
        cursor.add(IntTag.valueOf(1));
        assertGolden(root);
        cursor.clear();
        cursor.add(new CompoundTag());
        assertSame(P11StrictNbtSize.TooDeep.INSTANCE,
                P11StrictNbtSize.measure(root, Long.MAX_VALUE));
    }

    @Test
    void compoundListAndMixedCyclesAreRejectedWithoutRecursionOrWriting() {
        var self = new CompoundTag();
        self.put("self", self);
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(self, Long.MAX_VALUE));
        var list = new ListTag();
        list.add(list);
        var listRoot = new CompoundTag();
        listRoot.put("list", list);
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(listRoot, Long.MAX_VALUE));
        var mixed = new CompoundTag();
        var children = new ListTag();
        mixed.put("children", children);
        children.add(mixed);
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(mixed, Long.MAX_VALUE));
    }

    @Test
    void sharedSubtreesAreCountedAtEachOccurrenceRatherThanMisclassifiedAsCycles()
            throws Exception {
        var shared = new CompoundTag();
        shared.putLong("value", 7);
        var root = new CompoundTag();
        root.put("left", shared);
        root.put("right", shared);
        var list = new ListTag();
        list.add(shared);
        list.add(shared);
        root.put("list", list);
        assertGolden(root);
        assertSame(shared, root.get("left"));
        assertSame(shared, root.get("right"));
        assertSame(shared, list.get(0));
        assertSame(shared, list.get(1));
    }

    @Test
    void unsupportedOrMalformedShapesAreRefusedWithoutCallingTheirSerializers() {
        var custom = new CompoundTag() {
            @Override
            public void write(DataOutput output) throws IOException {
                throw new AssertionError("custom serializer must not be invoked");
            }
        };
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(custom, Long.MAX_VALUE));
        var nested = new CompoundTag();
        nested.put("custom", custom);
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(nested, Long.MAX_VALUE));
        var namedEnd = new CompoundTag();
        namedEnd.put("not-a-field", EndTag.INSTANCE);
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(namedEnd, Long.MAX_VALUE));
        var nullKey = new CompoundTag();
        nullKey.put(null, IntTag.valueOf(1));
        assertSame(P11StrictNbtSize.Unencodable.INSTANCE,
                P11StrictNbtSize.measure(nullKey, Long.MAX_VALUE));
    }

    @Test
    void longMaximumRemainingDoesNotWrapAndBadProgrammingInputsFailFast() throws Exception {
        var root = new CompoundTag();
        root.putLong("v", Long.MAX_VALUE);
        long exact = golden(root);
        assertEquals(new P11StrictNbtSize.Fits(exact),
                P11StrictNbtSize.measure(root, Long.MAX_VALUE));
        assertEquals(new P11StrictNbtSize.Fits(exact),
                P11StrictNbtSize.measure(root, Long.MAX_VALUE - 1));
        assertSame(P11StrictNbtSize.Exceeded.INSTANCE,
                P11StrictNbtSize.measure(root, exact - 1));
        assertThrows(NullPointerException.class, () -> P11StrictNbtSize.measure(null, 1));
        assertThrows(IllegalArgumentException.class, () -> P11StrictNbtSize.measure(root, -1));
        assertThrows(IllegalArgumentException.class,
                () -> P11StrictNbtSize.measure(root, Long.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> new P11StrictNbtSize.Fits(-1));
    }

    private static CompoundTag compoundDepth(int depth) {
        var root = new CompoundTag();
        var cursor = root;
        for (int index = 1; index < depth; index++) {
            var next = new CompoundTag();
            cursor.put("n", next);
            cursor = next;
        }
        return root;
    }

    private static void assertGolden(CompoundTag root) throws Exception {
        long exact = golden(root);
        assertEquals(exact,
                assertInstanceOf(P11StrictNbtSize.Fits.class,
                        P11StrictNbtSize.measure(root, exact)).bytes());
        assertSame(P11StrictNbtSize.Exceeded.INSTANCE,
                P11StrictNbtSize.measure(root, exact - 1));
        assertEquals(new P11StrictNbtSize.Fits(exact),
                P11StrictNbtSize.measure(root, Long.MAX_VALUE));
    }

    private static int golden(CompoundTag root) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeUnnamedTag(root, output);
        }
        return bytes.size();
    }
}
