package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillCommitPrecondition;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure P4-A3-A construction operations for the derived encoded Store carrier. */
final class SkillStoreCarrierBuilder {
    private SkillStoreCarrierBuilder() {
    }

    static CarrierBuildResult rebuild(SkillDefinitionStore store) {
        Objects.requireNonNull(store, "store");
        try {
            var encoded = SkillDefinitionStorePersistenceBridge.encodeCurrentStoreLayout(
                    store.snapshot());
            if (encoded instanceof StoreLayoutEncodeResult.Failure failure) {
                return new CarrierBuildResult.Failure(failure.failure());
            }
            var success = (StoreLayoutEncodeResult.Success) encoded;
            return new CarrierBuildResult.Success(
                    EncodedSkillStoreCarrier.fromLayout(success.layout()));
        } catch (RuntimeException exception) {
            if (exception instanceof CarrierInvariantException invariant) {
                throw invariant;
            }
            throw invariant(CarrierInvariantException.Code.LAYOUT_INVALID);
        }
    }

    static CarrierUpdateResult prepareProspectiveUpdate(
            EncodedSkillStoreCarrier base,
            SkillSubmissionPlan plan) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(plan, "plan");
        var owner = Objects.requireNonNull(plan.owner(), "plan.owner");
        var precondition = Objects.requireNonNull(plan.precondition(), "plan.precondition");
        var document = Objects.requireNonNull(
                plan.proposedDocument(), "plan.proposedDocument");
        requirePlanShape(precondition, document);

        return switch (precondition) {
            case SkillCommitPrecondition.ExpectedAbsent expected ->
                    prepareNew(base, owner, expected, document);
            case SkillCommitPrecondition.ExpectedLatest expected ->
                    prepareExisting(base, owner, expected, document);
        };
    }

    /**
     * Filters encoded revisions using the immediate post-reclaim snapshot.
     *
     * <p>For a legal P3-D reclaim subset this operation is total: it performs no document encode
     * and has no ordinary typed-failure branch. A mismatch means the derived carrier and domain
     * snapshot were not paired by their future lifecycle adapter.</p>
     */
    static EncodedSkillStoreCarrier filterAfterReclaim(
            EncodedSkillStoreCarrier base,
            SkillDefinitionStoreSnapshot postReclaimSnapshot) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(postReclaimSnapshot, "postReclaimSnapshot");
        var snapshots = postReclaimSnapshot.histories();
        if (snapshots.size() != base.historyCount()) {
            throw invariant(CarrierInvariantException.Code.RECLAIM_HISTORY_SET_MISMATCH);
        }

        var parts = new ArrayList<StoreNbtFraming.RoutedHistorySource>(snapshots.size());
        var expectedStoreSize = (long) base.storeByteCount();
        for (var historyIndex = 0; historyIndex < snapshots.size(); historyIndex++) {
            var snapshot = snapshots.get(historyIndex);
            var encoded = base.histories().get(historyIndex);
            if (!snapshot.skillId().equals(encoded.skillId())) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_HISTORY_SET_MISMATCH);
            }
            if (!snapshot.owner().equals(encoded.owner())) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_OWNER_MISMATCH);
            }

            var retained = snapshot.revisions();
            if (retained.isEmpty()) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_EMPTY_HISTORY);
            }
            var retainedIndexes = new ArrayList<EncodedRevisionIndex>(retained.size());
            var previousRevision = -1;
            for (var revision : retained) {
                var value = revision.revision().value();
                if (value <= previousRevision) {
                    throw invariant(CarrierInvariantException.Code.RECLAIM_REVISION_ORDER_INVALID);
                }
                previousRevision = value;
                retainedIndexes.add(encoded.findRevision(revision.revision())
                        .orElseThrow(() -> invariant(
                                CarrierInvariantException.Code.RECLAIM_ROUTE_NOT_IN_BASE)));
            }
            if (!retainedIndexes.getLast().reference().equals(encoded.latestReference())) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_LATEST_MISSING);
            }

            if (sameRevisionRoutes(encoded.revisions(), retainedIndexes)) {
                parts.add(base.routedHistorySource(encoded));
                continue;
            }

            var sources = retainedIndexes.stream().map(base::routedRevisionSource).toList();
            var framed = StoreNbtFraming.encodeHistoryWithLayout(
                    encoded.skillId(), encoded.owner(), sources);
            if (framed.failureValue().isPresent()) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_ENCODING_FAILED);
            }
            var frame = framed.successValue().orElseThrow();
            var expectedHistorySize = expectedFilteredHistorySize(encoded, retainedIndexes);
            if (frame.blob().byteCount() != expectedHistorySize) {
                throw invariant(CarrierInvariantException.Code.RECLAIM_SIZE_MISMATCH);
            }
            expectedStoreSize = checkedSubtract(
                    expectedStoreSize,
                    checkedSubtract((long) encoded.byteLength(), expectedHistorySize));
            parts.add(frame);
        }

        var assembled = assemble(parts);
        if (assembled instanceof CarrierBuildResult.Failure) {
            throw invariant(CarrierInvariantException.Code.RECLAIM_ENCODING_FAILED);
        }
        var filtered = ((CarrierBuildResult.Success) assembled).carrier();
        if (filtered.storeByteCount() != expectedStoreSize
                || filtered.storeByteCount() > base.storeByteCount()) {
            throw invariant(CarrierInvariantException.Code.RECLAIM_SIZE_MISMATCH);
        }
        return filtered;
    }

    private static CarrierUpdateResult prepareNew(
            EncodedSkillStoreCarrier base,
            SkillOwnerId owner,
            SkillCommitPrecondition.ExpectedAbsent expected,
            SkillDocument document) {
        if (base.findHistory(expected.skillId()).isPresent()) {
            throw invariant(CarrierInvariantException.Code.EXPECTED_ABSENT_BASE_PRESENT);
        }
        if (document.revision().value() != 0) {
            throw invariant(CarrierInvariantException.Code.PLAN_SHAPE_INVALID);
        }

        var revisionResult = SkillDefinitionStorePersistenceBridge.encodeCurrentRevision(document);
        if (revisionResult.failureValue().isPresent()) {
            return updateFailure(revisionResult.failureValue().orElseThrow());
        }
        var revision = revisionResult.successValue().orElseThrow();
        var historyResult = StoreNbtFraming.encodeHistoryWithLayout(
                document.skillId(), owner, List.of(revision));
        if (historyResult.failureValue().isPresent()) {
            return updateFailure(historyResult.failureValue().orElseThrow());
        }
        var history = historyResult.successValue().orElseThrow();
        var proposedReference = referenceOf(document);
        var newPart = history;

        var parts = new ArrayList<StoreNbtFraming.RoutedHistorySource>(
                base.historyCount() + 1);
        var inserted = false;
        for (var existing : base.histories()) {
            if (!inserted
                    && document.skillId().value().compareTo(existing.skillId().value()) < 0) {
                parts.add(newPart);
                inserted = true;
            }
            parts.add(base.routedHistorySource(existing));
        }
        if (!inserted) {
            parts.add(newPart);
        }
        return preparedUpdate(base, proposedReference, CarrierUpdateKind.NEW, assemble(parts));
    }

    private static CarrierUpdateResult prepareExisting(
            EncodedSkillStoreCarrier base,
            SkillOwnerId owner,
            SkillCommitPrecondition.ExpectedLatest expected,
            SkillDocument document) {
        var encoded = base.findHistory(expected.skillId())
                .orElseThrow(() -> invariant(
                        CarrierInvariantException.Code.EXPECTED_LATEST_BASE_ABSENT));
        if (!encoded.owner().equals(owner)) {
            throw invariant(CarrierInvariantException.Code.EXPECTED_LATEST_OWNER_MISMATCH);
        }
        if (!encoded.latestReference().equals(expected.latest())) {
            throw invariant(CarrierInvariantException.Code.EXPECTED_LATEST_BASE_MISMATCH);
        }
        var proposedReference = referenceOf(document);
        if (encoded.findRevision(proposedReference.revision()).isPresent()) {
            throw invariant(CarrierInvariantException.Code.PROPOSED_REVISION_PRESENT);
        }

        var revisionResult = SkillDefinitionStorePersistenceBridge.encodeCurrentRevision(document);
        if (revisionResult.failureValue().isPresent()) {
            return updateFailure(revisionResult.failureValue().orElseThrow());
        }
        var newRevision = revisionResult.successValue().orElseThrow();
        var revisionSources = new ArrayList<StoreNbtFraming.RoutedRevisionSource>(
                encoded.revisions().size() + 1);
        for (var revision : encoded.revisions()) {
            revisionSources.add(base.routedRevisionSource(revision));
        }
        revisionSources.add(newRevision);

        var historyResult = StoreNbtFraming.encodeHistoryWithLayout(
                encoded.skillId(), encoded.owner(), revisionSources);
        if (historyResult.failureValue().isPresent()) {
            return updateFailure(historyResult.failureValue().orElseThrow());
        }
        var history = historyResult.successValue().orElseThrow();
        var replacement = history;

        var parts = new ArrayList<StoreNbtFraming.RoutedHistorySource>(base.historyCount());
        for (var current : base.histories()) {
            parts.add(current == encoded ? replacement : base.routedHistorySource(current));
        }
        return preparedUpdate(
                base, proposedReference, CarrierUpdateKind.EXISTING, assemble(parts));
    }

    private static CarrierUpdateResult preparedUpdate(
            EncodedSkillStoreCarrier base,
            SkillReference proposedReference,
            CarrierUpdateKind kind,
            CarrierBuildResult buildResult) {
        return switch (buildResult) {
            case CarrierBuildResult.Success success ->
                    new CarrierUpdateResult.Prepared(new PreparedCarrierUpdate(
                            base, success.carrier(), proposedReference, kind));
            case CarrierBuildResult.Failure failure ->
                    new CarrierUpdateResult.Failure(failure.failure());
        };
    }

    private static CarrierBuildResult assemble(
            List<? extends StoreNbtFraming.RoutedHistorySource> parts) {
        var immutableParts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        SkillId previous = null;
        for (var part : immutableParts) {
            Objects.requireNonNull(part, "part");
            if (previous != null && previous.value().compareTo(part.skillId().value()) >= 0) {
                throw invariant(CarrierInvariantException.Code.HISTORY_ORDER_INVALID);
            }
            previous = part.skillId();
        }

        StoreNbtFraming.FramingResult<StoreNbtFraming.EncodedStoreFrame> storeResult;
        try {
            storeResult = StoreNbtFraming.encodeStoreWithLayout(
                    StorePersistenceSchema.CURRENT_SCHEMA_VERSION, immutableParts);
        } catch (RuntimeException exception) {
            if (exception instanceof CarrierInvariantException invariant) {
                throw invariant;
            }
            throw invariant(CarrierInvariantException.Code.LAYOUT_INVALID);
        }
        if (storeResult.failureValue().isPresent()) {
            return new CarrierBuildResult.Failure(storeResult.failureValue().orElseThrow());
        }
        var frame = storeResult.successValue().orElseThrow();
        try {
            return new CarrierBuildResult.Success(EncodedSkillStoreCarrier.fromLayout(
                    StoreEncodingLayout.fromWriterFrame(frame)));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw invariant(CarrierInvariantException.Code.LAYOUT_INVALID);
        }
    }

    private static long expectedFilteredHistorySize(
            EncodedHistoryIndex history,
            List<EncodedRevisionIndex> retained) {
        var retainedReferences = retained.stream()
                .map(EncodedRevisionIndex::reference)
                .toList();
        var removedBytes = 0L;
        for (var revision : history.revisions()) {
            if (!retainedReferences.contains(revision.reference())) {
                removedBytes = checkedAdd(
                        removedBytes, checkedAdd(4L, revision.byteLength()));
            }
        }
        return checkedSubtract((long) history.byteLength(), removedBytes);
    }

    private static boolean sameRevisionRoutes(
            List<EncodedRevisionIndex> encoded,
            List<EncodedRevisionIndex> retained) {
        if (encoded.size() != retained.size()) {
            return false;
        }
        for (var index = 0; index < encoded.size(); index++) {
            if (!encoded.get(index).reference().equals(retained.get(index).reference())) {
                return false;
            }
        }
        return true;
    }

    private static void requirePlanShape(
            SkillCommitPrecondition precondition,
            SkillDocument document) {
        if (!precondition.skillId().equals(document.skillId())
                || document.schemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION
                || document.nodes().isEmpty()) {
            throw invariant(CarrierInvariantException.Code.PLAN_SHAPE_INVALID);
        }
        if (precondition instanceof SkillCommitPrecondition.ExpectedLatest expected) {
            var successor = expected.latest().revision().successor()
                    .orElseThrow(() -> invariant(
                            CarrierInvariantException.Code.PLAN_SHAPE_INVALID));
            if (!successor.equals(document.revision())) {
                throw invariant(CarrierInvariantException.Code.PLAN_SHAPE_INVALID);
            }
        }
    }

    private static SkillReference referenceOf(SkillDocument document) {
        return new SkillReference(document.skillId(), document.revision());
    }

    private static CarrierUpdateResult.Failure updateFailure(
            StorePersistenceFailure failure) {
        return new CarrierUpdateResult.Failure(failure);
    }

    private static CarrierInvariantException invariant(CarrierInvariantException.Code code) {
        return new CarrierInvariantException(code);
    }

    private static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw invariant(CarrierInvariantException.Code.LAYOUT_INVALID);
        }
    }

    private static long checkedSubtract(long left, long right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException exception) {
            throw invariant(CarrierInvariantException.Code.LAYOUT_INVALID);
        }
    }

}

sealed interface CarrierBuildResult
        permits CarrierBuildResult.Success, CarrierBuildResult.Failure {
    record Success(EncodedSkillStoreCarrier carrier) implements CarrierBuildResult {
        public Success {
            Objects.requireNonNull(carrier, "carrier");
        }
    }

    record Failure(StorePersistenceFailure failure) implements CarrierBuildResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

sealed interface CarrierUpdateResult
        permits CarrierUpdateResult.Prepared, CarrierUpdateResult.Failure {
    record Prepared(PreparedCarrierUpdate update) implements CarrierUpdateResult {
        public Prepared {
            Objects.requireNonNull(update, "update");
        }
    }

    record Failure(StorePersistenceFailure failure) implements CarrierUpdateResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

/** Fixed machine-readable adapter/programming-invariant failure. */
final class CarrierInvariantException extends IllegalStateException {
    private final Code code;

    CarrierInvariantException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    Code code() {
        return code;
    }

    enum Code {
        PLAN_SHAPE_INVALID,
        EXPECTED_ABSENT_BASE_PRESENT,
        EXPECTED_LATEST_BASE_ABSENT,
        EXPECTED_LATEST_OWNER_MISMATCH,
        EXPECTED_LATEST_BASE_MISMATCH,
        PROPOSED_REVISION_PRESENT,
        HISTORY_ORDER_INVALID,
        LAYOUT_INVALID,
        RECLAIM_HISTORY_SET_MISMATCH,
        RECLAIM_OWNER_MISMATCH,
        RECLAIM_EMPTY_HISTORY,
        RECLAIM_REVISION_ORDER_INVALID,
        RECLAIM_ROUTE_NOT_IN_BASE,
        RECLAIM_LATEST_MISSING,
        RECLAIM_ENCODING_FAILED,
        RECLAIM_SIZE_MISMATCH
    }
}
