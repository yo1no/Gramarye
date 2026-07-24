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

    void copyInto(byte[] destination, int offset) {
        copyRangeInto(0, bytes.length, destination, offset);
    }

    void copyRangeInto(
            int sourceOffset,
            int byteLength,
            byte[] destination,
            int destinationOffset) {
        Objects.requireNonNull(destination, "destination");
        Objects.checkFromIndexSize(sourceOffset, byteLength, bytes.length);
        Objects.checkFromIndexSize(destinationOffset, byteLength, destination.length);
        System.arraycopy(bytes, sourceOffset, destination, destinationOffset, byteLength);
    }

    HistoryBlobSource historySlice(int offset, int byteLength) {
        return new StoreHistoryBlobSlice(this, offset, byteLength);
    }

    RevisionBlobSource revisionSlice(int offset, int byteLength) {
        return new StoreRevisionBlobSlice(this, offset, byteLength);
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

/** Package-internal source for one complete encoded history entry. */
abstract sealed class HistoryBlobSource permits ImmutableHistoryBlob, StoreHistoryBlobSlice {
    abstract int byteCount();

    abstract void copyInto(byte[] destination, int offset);
}

/** Nominal, defensive handle for one encoded history entry. */
final class ImmutableHistoryBlob extends HistoryBlobSource {
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

    @Override
    int byteCount() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
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

/** Package-internal source for one complete encoded revision entry. */
abstract sealed class RevisionBlobSource permits ImmutableRevisionBlob, StoreRevisionBlobSlice {
    abstract int byteCount();

    abstract void copyInto(byte[] destination, int offset);
}

/** Nominal, defensive handle for one encoded revision entry. */
final class ImmutableRevisionBlob extends RevisionBlobSource {
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

    @Override
    int byteCount() {
        return bytes.length;
    }

    byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
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

/** Bounded history view backed only by the immutable root Store blob. */
final class StoreHistoryBlobSlice extends HistoryBlobSource {
    private final ImmutableStoreBlob root;
    private final int offset;
    private final int length;

    StoreHistoryBlobSlice(ImmutableStoreBlob root, int offset, int length) {
        this.root = Objects.requireNonNull(root, "root");
        this.offset = requireSlice(root, offset, length);
        this.length = length;
    }

    @Override
    int byteCount() {
        return length;
    }

    @Override
    void copyInto(byte[] destination, int destinationOffset) {
        root.copyRangeInto(offset, length, destination, destinationOffset);
    }

    @Override
    public String toString() {
        return "StoreHistoryBlobSlice[byteCount=" + length + "]";
    }

    private static int requireSlice(ImmutableStoreBlob root, int offset, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("history slice is outside the Store blob");
        }
        Objects.checkFromIndexSize(offset, length, root.byteCount());
        return offset;
    }
}

/** Bounded revision view backed only by the immutable root Store blob. */
final class StoreRevisionBlobSlice extends RevisionBlobSource {
    private final ImmutableStoreBlob root;
    private final int offset;
    private final int length;

    StoreRevisionBlobSlice(ImmutableStoreBlob root, int offset, int length) {
        this.root = Objects.requireNonNull(root, "root");
        this.offset = requireSlice(root, offset, length);
        this.length = length;
    }

    @Override
    int byteCount() {
        return length;
    }

    @Override
    void copyInto(byte[] destination, int destinationOffset) {
        root.copyRangeInto(offset, length, destination, destinationOffset);
    }

    @Override
    public String toString() {
        return "StoreRevisionBlobSlice[byteCount=" + length + "]";
    }

    private static int requireSlice(ImmutableStoreBlob root, int offset, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("revision slice is outside the Store blob");
        }
        Objects.checkFromIndexSize(offset, length, root.byteCount());
        return offset;
    }
}
