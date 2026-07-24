package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
import java.util.Objects;

/**
 * Opaque, deeply isolated bytes for one family-tagged physical skill document.
 *
 * <p>This transport handle exposes no physical fields and is not a Store identity. Loading it must
 * go through {@link SkillDocumentStorePersistenceFacade#load(EncodedSkillDocument, java.util.Optional)}
 * so schema migration cannot be bypassed.</p>
 */
public final class EncodedSkillDocument {
    private final byte[] bytes;

    private EncodedSkillDocument(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        if (bytes.length == 0 || bytes.length > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
            throw new IllegalArgumentException(
                    "encoded document byte count must be within the hard range");
        }
    }

    /** Creates a defensive snapshot after enforcing the encoded-document hard ceiling. */
    public static EncodedSkillDocument copyOf(byte[] bytes) {
        return new EncodedSkillDocument(bytes);
    }

    /** Returns the bounded encoded byte count without exposing the bytes. */
    public int byteCount() {
        return bytes.length;
    }

    /** Returns a new defensive copy of the opaque physical bytes. */
    public byte[] copyBytes() {
        return bytes.clone();
    }

    /**
     * Defensively copies the complete bounded encoding into caller-owned storage without
     * exposing the internal array or allocating an intermediate copy.
     */
    public void copyInto(byte[] destination, int offset) {
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(offset, bytes.length, destination.length);
        System.arraycopy(bytes, 0, destination, offset, bytes.length);
    }

    static EncodedSkillDocument fromInternal(ImmutableEncodedBytes encoded) {
        Objects.requireNonNull(encoded, "encoded");
        return new EncodedSkillDocument(encoded.copyBytes());
    }

    ImmutableEncodedBytes copyInternal() {
        return ImmutableEncodedBytes.copyOf(bytes);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EncodedSkillDocument document
                        && Arrays.equals(bytes, document.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "EncodedSkillDocument[byteCount=" + bytes.length + "]";
    }
}
