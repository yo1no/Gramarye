package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Typed side table that keeps both exact blobs outside the migration-visible tree. */
final class OpaqueSavedDataBlobTable {
    private final ImmutableStoreBlob storeBlob;
    private final OpaquePendingAttachmentUpdatesBlob pending;

    OpaqueSavedDataBlobTable(
            ImmutableStoreBlob storeBlob,
            OpaquePendingAttachmentUpdatesBlob pending) {
        this.storeBlob = Objects.requireNonNull(storeBlob, "storeBlob");
        this.pending = Objects.requireNonNull(pending, "pending");
    }

    ImmutableStoreBlob storeBlob() {
        return storeBlob;
    }

    OpaquePendingAttachmentUpdatesBlob pending() {
        return pending;
    }
}
