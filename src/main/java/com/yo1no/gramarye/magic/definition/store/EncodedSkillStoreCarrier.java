package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, package-internal derived persistence representation of one Store snapshot.
 *
 * <p>The domain {@link SkillDefinitionStore} remains the owner, document, latest-revision, quota,
 * and compare-and-insert truth. This carrier retains one canonical Store blob plus immutable route
 * and range metadata. It deliberately exposes no gameplay document lookup and uses object identity,
 * rather than structural equality, when a prospective update is tied to a base carrier.</p>
 */
final class EncodedSkillStoreCarrier {
    private final ImmutableStoreBlob storeBlob;
    private final List<EncodedHistoryIndex> histories;
    private final int historyCount;
    private final int revisionCount;
    private final long totalHistoryBlobBytes;
    private final long totalRevisionBlobBytes;

    private EncodedSkillStoreCarrier(
            ImmutableStoreBlob storeBlob,
            List<EncodedHistoryIndex> histories) {
        this.storeBlob = Objects.requireNonNull(storeBlob, "storeBlob");
        this.histories = List.copyOf(Objects.requireNonNull(histories, "histories"));
        if (storeBlob.byteCount() > MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES) {
            throw new IllegalArgumentException("Store blob exceeds the encoded Store ceiling");
        }

        SkillId previousSkillId = null;
        long previousHistoryEnd = 0;
        var revisions = 0;
        var historyBytes = 0L;
        var revisionBytes = 0L;
        for (var history : this.histories) {
            Objects.requireNonNull(history, "history");
            if (previousSkillId != null
                    && previousSkillId.value().compareTo(history.skillId().value()) >= 0) {
                throw new IllegalArgumentException(
                        "carrier histories must be in strict canonical SkillId order");
            }
            history.range().requireWithin(storeBlob.byteCount());
            if (history.byteLength()
                    > MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES) {
                throw new IllegalArgumentException(
                        "encoded history index exceeds the history byte ceiling");
            }
            if (history.payloadOffset() < previousHistoryEnd) {
                throw new IllegalArgumentException("carrier history ranges must not overlap");
            }
            previousSkillId = history.skillId();
            previousHistoryEnd = history.range().endExclusive();
            revisions = Math.addExact(revisions, history.revisions().size());
            historyBytes = Math.addExact(historyBytes, history.byteLength());
            for (var revision : history.revisions()) {
                if (revision.byteLength()
                        > MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES) {
                    throw new IllegalArgumentException(
                            "encoded revision index exceeds the revision byte ceiling");
                }
                revisionBytes = Math.addExact(revisionBytes, revision.byteLength());
            }
        }
        historyCount = this.histories.size();
        revisionCount = revisions;
        totalHistoryBlobBytes = historyBytes;
        totalRevisionBlobBytes = revisionBytes;
    }

    static EncodedSkillStoreCarrier fromLayout(StoreEncodingLayout layout) {
        Objects.requireNonNull(layout, "layout");
        return new EncodedSkillStoreCarrier(layout.blob(), layout.histories());
    }

    int storeByteCount() {
        return storeBlob.byteCount();
    }

    void copyStoreBlobInto(byte[] destination, int offset) {
        storeBlob.copyInto(destination, offset);
    }

    /** Compares the complete encoded Store payload without exposing or copying its bytes. */
    boolean matchesStoreBlob(ImmutableStoreBlob source) {
        return storeBlob.equals(Objects.requireNonNull(source, "source"));
    }

    HistoryBlobSource historySlice(EncodedHistoryIndex history) {
        requireHistoryIdentity(history);
        return storeBlob.historySlice(history.payloadOffset(), history.byteLength());
    }

    RevisionBlobSource revisionSlice(EncodedRevisionIndex revision) {
        requireRevisionIdentity(revision);
        return storeBlob.revisionSlice(revision.payloadOffset(), revision.byteLength());
    }

    StoreNbtFraming.RoutedHistorySource routedHistorySource(EncodedHistoryIndex history) {
        requireHistoryIdentity(history);
        var references = new java.util.ArrayList<SkillReference>(history.revisions().size());
        var ranges = new java.util.ArrayList<StoreNbtFraming.BlobRange>(
                history.revisions().size());
        for (var revision : history.revisions()) {
            references.add(revision.reference());
            ranges.add(StoreNbtFraming.BlobRange.fromLong(
                    Math.subtractExact(
                            (long) revision.payloadOffset(), history.payloadOffset()),
                    revision.byteLength()));
        }
        return StoreNbtFraming.bindVerifiedHistorySource(
                history.skillId(),
                history.owner(),
                storeBlob.historySlice(history.payloadOffset(), history.byteLength()),
                references,
                ranges);
    }

    StoreNbtFraming.RoutedRevisionSource routedRevisionSource(EncodedRevisionIndex revision) {
        requireRevisionIdentity(revision);
        return StoreNbtFraming.bindVerifiedRevisionSource(
                revision.reference(),
                storeBlob.revisionSlice(revision.payloadOffset(), revision.byteLength()));
    }

    List<EncodedHistoryIndex> histories() {
        return histories;
    }

    int revisionCount() {
        return revisionCount;
    }

    int historyCount() {
        return historyCount;
    }

    long totalHistoryBlobBytes() {
        return totalHistoryBlobBytes;
    }

    long totalRevisionBlobBytes() {
        return totalRevisionBlobBytes;
    }

    Optional<EncodedHistoryIndex> findHistory(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        var low = 0;
        var high = histories.size() - 1;
        while (low <= high) {
            var middle = (low + high) >>> 1;
            var candidate = histories.get(middle);
            var comparison = candidate.skillId().value().compareTo(skillId.value());
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private EncodedHistoryIndex requireHistoryIdentity(EncodedHistoryIndex history) {
        Objects.requireNonNull(history, "history");
        var current = findHistory(history.skillId());
        if (current.isEmpty() || current.orElseThrow() != history) {
            throw new IllegalArgumentException("history index does not belong to this carrier");
        }
        return history;
    }

    private void requireRevisionIdentity(EncodedRevisionIndex revision) {
        Objects.requireNonNull(revision, "revision");
        var history = findHistory(revision.reference().skillId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "revision history does not belong to this carrier"));
        var current = history.findRevision(revision.reference().revision());
        if (current.isEmpty() || current.orElseThrow() != revision) {
            throw new IllegalArgumentException("revision index does not belong to this carrier");
        }
    }

    @Override
    public String toString() {
        return "EncodedSkillStoreCarrier[storeByteCount=" + storeBlob.byteCount()
                + ", historyCount=" + historyCount
                + ", revisionCount=" + revisionCount + "]";
    }

}

/** Immutable route and Store-root range metadata for one encoded history blob. */
final class EncodedHistoryIndex {
    private final SkillId skillId;
    private final SkillOwnerId owner;
    private final int payloadOffset;
    private final int byteLength;
    private final List<EncodedRevisionIndex> revisions;

    EncodedHistoryIndex(
            SkillId skillId,
            SkillOwnerId owner,
            StoreNbtFraming.BlobRange range,
            List<EncodedRevisionIndex> revisions) {
        this(skillId, owner, range.offset(), range.length(), revisions);
    }

    EncodedHistoryIndex(
            SkillId skillId,
            SkillOwnerId owner,
            long payloadOffset,
            long byteLength,
            List<EncodedRevisionIndex> revisions) {
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.owner = Objects.requireNonNull(owner, "owner");
        var range = StoreNbtFraming.BlobRange.fromLong(payloadOffset, byteLength);
        this.payloadOffset = range.offset();
        this.byteLength = range.length();
        this.revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions"));
        if (this.revisions.isEmpty()) {
            throw new IllegalArgumentException("encoded history index must contain a revision");
        }

        SkillRevision previousRevision = null;
        long previousRevisionEnd = range.offset();
        for (var revision : this.revisions) {
            Objects.requireNonNull(revision, "revision");
            if (!skillId.equals(revision.reference().skillId())) {
                throw new IllegalArgumentException(
                        "encoded revision route must use its parent SkillId");
            }
            if (previousRevision != null
                    && previousRevision.value() >= revision.reference().revision().value()) {
                throw new IllegalArgumentException(
                        "encoded revisions must be in strict canonical revision order");
            }
            revision.range().requireWithin(range.endExclusive());
            if (revision.range().offset() < range.offset()
                    || revision.range().offset() < previousRevisionEnd) {
                throw new IllegalArgumentException(
                        "encoded revision ranges must be contained and non-overlapping");
            }
            previousRevision = revision.reference().revision();
            previousRevisionEnd = revision.range().endExclusive();
        }
    }

    SkillId skillId() {
        return skillId;
    }

    SkillOwnerId owner() {
        return owner;
    }

    StoreNbtFraming.BlobRange range() {
        return new StoreNbtFraming.BlobRange(payloadOffset, byteLength);
    }

    int payloadOffset() {
        return payloadOffset;
    }

    int byteLength() {
        return byteLength;
    }

    List<EncodedRevisionIndex> revisions() {
        return revisions;
    }

    SkillReference latestReference() {
        return revisions.getLast().reference();
    }

    Optional<EncodedRevisionIndex> findRevision(SkillRevision revision) {
        Objects.requireNonNull(revision, "revision");
        var low = 0;
        var high = revisions.size() - 1;
        while (low <= high) {
            var middle = (low + high) >>> 1;
            var candidate = revisions.get(middle);
            var comparison = Integer.compare(
                    candidate.reference().revision().value(), revision.value());
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return "EncodedHistoryIndex[skillId=" + skillId
                + ", byteCount=" + byteLength
                + ", revisionCount=" + revisions.size() + "]";
    }
}

/** Immutable route and Store-root range metadata for one encoded revision blob. */
final class EncodedRevisionIndex {
    private final SkillReference reference;
    private final int payloadOffset;
    private final int byteLength;

    EncodedRevisionIndex(
            SkillReference reference,
            StoreNbtFraming.BlobRange range) {
        this(reference, range.offset(), range.length());
    }

    EncodedRevisionIndex(
            SkillReference reference,
            long payloadOffset,
            long byteLength) {
        this.reference = Objects.requireNonNull(reference, "reference");
        var range = StoreNbtFraming.BlobRange.fromLong(payloadOffset, byteLength);
        this.payloadOffset = range.offset();
        this.byteLength = range.length();
    }

    SkillReference reference() {
        return reference;
    }

    StoreNbtFraming.BlobRange range() {
        return new StoreNbtFraming.BlobRange(payloadOffset, byteLength);
    }

    int payloadOffset() {
        return payloadOffset;
    }

    int byteLength() {
        return byteLength;
    }

    @Override
    public String toString() {
        return "EncodedRevisionIndex[reference=" + reference
                + ", byteCount=" + byteLength + "]";
    }
}
