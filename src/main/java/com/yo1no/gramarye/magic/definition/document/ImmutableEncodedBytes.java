package com.yo1no.gramarye.magic.definition.document;

import java.util.Arrays;
import java.util.Objects;

/** Deeply isolated encoded bytes used only inside the document persistence boundary. */
final class ImmutableEncodedBytes {
    private final byte[] bytes;

    private ImmutableEncodedBytes(byte[] bytes, boolean trustedOwnedArray) {
        Objects.requireNonNull(bytes, "bytes");
        this.bytes = trustedOwnedArray ? bytes : bytes.clone();
    }

    static ImmutableEncodedBytes copyOf(byte[] bytes) {
        return new ImmutableEncodedBytes(bytes, false);
    }

    static ImmutableEncodedBytes takeOwnership(byte[] bytes) {
        return new ImmutableEncodedBytes(bytes, true);
    }

    int size() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ImmutableEncodedBytes encoded
                        && Arrays.equals(bytes, encoded.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ImmutableEncodedBytes[byteCount=" + bytes.length + "]";
    }
}
