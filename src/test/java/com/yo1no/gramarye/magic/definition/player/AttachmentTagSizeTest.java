package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.UTFDataFormatException;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

final class AttachmentTagSizeTest {
    @Test
    void writeAnyTagGoldenFramingHasTypeAndPayloadWithoutRootName() throws Exception {
        var nested = new CompoundTag();
        nested.put("x", new CompoundTag());

        assertEquals(1, exactSize(EndTag.INSTANCE));
        assertEquals(2, exactSize(ByteTag.valueOf((byte) 7)));
        assertEquals(5, exactSize(IntTag.valueOf(7)));
        assertEquals(6, exactSize(new ListTag()));
        assertEquals(2, exactSize(new CompoundTag()));
        assertEquals(7, exactSize(nested));
        assertEquals(142, exactSize(marker()));
        assertEquals(142, exactSize(PlayerSkillAttachmentMarker.freshTag()));
    }

    @Test
    void writeUnnamedTagAddsExactlyTwoRootNameBytesForNonEndTag() throws Exception {
        var tag = ByteTag.valueOf((byte) 1);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeUnnamedTag(tag, output);
        }
        assertEquals(exactSize(tag) + 2, bytes.size());
    }

    @Test
    void exactMaximumSucceedsAndMaximumPlusOneStopsWithoutBackingArray() throws Exception {
        var maximum = MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES;
        var exact = new BoundedCountingDataOutput(maximum);
        streamBytes(exact, maximum);
        assertEquals(maximum, exact.byteCount());

        var over = new BoundedCountingDataOutput(maximum);
        streamBytes(over, maximum);
        var failure = assertThrows(BoundedCountingDataOutput.CapacityExceeded.class,
                () -> over.writeByte(0));
        assertEquals(maximum + 1L, failure.observedAtLeast());
        assertEquals(maximum, failure.maximum());
        assertEquals(maximum, over.byteCount());
    }

    @Test
    void modifiedUtfCountingMatchesDataOutputContract() throws Exception {
        var output = new BoundedCountingDataOutput(100);
        output.writeUTF("A\u0000\u0080\u0800");
        assertEquals(10, output.byteCount());

        var tooLong = new BoundedCountingDataOutput(Long.MAX_VALUE);
        assertThrows(UTFDataFormatException.class, () -> tooLong.writeUTF("\u0800".repeat(21_846)));
    }

    private static long exactSize(net.minecraft.nbt.Tag tag) throws Exception {
        return ((AttachmentTagSizeResult.WithinLimit) AttachmentTagSize.measure(tag)).exactByteCount();
    }

    private static void streamBytes(BoundedCountingDataOutput output, int byteCount) throws Exception {
        var chunk = new byte[8_192];
        var remaining = byteCount;
        while (remaining > 0) {
            var next = Math.min(remaining, chunk.length);
            output.write(chunk, 0, next);
            remaining -= next;
        }
    }

    private static CompoundTag marker() {
        var metadata = new CompoundTag();
        metadata.putInt("schema_version", 0);
        metadata.putString("code", "encoded_capacity_exceeded");
        metadata.putLong("observed_at_least", 16_777_217L);
        metadata.putLong("maximum", 16_777_216L);
        var marker = new CompoundTag();
        marker.put("__gramarye_attachment_quarantine_v0", metadata);
        return marker;
    }
}
