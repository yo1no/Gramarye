package com.yo1no.gramarye.magic.definition.store;

import java.util.List;
import java.util.Objects;

/** Transient writer layout: one immutable Store blob plus Store-root route/range metadata. */
final class StoreEncodingLayout {
    private final ImmutableStoreBlob blob;
    private final List<EncodedHistoryIndex> histories;

    private StoreEncodingLayout(
            ImmutableStoreBlob blob,
            List<EncodedHistoryIndex> histories) {
        this.blob = Objects.requireNonNull(blob, "blob");
        this.histories = List.copyOf(Objects.requireNonNull(histories, "histories"));
    }

    /**
     * Binds logical routes only to ranges emitted by the same Store framing operation.
     *
     * <p>The writer frame is the provenance token for the physical payload ranges. Route metadata
     * is supplied by the two reviewed composition paths, which encode new blobs from those same
     * routes or copy slices from an already verified carrier.</p>
     */
    static StoreEncodingLayout fromWriterFrame(
            StoreNbtFraming.EncodedStoreFrame frame) {
        Objects.requireNonNull(frame, "frame");
        var histories = List.copyOf(frame.histories());
        if (frame.historyRanges().size() != histories.size()) {
            throw new IllegalArgumentException(
                    "writer ranges and history indexes must have equal size");
        }
        for (var index = 0; index < histories.size(); index++) {
            var history = Objects.requireNonNull(histories.get(index), "history");
            var writerRange = frame.historyRanges().get(index);
            if (history.payloadOffset() != writerRange.offset()
                    || history.byteLength() != writerRange.length()) {
                throw new IllegalArgumentException(
                        "history index must use its writer-produced payload range");
            }
            StoreNbtFraming.BlobRange.fromLong(
                            history.payloadOffset(), history.byteLength())
                    .requireWithin(frame.blob().byteCount());
        }
        return new StoreEncodingLayout(frame.blob(), histories);
    }

    ImmutableStoreBlob blob() {
        return blob;
    }

    List<EncodedHistoryIndex> histories() {
        return histories;
    }

    @Override
    public String toString() {
        return "StoreEncodingLayout[storeByteCount=" + blob.byteCount()
                + ", historyCount=" + histories.size() + "]";
    }
}

sealed interface StoreLayoutEncodeResult
        permits StoreLayoutEncodeResult.Success, StoreLayoutEncodeResult.Failure {
    record Success(StoreEncodingLayout layout) implements StoreLayoutEncodeResult {
        public Success {
            Objects.requireNonNull(layout, "layout");
        }
    }

    record Failure(StorePersistenceFailure failure) implements StoreLayoutEncodeResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
