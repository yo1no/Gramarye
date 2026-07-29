package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.Tag;

/** Version-neutral iterative arbitrary-NBT scan before schema-specific decoding. */
final class PendingAttachmentJournalWireScan {
    private PendingAttachmentJournalWireScan() {
    }

    static Result scan(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            return new Result.Rejected(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                    PendingAttachmentJournalFailure.Stage.FRAMING,
                    PendingAttachmentJournalFailure.Field.ROOT));
        }
        if (bytes.length > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
            return new Result.Rejected(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENCODED_CAPACITY_EXCEEDED,
                    (long) MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES + 1,
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
        }

        var cursor = new PendingAttachmentJournalCursor(bytes);
        var state = new ScanState();
        try {
            if (cursor.readUnsignedByte() != Tag.TAG_COMPOUND) {
                return new Result.Rejected(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                        PendingAttachmentJournalFailure.Stage.FRAMING,
                        PendingAttachmentJournalFailure.Field.ROOT));
            }
            var frames = new ArrayDeque<Frame>();
            frames.push(new CompoundFrame(true));
            while (!frames.isEmpty()) {
                switch (frames.pop()) {
                    case CompoundFrame compound -> scanCompound(cursor, frames, compound, state);
                    case ListFrame list -> scanList(frames, list);
                    case ValueFrame value -> scanValue(cursor, frames, value.type());
                }
            }
            if (!cursor.finished()) {
                return new Result.Rejected(PendingAttachmentJournalFailure.at(
                        PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                        PendingAttachmentJournalFailure.Stage.FRAMING,
                        PendingAttachmentJournalFailure.Field.ROOT));
            }
        } catch (MalformedWireException exception) {
            return new Result.Rejected(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                    PendingAttachmentJournalFailure.Stage.FRAMING,
                    PendingAttachmentJournalFailure.Field.ROOT));
        }
        if (state.duplicateSeen) {
            return new Result.Rejected(PendingAttachmentJournalFailure.duplicateAt(
                    state.firstDuplicateOffset));
        }
        return new Result.Scanned(
                state.schemaSeen, state.schemaTagType, state.schemaVersion);
    }

    private static void scanCompound(
            PendingAttachmentJournalCursor cursor,
            ArrayDeque<Frame> frames,
            CompoundFrame compound,
            ScanState state) throws MalformedWireException {
        var type = cursor.readUnsignedByte();
        if (type == Tag.TAG_END) {
            return;
        }
        requireKnownType(type, false);
        var name = cursor.readModifiedUtf();
        if (!compound.names.add(name)) {
            state.duplicateSeen = true;
            if (state.firstDuplicateOffset < 0) {
                state.firstDuplicateOffset = cursor.position();
            }
        }
        frames.push(compound);
        if (compound.root
                && name.equals(PendingAttachmentJournalSchema.VERSION)) {
            state.schemaSeen = true;
            state.schemaTagType = type;
            if (type == Tag.TAG_INT) {
                state.schemaVersion = cursor.readInt();
                return;
            }
        }
        frames.push(new ValueFrame(type));
    }

    private static void scanList(ArrayDeque<Frame> frames, ListFrame list) {
        if (list.remaining > 0) {
            frames.push(new ListFrame(list.type, list.remaining - 1));
            frames.push(new ValueFrame(list.type));
        }
    }

    private static void scanValue(
            PendingAttachmentJournalCursor cursor,
            ArrayDeque<Frame> frames,
            int type) throws MalformedWireException {
        switch (type) {
            case Tag.TAG_BYTE -> cursor.skip(1);
            case Tag.TAG_SHORT -> cursor.skip(2);
            case Tag.TAG_INT, Tag.TAG_FLOAT -> cursor.skip(4);
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> cursor.skip(8);
            case Tag.TAG_BYTE_ARRAY -> cursor.skipElements(cursor.readLength(), 1);
            case Tag.TAG_STRING -> cursor.readModifiedUtf();
            case Tag.TAG_LIST -> {
                var elementType = cursor.readUnsignedByte();
                var length = cursor.readLength();
                requireKnownType(elementType, length == 0);
                frames.push(new ListFrame(elementType, length));
            }
            case Tag.TAG_COMPOUND -> frames.push(new CompoundFrame(false));
            case Tag.TAG_INT_ARRAY -> cursor.skipElements(cursor.readLength(), 4);
            case Tag.TAG_LONG_ARRAY -> cursor.skipElements(cursor.readLength(), 8);
            default -> throw new MalformedWireException();
        }
    }

    private static void requireKnownType(int type, boolean allowEnd)
            throws MalformedWireException {
        if (type < (allowEnd ? Tag.TAG_END : Tag.TAG_BYTE) || type > Tag.TAG_LONG_ARRAY) {
            throw new MalformedWireException();
        }
    }

    sealed interface Result permits Result.Scanned, Result.Rejected {
        record Scanned(boolean schemaSeen, int schemaTagType, int schemaVersion)
                implements Result {
        }

        record Rejected(PendingAttachmentJournalFailure failure) implements Result {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    private sealed interface Frame permits CompoundFrame, ListFrame, ValueFrame {
    }

    private static final class CompoundFrame implements Frame {
        private final boolean root;
        private final Set<String> names = new HashSet<>();

        private CompoundFrame(boolean root) {
            this.root = root;
        }
    }

    private record ListFrame(int type, int remaining) implements Frame {
    }

    private record ValueFrame(int type) implements Frame {
    }

    private static final class ScanState {
        private boolean duplicateSeen;
        private int firstDuplicateOffset = -1;
        private boolean schemaSeen;
        private int schemaTagType = -1;
        private int schemaVersion = -1;
    }
}

/** Bounds-checked cursor with exact Java modified-UTF semantics. */
final class PendingAttachmentJournalCursor {
    private final byte[] bytes;
    private int position;

    PendingAttachmentJournalCursor(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    int readUnsignedByte() throws MalformedWireException {
        requireRemaining(1);
        return Byte.toUnsignedInt(bytes[position++]);
    }

    int readInt() throws MalformedWireException {
        requireRemaining(4);
        var value = (Byte.toUnsignedInt(bytes[position]) << 24)
                | (Byte.toUnsignedInt(bytes[position + 1]) << 16)
                | (Byte.toUnsignedInt(bytes[position + 2]) << 8)
                | Byte.toUnsignedInt(bytes[position + 3]);
        position += 4;
        return value;
    }

    int readLength() throws MalformedWireException {
        var length = readInt();
        if (length < 0) {
            throw new MalformedWireException();
        }
        return length;
    }

    int[] readUuidArray() throws MalformedWireException {
        if (readLength() != 4) {
            throw new MalformedWireException();
        }
        return new int[] {readInt(), readInt(), readInt(), readInt()};
    }

    String readModifiedUtf() throws MalformedWireException {
        requireRemaining(2);
        var encodedLength = (Byte.toUnsignedInt(bytes[position]) << 8)
                | Byte.toUnsignedInt(bytes[position + 1]);
        requireRemaining(encodedLength + 2);
        var framed = new byte[encodedLength + 2];
        System.arraycopy(bytes, position, framed, 0, framed.length);
        try (var input = new DataInputStream(new ByteArrayInputStream(framed))) {
            var decoded = input.readUTF();
            if (input.read() != -1) {
                throw new MalformedWireException();
            }
            position += framed.length;
            return decoded;
        } catch (IOException exception) {
            throw new MalformedWireException();
        }
    }

    void skip(int length) throws MalformedWireException {
        requireRemaining(length);
        position += length;
    }

    void skipElements(int count, int width) throws MalformedWireException {
        if (count < 0 || width <= 0 || count > remaining() / width) {
            throw new MalformedWireException();
        }
        position += count * width;
    }

    boolean finished() {
        return position == bytes.length;
    }

    int position() {
        return position;
    }

    private int remaining() {
        return bytes.length - position;
    }

    private void requireRemaining(int length) throws MalformedWireException {
        if (length < 0 || length > remaining()) {
            throw new MalformedWireException();
        }
    }
}

final class MalformedWireException extends Exception {
    private static final long serialVersionUID = 1L;
}
