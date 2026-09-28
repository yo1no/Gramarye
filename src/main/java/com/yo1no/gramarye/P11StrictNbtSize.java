package com.yo1no.gramarye;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Objects;
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
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Strict writeUnnamedTag width of an already materialized root, without serializing or copying it.
 * Refusal limits only an additional P11 seal, never the legality of native whole-player saving.
 * The caller owns coherence: a successful count is not a source, snapshot, or persistence receipt.
 */
final class P11StrictNbtSize {
    private P11StrictNbtSize() {
        throw new AssertionError("no instances");
    }

    sealed interface Result permits Fits, Exceeded, Unencodable, TooDeep { }

    record Fits(long bytes) implements Result {
        Fits {
            if (bytes < 0) {
                throw new IllegalArgumentException("negative NBT width");
            }
        }
    }

    enum Exceeded implements Result { INSTANCE }

    enum Unencodable implements Result { INSTANCE }

    enum TooDeep implements Result { INSTANCE }

    static Result measure(CompoundTag root, long remainingBytes) {
        Objects.requireNonNull(root, "root");
        if (remainingBytes < 0) {
            throw new IllegalArgumentException("negative remaining bytes");
        }
        return new Walker(remainingBytes).measure(root);
    }

    private sealed interface Frame permits CompoundFrame, ListFrame {
        Tag tag();
    }

    private record CompoundFrame(CompoundTag tag, Iterator<String> keys) implements Frame { }

    private static final class ListFrame implements Frame {
        private final ListTag tag;
        private final int elementKind;
        private int next;

        private ListFrame(ListTag tag, int elementKind) {
            this.tag = tag;
            this.elementKind = elementKind;
        }

        @Override
        public ListTag tag() {
            return tag;
        }
    }

    private static final class Walker {
        private final long remainingBytes;
        private final ArrayDeque<Frame> frames = new ArrayDeque<>();
        private final IdentityHashMap<Tag, Boolean> ancestors = new IdentityHashMap<>();
        private long bytes;
        private Result failure;

        private Walker(long remainingBytes) {
            this.remainingBytes = remainingBytes;
        }

        private Result measure(CompoundTag root) {
            // Exact native shapes only: a custom Tag.write override need not use this framing.
            if (kind(root) != Tag.TAG_COMPOUND) {
                return Unencodable.INSTANCE;
            }
            // Root type byte and the empty modified-UTF name's unsigned-short length.
            if (!add(3) || !value(root, Tag.TAG_COMPOUND)) {
                return failure;
            }
            while (!frames.isEmpty() && failure == null) {
                var frame = frames.peek();
                if (frame instanceof CompoundFrame compound) {
                    if (!compound.keys.hasNext()) {
                        if (!add(1)) {
                            break;
                        }
                        leave();
                        continue;
                    }
                    var key = compound.keys.next();
                    var tag = compound.tag.get(key);
                    int kind = kind(tag);
                    // A named END would terminate the compound instead of encoding a field.
                    if (key == null || kind < 1) {
                        failure = Unencodable.INSTANCE;
                        break;
                    }
                    if (!add(1) || !utf(key) || !value(tag, kind)) {
                        break;
                    }
                } else {
                    var list = (ListFrame) frame;
                    if (list.next == list.tag.size()) {
                        leave();
                        continue;
                    }
                    var tag = list.tag.get(list.next++);
                    int kind = kind(tag);
                    if (kind < 1 || kind != list.elementKind) {
                        failure = Unencodable.INSTANCE;
                        break;
                    }
                    if (!value(tag, kind)) {
                        break;
                    }
                }
            }
            return failure == null ? new Fits(bytes) : failure;
        }

        private boolean value(Tag tag, int kind) {
            return switch (kind) {
                case Tag.TAG_BYTE -> add(1);
                case Tag.TAG_SHORT -> add(2);
                case Tag.TAG_INT, Tag.TAG_FLOAT -> add(4);
                case Tag.TAG_LONG, Tag.TAG_DOUBLE -> add(8);
                case Tag.TAG_BYTE_ARRAY -> array(((ByteArrayTag) tag).size(), 1);
                case Tag.TAG_INT_ARRAY -> array(((IntArrayTag) tag).size(), 4);
                case Tag.TAG_LONG_ARRAY -> array(((LongArrayTag) tag).size(), 8);
                case Tag.TAG_STRING -> utf(((StringTag) tag).getAsString());
                case Tag.TAG_COMPOUND -> {
                    if (!enter(tag)) {
                        yield false;
                    }
                    var compound = (CompoundTag) tag;
                    frames.push(new CompoundFrame(compound, compound.getAllKeys().iterator()));
                    yield true;
                }
                case Tag.TAG_LIST -> {
                    if (!enter(tag) || !add(5)) {
                        yield false;
                    }
                    var list = (ListTag) tag;
                    // Native ListTag.write derives type from the first element (empty => END),
                    // but changes its own type field. Counting must not perform that mutation.
                    int elementKind = list.isEmpty() ? Tag.TAG_END : kind(list.get(0));
                    if (!list.isEmpty() && elementKind < 1) {
                        failure = Unencodable.INSTANCE;
                        yield false;
                    }
                    frames.push(new ListFrame(list, elementKind));
                    yield true;
                }
                default -> {
                    failure = Unencodable.INSTANCE;
                    yield false;
                }
            };
        }

        private boolean enter(Tag tag) {
            if (ancestors.containsKey(tag)) {
                failure = Unencodable.INSTANCE;
                return false;
            }
            // Matches locked NbtAccounter: only Compound/List push depth, root container is 1.
            if (frames.size() >= Tag.MAX_DEPTH) {
                failure = TooDeep.INSTANCE;
                return false;
            }
            ancestors.put(tag, Boolean.TRUE);
            return true;
        }

        private void leave() {
            ancestors.remove(frames.pop().tag());
        }

        private boolean array(int length, int elementBytes) {
            return add(4) && add(Math.multiplyExact((long) length, elementBytes));
        }

        private boolean utf(String value) {
            if (!add(2)) {
                return false;
            }
            int payloadBytes = 0;
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                int width = character >= 1 && character <= 0x7F ? 1 : character <= 0x7FF ? 2 : 3;
                // At most 65538: no String-length multiplication or intermediate int overflow.
                payloadBytes += width;
                if (payloadBytes > 65_535) {
                    failure = Unencodable.INSTANCE;
                    return false;
                }
                if (!add(width)) {
                    return false;
                }
            }
            return true;
        }

        private boolean add(long count) {
            // Invariant 0 <= bytes <= remainingBytes. Prove room before adding; no limit+1
            // expression can wrap at Long.MAX_VALUE. Exceeded records the first excess only.
            if (count > remainingBytes - bytes) {
                failure = Exceeded.INSTANCE;
                return false;
            }
            bytes += count;
            return true;
        }
    }

    private static int kind(Tag tag) {
        if (tag == null) {
            return -1;
        }
        var type = tag.getClass();
        if (type == ByteTag.class) return Tag.TAG_BYTE;
        if (type == ShortTag.class) return Tag.TAG_SHORT;
        if (type == IntTag.class) return Tag.TAG_INT;
        if (type == LongTag.class) return Tag.TAG_LONG;
        if (type == FloatTag.class) return Tag.TAG_FLOAT;
        if (type == DoubleTag.class) return Tag.TAG_DOUBLE;
        if (type == ByteArrayTag.class) return Tag.TAG_BYTE_ARRAY;
        if (type == StringTag.class) return Tag.TAG_STRING;
        if (type == ListTag.class) return Tag.TAG_LIST;
        if (type == CompoundTag.class) return Tag.TAG_COMPOUND;
        if (type == IntArrayTag.class) return Tag.TAG_INT_ARRAY;
        if (type == LongArrayTag.class) return Tag.TAG_LONG_ARRAY;
        return -1;
    }
}
