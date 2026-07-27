package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** The sole production admission owner for the complete player Attachment writeAnyTag bound. */
final class AttachmentTagSize {
    private AttachmentTagSize() {
    }

    static AttachmentTagSizeResult measure(Tag tag) throws IOException {
        Objects.requireNonNull(tag, "tag");
        var output = new BoundedCountingDataOutput(
                maximum());
        try {
            NbtIo.writeAnyTag(tag, output);
            return new AttachmentTagSizeResult.WithinLimit(output.byteCount());
        } catch (BoundedCountingDataOutput.CapacityExceeded exceeded) {
            return new AttachmentTagSizeResult.Exceeded(
                    exceeded.observedAtLeast(), exceeded.maximum());
        }
    }

    static long maximum() {
        return MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES;
    }

    static long observedAtLeast() {
        return maximum() + 1;
    }
}
