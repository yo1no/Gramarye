package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagVisitor;
import net.minecraft.nbt.StreamTagVisitor;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentSerializerTest {
    private final PlayerSkillAttachmentSerializer serializer = new PlayerSkillAttachmentSerializer();

    @Test
    void serializerIsTagGenericAndNeverRegistersAnAttachmentType() {
        var generic = (ParameterizedType) PlayerSkillAttachmentSerializer.class
                .getGenericInterfaces()[0];
        assertEquals(IAttachmentSerializer.class, generic.getRawType());
        assertEquals(Tag.class, generic.getActualTypeArguments()[0]);
    }

    @Test
    void currentEmptyReadyLoadsAndWritesACompleteFreshCanonicalCarrier() {
        var input = emptyReadyTag();
        var state = serializer.read(null, input, null);
        var ready = assertInstanceOf(PlayerSkillAttachmentReady.class, state);
        var first = assertInstanceOf(CompoundTag.class, serializer.write(ready, null));
        first.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 99);
        var second = assertInstanceOf(CompoundTag.class, serializer.write(ready, null));

        assertEquals(PlayerSkillAttachmentSchema.OUTER_FIELDS, second.getAllKeys());
        assertEquals(0, ((IntTag) second.get(
                PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION)).getAsInt());
        assertEquals(0, ((ListTag) second.get(PlayerSkillAttachmentSchema.DRAFTS)).size());
        assertEquals(0, ((ListTag) second.get(PlayerSkillAttachmentSchema.LATEST_STATES)).size());
        assertEquals(0, ((ListTag) second.get(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS)).size());
        assertEquals(Set.of(), ((CompoundTag) second.get(PlayerSkillAttachmentSchema.EDITOR)).getAllKeys());
        assertNotSame(first, second);
        assertTrue(ready.carrier().encodedByteCount() > 0);
    }

    @Test
    void everyWrongRootKindBecomesNonNullPreservedRawAndRoundTripsStructurally() {
        for (var malformed : new Tag[] {ByteTag.valueOf((byte) 7), new ListTag(), IntTag.valueOf(4)}) {
            var state = serializer.read(null, malformed, null);
            var preserved = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class, state);
            var first = serializer.write(preserved, null);
            assertEquals(malformed, first);
            if (first instanceof ListTag list) {
                assertNotSame(malformed, first);
                list.add(IntTag.valueOf(9));
            }
            assertEquals(malformed, serializer.write(preserved, null));
        }
    }

    @Test
    void malformedCompoundIsNotAnEmptyFallbackAndPreservesAnIsolatedSnapshot() {
        var input = new CompoundTag();
        input.putString("private", "value");
        var state = serializer.read(null, input, null);
        var preserved = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class, state);
        input.putString("private", "changed-after-read");
        var output = assertInstanceOf(CompoundTag.class, serializer.write(preserved, null));
        output.putString("private", "changed-after-write");

        assertEquals("value", ((CompoundTag) serializer.write(preserved, null)).getString("private"));
        assertEquals(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                preserved.failure().code());
        assertFalse(preserved.toString().contains("value"));
    }

    @Test
    void exactMarkerRestartsAsOversizeAndEveryWriteIsFresh() {
        var state = serializer.read(null, PlayerSkillAttachmentMarker.freshTag(), null);
        var oversize = assertInstanceOf(PlayerSkillAttachmentOversizeMarker.class, state);
        var first = assertInstanceOf(CompoundTag.class, serializer.write(oversize, null));
        assertTrue(PlayerSkillAttachmentMarker.isExact(first));
        first.putInt("mutation", 1);
        var second = assertInstanceOf(CompoundTag.class, serializer.write(oversize, null));
        assertTrue(PlayerSkillAttachmentMarker.isExact(second));
        assertNotSame(first, second);
        assertInstanceOf(
                PlayerSkillAttachmentOversizeMarker.class,
                serializer.read(null, second, null));
    }

    @Test
    void markerNearMissesArePreservedRawInsteadOfDestructiveQuarantine() {
        var extra = PlayerSkillAttachmentMarker.freshTag();
        extra.putInt("extra", 1);
        var wrongValue = PlayerSkillAttachmentMarker.freshTag();
        ((CompoundTag) wrongValue.get(PlayerSkillAttachmentMarker.ROOT_FIELD))
                .putLong("maximum", 3);
        var wrongType = PlayerSkillAttachmentMarker.freshTag();
        ((CompoundTag) wrongType.get(PlayerSkillAttachmentMarker.ROOT_FIELD))
                .putString("maximum", "16777216");
        var missing = PlayerSkillAttachmentMarker.freshTag();
        ((CompoundTag) missing.get(PlayerSkillAttachmentMarker.ROOT_FIELD))
                .remove("maximum");

        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, extra, null));
        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, wrongValue, null));
        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, wrongType, null));
        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, missing, null));
    }

    @Test
    void capacityStopsBeforeRawCopyWhileErrorFromInBoundCopyPassesThrough() {
        var oversize = new StreamingTestTag((int) AttachmentTagSize.maximum(), true);
        var state = serializer.read(null, oversize, null);
        assertInstanceOf(PlayerSkillAttachmentOversizeMarker.class, state);
        assertFalse(oversize.copyInvoked);

        var inBound = new StreamingTestTag(1, true);
        assertThrows(AssertionError.class, () -> serializer.read(null, inBound, null));
        assertTrue(inBound.copyInvoked);
    }

    @Test
    void quarantineStateFactoriesEnforceTheFixedBoundaries() {
        var failure = PlayerSkillAttachmentFailure.simple(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA);
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerSkillAttachmentPreservedRaw(
                        failure, new CompoundTag(), AttachmentTagSize.maximum() + 1));
        var marker = new PlayerSkillAttachmentOversizeMarker();
        assertEquals(AttachmentTagSize.observedAtLeast(), marker.observedAtLeast());
        assertEquals(AttachmentTagSize.maximum(), marker.maximum());
    }

    private static CompoundTag emptyReadyTag() {
        var root = new CompoundTag();
        root.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 0);
        root.put(PlayerSkillAttachmentSchema.DRAFTS, new ListTag());
        root.put(PlayerSkillAttachmentSchema.LATEST_STATES, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EDITOR, new CompoundTag());
        return root;
    }

    private static final class StreamingTestTag implements Tag {
        private final int payloadBytes;
        private final boolean errorOnCopy;
        private boolean copyInvoked;

        private StreamingTestTag(int payloadBytes, boolean errorOnCopy) {
            this.payloadBytes = payloadBytes;
            this.errorOnCopy = errorOnCopy;
        }

        @Override
        public void write(DataOutput output) throws IOException {
            var chunk = new byte[8_192];
            var remaining = payloadBytes;
            while (remaining > 0) {
                var count = Math.min(remaining, chunk.length);
                output.write(chunk, 0, count);
                remaining -= count;
            }
        }

        @Override
        public byte getId() {
            return TAG_BYTE;
        }

        @Override
        public TagType<?> getType() {
            return net.minecraft.nbt.ByteTag.TYPE;
        }

        @Override
        public Tag copy() {
            copyInvoked = true;
            if (errorOnCopy) {
                throw new AssertionError("copy passthrough");
            }
            return this;
        }

        @Override
        public int sizeInBytes() {
            return 0;
        }

        @Override
        public void accept(TagVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "StreamingTestTag[payloadBytes=" + payloadBytes + ']';
        }
    }
}
