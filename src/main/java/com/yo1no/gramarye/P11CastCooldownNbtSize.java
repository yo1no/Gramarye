package com.yo1no.gramarye;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import net.minecraft.nbt.*;

/** Counts logical writeAnyTag bytes before any preservation copy, without invoking a writer. */
final class P11CastCooldownNbtSize {
    record Result(boolean fits, boolean depthExceeded, long observedAtLeast) { }
    private static final class Frame {
        final Tag tag;
        final int depth;
        final Iterator<String> keys;
        int index;
        Frame(Tag tag, int depth) {
            this.tag = tag; this.depth = depth;
            keys = tag instanceof CompoundTag compound ? compound.getAllKeys().iterator() : null;
        }
    }
    private P11CastCooldownNbtSize() { }

    static Result measure(Tag root, long maximum, int maxDepth) {
        long bytes = 1;
        var work = new ArrayDeque<Frame>();
        var ancestors = new IdentityHashMap<Tag, Boolean>();
        Tag tag = root;
        int depth = 1;
        while (true) {
            if (tag == null) { throw new IllegalArgumentException("COOLDOWN_NULL_NBT"); }
            if (depth > maxDepth) { return new Result(false, true, depth); }
            long add;
            if (tag.getClass() == ByteTag.class) { add = 1; }
            else if (tag.getClass() == ShortTag.class) { add = 2; }
            else if (tag.getClass() == IntTag.class || tag.getClass() == FloatTag.class) { add = 4; }
            else if (tag.getClass() == LongTag.class || tag.getClass() == DoubleTag.class) { add = 8; }
            else if (tag.getClass() == ByteArrayTag.class) { add = 4L + ((ByteArrayTag) tag).size(); }
            else if (tag.getClass() == IntArrayTag.class) { add = 4L + 4L * ((IntArrayTag) tag).size(); }
            else if (tag.getClass() == LongArrayTag.class) { add = 4L + 8L * ((LongArrayTag) tag).size(); }
            else if (tag.getClass() == StringTag.class) {
                if (maximum - bytes < 2) { return new Result(false, false, maximum + 1); }
                long utf = utf(((StringTag) tag).getAsString(), maximum - bytes - 2);
                if (utf > maximum - bytes - 2) { return new Result(false, false, maximum + 1); }
                if (utf > 65535) { throw new IllegalArgumentException("COOLDOWN_UNENCODABLE_UTF"); }
                add = 2 + utf;
            } else if (tag.getClass() == EndTag.class) { add = 0; }
            else if (tag.getClass() == ListTag.class || tag.getClass() == CompoundTag.class) {
                if (ancestors.put(tag, Boolean.TRUE) != null) { throw new IllegalArgumentException("COOLDOWN_CYCLIC_NBT"); }
                work.push(new Frame(tag, depth));
                if (tag instanceof ListTag list) {
                    add = 5;
                    if (!list.isEmpty() && list.getElementType() == Tag.TAG_END) {
                        throw new IllegalArgumentException("COOLDOWN_UNENCODABLE_LIST");
                    }
                } else {
                    add = 1;
                }
            } else { throw new IllegalArgumentException("COOLDOWN_UNKNOWN_NBT_IMPLEMENTATION"); }
            if (add > maximum - bytes) { return new Result(false, false, maximum + 1); }
            bytes += add;
            boolean found = false;
            while (!work.isEmpty()) {
                var frame = work.peek();
                if (frame.tag instanceof CompoundTag compound && frame.keys.hasNext()) {
                    String key = frame.keys.next();
                    Tag child = compound.get(key);
                    if (child == null || child.getId() == Tag.TAG_END) {
                        throw new IllegalArgumentException("COOLDOWN_NAMED_END_NBT");
                    }
                    if (maximum - bytes < 3) { return new Result(false, false, maximum + 1); }
                    long utf = utf(key, maximum - bytes - 3);
                    if (utf > maximum - bytes - 3) { return new Result(false, false, maximum + 1); }
                    if (utf > 65535) { throw new IllegalArgumentException("COOLDOWN_UNENCODABLE_UTF"); }
                    if (3 + utf > maximum - bytes) { return new Result(false, false, maximum + 1); }
                    bytes += 3 + utf;
                    tag = child; depth = frame.depth + 1; found = true; break;
                }
                if (frame.tag instanceof ListTag list && frame.index < list.size()) {
                    tag = list.get(frame.index++); depth = frame.depth + 1; found = true; break;
                }
                work.pop(); ancestors.remove(frame.tag);
            }
            if (!found) { return new Result(true, false, bytes); }
        }
    }

    private static long utf(String text, long remaining) {
        long result = 0;
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            result += c >= 1 && c <= 127 ? 1 : c > 2047 ? 3 : 2;
            if (result > remaining || result > 65535) { return result; }
        }
        return result;
    }
}
