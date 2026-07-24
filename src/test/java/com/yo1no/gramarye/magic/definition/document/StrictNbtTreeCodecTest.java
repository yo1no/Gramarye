package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
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
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class StrictNbtTreeCodecTest {
    @Test
    void arbitraryCompoundRoundTripPreservesEveryStandardValueTypeAndOrder() throws Exception {
        var list = new ListTag();
        list.add(StringTag.valueOf("first"));
        list.add(StringTag.valueOf("second"));
        var source = new CompoundTag();
        source.put("byte", ByteTag.valueOf((byte) 1));
        source.put("short", ShortTag.valueOf((short) 2));
        source.put("int", IntTag.valueOf(3));
        source.put("long", LongTag.valueOf(4));
        source.put("float", FloatTag.valueOf(5.5F));
        source.put("double", DoubleTag.valueOf(6.5));
        source.put("string", StringTag.valueOf("utf8-魔法"));
        source.put("list", list);
        source.put("bytes", new ByteArrayTag(new byte[] {1, 2}));
        source.put("ints", new IntArrayTag(new int[] {3, 4}));
        source.put("longs", new LongArrayTag(new long[] {5, 6}));

        var encoded = StrictNbtTreeCodec.encode(source, 16_384);
        var decoded = StrictNbtTreeCodec.decode(encoded, 16_384);

        assertEquals(source, decoded);
        var decodedList = assertInstanceOf(CompoundTag.class, decoded).get("list");
        assertEquals(list, decodedList);
    }

    @Test
    void arbitraryPrimitiveRootAndSingleByteEndTagAreSupported() throws Exception {
        var primitive = IntTag.valueOf(42);
        var primitiveBytes = StrictNbtTreeCodec.encode(primitive, 16);
        var endBytes = StrictNbtTreeCodec.encode(EndTag.INSTANCE, 1);

        assertArrayEquals(new byte[] {Tag.TAG_INT, 0, 0, 0, 42}, primitiveBytes.copyBytes());
        assertArrayEquals(new byte[] {Tag.TAG_END}, endBytes.copyBytes());
        assertEquals(primitive, StrictNbtTreeCodec.decode(primitiveBytes, 16));
        assertEquals(EndTag.INSTANCE, StrictNbtTreeCodec.decode(endBytes, 1));
        assertEquals(1, endBytes.size());
    }

    @Test
    void everyPrimitiveAndPrimitiveArrayCanBeAnArbitraryRoot() throws Exception {
        var roots = List.<net.minecraft.nbt.Tag>of(
                ByteTag.valueOf((byte) -3),
                ShortTag.valueOf((short) 12),
                IntTag.valueOf(34),
                LongTag.valueOf(56L),
                FloatTag.valueOf(7.25F),
                DoubleTag.valueOf(-8.5D),
                StringTag.valueOf("root-魔法"),
                new ByteArrayTag(new byte[] {-1, 0, 1}),
                new IntArrayTag(new int[] {-2, 0, 2}),
                new LongArrayTag(new long[] {-3L, 0L, 3L}));

        for (var root : roots) {
            var encoded = StrictNbtTreeCodec.encode(root, 1_024);
            var decoded = StrictNbtTreeCodec.decode(encoded, 1_024);

            assertEquals(root.getClass(), decoded.getClass());
            assertEquals(root, decoded);
        }
    }

    @Test
    void exactMaximumByteArrayFixturePassesFiniteAccounterAndMaximumPlusOneFails()
            throws Exception {
        var maximum = MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES;
        var atLimit = new ByteArrayTag(new byte[maximum - 5]);
        var overLimit = new ByteArrayTag(new byte[maximum - 4]);

        var encoded = StrictNbtTreeCodec.encode(atLimit, maximum);

        assertEquals(maximum, encoded.size());
        assertEquals(atLimit, StrictNbtTreeCodec.decode(encoded, maximum));
        assertThrows(BoundedByteEncoding.CapacityExceeded.class,
                () -> StrictNbtTreeCodec.encode(overLimit, maximum));
        assertEquals(
                2L * maximum
                        + 65L * MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES,
                StrictNbtTreeCodec.accounterQuota(maximum));
    }

    @Test
    void emptyTrailingMalformedAndExcessiveDepthAreRejected() throws Exception {
        assertThrows(MalformedTreeException.class,
                () -> StrictNbtTreeCodec.decode(ImmutableEncodedBytes.copyOf(new byte[0]), 32));
        assertThrows(MalformedTreeException.class,
                () -> StrictNbtTreeCodec.decode(
                        ImmutableEncodedBytes.copyOf(new byte[] {(byte) 127}),
                        32));

        var valid = StrictNbtTreeCodec.encode(IntTag.valueOf(7), 32).copyBytes();
        var trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(MalformedTreeException.class,
                () -> StrictNbtTreeCodec.decode(ImmutableEncodedBytes.copyOf(trailing), 32));

        var exactDepth = nestedCompound(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH);
        var exactDepthBytes = StrictNbtTreeCodec.encode(exactDepth, 4_096);
        assertEquals(exactDepth, StrictNbtTreeCodec.decode(exactDepthBytes, 4_096));

        var tooDeep = nestedCompound(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH + 1);
        var deepBytes = StrictNbtTreeCodec.encode(tooDeep, 4_096);
        assertThrows(MalformedTreeException.class,
                () -> StrictNbtTreeCodec.decode(deepBytes, 4_096));
    }

    @Test
    void nestedEndTagIsRejectedBecauseNbtUsesItAsAContainerTerminator() {
        var compound = new CompoundTag();
        compound.put("not-encodable", EndTag.INSTANCE);

        assertThrows(MalformedTreeException.class,
                () -> StrictNbtTreeCodec.encode(compound, 32));
    }

    private static CompoundTag nestedCompound(int depth) {
        var root = new CompoundTag();
        var current = root;
        for (var index = 1; index < depth; index++) {
            var child = new CompoundTag();
            current.put("next", child);
            current = child;
        }
        return root;
    }
}
