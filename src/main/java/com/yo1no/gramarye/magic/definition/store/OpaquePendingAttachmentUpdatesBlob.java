package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Immutable P4-B1 transport value for an opaque pending Attachment-update journal blob. */
final class OpaquePendingAttachmentUpdatesBlob {
    private static final OpaquePendingAttachmentUpdatesBlob EMPTY =
            new OpaquePendingAttachmentUpdatesBlob(new byte[0], true);

    private final byte[] bytes;

    private OpaquePendingAttachmentUpdatesBlob(byte[] bytes, boolean trusted) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "pending Attachment-update blob exceeds its encoded-byte ceiling");
        }
        this.bytes = trusted ? bytes : bytes.clone();
    }

    static OpaquePendingAttachmentUpdatesBlob empty() {
        return EMPTY;
    }

    static OpaquePendingAttachmentUpdatesBlob capture(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.length == 0
                ? EMPTY
                : new OpaquePendingAttachmentUpdatesBlob(bytes, false);
    }

    int byteCount() {
        return bytes.length;
    }

    boolean isEmpty() {
        return bytes.length == 0;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    void copyInto(byte[] destination, int offset) {
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(offset, bytes.length, destination.length);
        System.arraycopy(bytes, 0, destination, offset, bytes.length);
    }

    boolean contentEquals(OpaquePendingAttachmentUpdatesBlob other) {
        return other != null && java.util.Arrays.equals(bytes, other.bytes);
    }

    @Override
    public String toString() {
        return "OpaquePendingAttachmentUpdatesBlob[byteCount=" + bytes.length
                + ", empty=" + isEmpty() + "]";
    }
}
