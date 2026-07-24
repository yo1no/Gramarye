package com.yo1no.gramarye.magic.definition.store;

import java.util.Arrays;
import java.util.Objects;

/** Defensive ingress/egress handle for one uncompressed arbitrary-NBT Store blob. */
final class ImmutableStoreBlob {
    private final byte[] bytes;

    private ImmutableStoreBlob(byte[] bytes, boolean trusted) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Store blob must not be empty");
        }
        this.bytes = trusted ? bytes : bytes.clone();
    }

    static ImmutableStoreBlob copyOf(byte[] bytes) {
        return new ImmutableStoreBlob(bytes, false);
    }

    static ImmutableStoreBlob takeOwnership(byte[] bytes) {
        return new ImmutableStoreBlob(bytes, true);
    }

    int byteCount() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ImmutableStoreBlob blob && Arrays.equals(bytes, blob.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "ImmutableStoreBlob[byteCount=" + bytes.length + "]";
    }
}

/** Nominal, defensive handle for one encoded history entry. */
final class ImmutableHistoryBlob {
    private final byte[] bytes;

    private ImmutableHistoryBlob(byte[] bytes, boolean trusted) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("history blob must not be empty");
        }
        this.bytes = trusted ? bytes : bytes.clone();
    }

    static ImmutableHistoryBlob copyOf(byte[] bytes) {
        return new ImmutableHistoryBlob(bytes, false);
    }

    static ImmutableHistoryBlob takeOwnership(byte[] bytes) {
        return new ImmutableHistoryBlob(bytes, true);
    }

    int byteCount() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    void copyInto(byte[] destination, int offset) {
        Objects.requireNonNull(destination, "destination");
        if (offset < 0 || bytes.length > destination.length - offset) {
            throw new IndexOutOfBoundsException("history copy destination is too small");
        }
        System.arraycopy(bytes, 0, destination, offset, bytes.length);
    }

    @Override
    public String toString() {
        return "ImmutableHistoryBlob[byteCount=" + bytes.length + "]";
    }
}

/** Nominal, defensive handle for one encoded revision entry. */
final class ImmutableRevisionBlob {
    private final byte[] bytes;

    private ImmutableRevisionBlob(byte[] bytes, boolean trusted) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("revision blob must not be empty");
        }
        this.bytes = trusted ? bytes : bytes.clone();
    }

    static ImmutableRevisionBlob copyOf(byte[] bytes) {
        return new ImmutableRevisionBlob(bytes, false);
    }

    static ImmutableRevisionBlob takeOwnership(byte[] bytes) {
        return new ImmutableRevisionBlob(bytes, true);
    }

    int byteCount() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    void copyInto(byte[] destination, int offset) {
        Objects.requireNonNull(destination, "destination");
        if (offset < 0 || bytes.length > destination.length - offset) {
            throw new IndexOutOfBoundsException("revision copy destination is too small");
        }
        System.arraycopy(bytes, 0, destination, offset, bytes.length);
    }

    @Override
    public String toString() {
        return "ImmutableRevisionBlob[byteCount=" + bytes.length + "]";
    }
}
