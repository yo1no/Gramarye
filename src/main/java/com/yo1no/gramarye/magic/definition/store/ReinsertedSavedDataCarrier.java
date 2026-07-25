package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Current outer carrier after exact opaque Store/pending blob reinsertion. */
record ReinsertedSavedDataCarrier(
        int schemaVersion,
        ImmutableStoreBlob storeBlob,
        OpaquePendingAttachmentUpdatesBlob pending) {
    ReinsertedSavedDataCarrier {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        Objects.requireNonNull(storeBlob, "storeBlob");
        Objects.requireNonNull(pending, "pending");
    }

    @Override
    public String toString() {
        return "ReinsertedSavedDataCarrier[schemaVersion=" + schemaVersion
                + ", storeByteCount=" + storeBlob.byteCount()
                + ", pendingByteCount=" + pending.byteCount() + "]";
    }
}
