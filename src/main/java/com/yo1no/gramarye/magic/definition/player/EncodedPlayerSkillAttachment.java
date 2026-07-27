package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable ownership of one prebuilt current Ready carrier. */
final class EncodedPlayerSkillAttachment {
    private final CompoundTag tag;
    private final long encodedByteCount;
    private final int draftCount;
    private final int latestCount;
    private final int equippedCount;

    private EncodedPlayerSkillAttachment(
            CompoundTag tag,
            long encodedByteCount,
            int draftCount,
            int latestCount,
            int equippedCount) {
        this.tag = Objects.requireNonNull(tag, "tag");
        if (encodedByteCount < 0) {
            throw new IllegalArgumentException("encodedByteCount must be non-negative");
        }
        this.encodedByteCount = encodedByteCount;
        this.draftCount = draftCount;
        this.latestCount = latestCount;
        this.equippedCount = equippedCount;
    }

    static EncodedPlayerSkillAttachment takeOwnership(
            CompoundTag freshUnexposedTag,
            long encodedByteCount,
            int draftCount,
            int latestCount,
            int equippedCount) {
        return new EncodedPlayerSkillAttachment(
                freshUnexposedTag,
                encodedByteCount,
                draftCount,
                latestCount,
                equippedCount);
    }

    long encodedByteCount() {
        return encodedByteCount;
    }

    Tag copyTag() {
        return tag.copy();
    }

    @Override
    public String toString() {
        return "EncodedPlayerSkillAttachment[encodedByteCount=" + encodedByteCount
                + ", draftCount=" + draftCount
                + ", latestCount=" + latestCount
                + ", equippedCount=" + equippedCount + ']';
    }
}
