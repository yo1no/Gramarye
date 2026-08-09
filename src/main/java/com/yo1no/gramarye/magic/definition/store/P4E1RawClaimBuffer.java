package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.Objects;

/** Sole progressive segmented backing for unpublished P4-E1-B1 raw root claims. */
final class P4E1RawClaimBuffer {
    private static final int SEGMENT_LENGTH = 256;

    private ArrayList<Segment> segments = new ArrayList<>();
    private int size;
    private boolean discarded;
    private boolean reservationOutstanding;

    ReservationResult reserve(
            P4E1AuditBudget budget, int sourceTableIndex, int rootCount) {
        requireOpen();
        Objects.requireNonNull(budget, "budget");
        if (sourceTableIndex < 0 || rootCount < 0) {
            throw new IllegalArgumentException("source index and root count must be non-negative");
        }
        if (reservationOutstanding) {
            throw new IllegalStateException("P4E1_RAW_CLAIM_RESERVATION_OUTSTANDING");
        }
        var exceeded = budget.checkpointRawRootClaims(
                P4E1AuditStage.RAW_ROOT_CAPTURE, rootCount);
        if (exceeded.isPresent()) {
            return new ReservationResult.OverLimit(exceeded.orElseThrow());
        }
        reservationOutstanding = true;
        return new ReservationResult.Reserved(
                new Reservation(this, sourceTableIndex, size, rootCount));
    }

    int size() {
        requireOpen();
        return size;
    }

    SkillReference referenceAt(int index) {
        return segment(index).references[index % SEGMENT_LENGTH];
    }

    int sourceTableIndexAt(int index) {
        return segment(index).sourceTableIndexes[index % SEGMENT_LENGTH];
    }

    int sourceLocalOrdinalAt(int index) {
        return segment(index).sourceLocalOrdinals[index % SEGMENT_LENGTH];
    }

    ClaimKind claimKindAt(int index) {
        return segment(index).claimKinds[index % SEGMENT_LENGTH];
    }

    int equippedSlotAt(int index) {
        return segment(index).equippedSlots[index % SEGMENT_LENGTH];
    }

    void discard() {
        requireOpen();
        discarded = true;
        for (var segment : segments) {
            segment.clear();
        }
        segments.clear();
        segments = null;
        size = 0;
        reservationOutstanding = false;
    }

    private void append(
            int sourceTableIndex,
            int sourceLocalOrdinal,
            ClaimKind kind,
            int equippedSlot,
            SkillReference reference) {
        requireOpen();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(reference, "reference");
        if (sourceTableIndex < 0 || sourceLocalOrdinal < 0 || equippedSlot < -1) {
            throw new IllegalArgumentException("invalid raw-root claim metadata");
        }
        var segmentIndex = size / SEGMENT_LENGTH;
        if (segmentIndex == segments.size()) {
            segments.add(new Segment());
        }
        var offset = size % SEGMENT_LENGTH;
        var segment = segments.get(segmentIndex);
        segment.references[offset] = reference;
        segment.sourceTableIndexes[offset] = sourceTableIndex;
        segment.sourceLocalOrdinals[offset] = sourceLocalOrdinal;
        segment.claimKinds[offset] = kind;
        segment.equippedSlots[offset] = equippedSlot;
        size++;
    }

    private Segment segment(int index) {
        requireOpen();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
        return segments.get(index / SEGMENT_LENGTH);
    }

    private void requireOpen() {
        if (discarded || segments == null) {
            throw new IllegalStateException("P4E1_RAW_CLAIM_BUFFER_DISCARDED");
        }
    }

    private void finishReservation() {
        if (!reservationOutstanding) {
            throw new IllegalStateException("P4E1_RAW_CLAIM_RESERVATION_MISSING");
        }
        reservationOutstanding = false;
    }

    enum ClaimKind {
        PLAYER_LATEST,
        PLAYER_EQUIPPED,
        JOURNAL_TARGET
    }

    sealed interface ReservationResult {
        record Reserved(Reservation reservation) implements ReservationResult {
            public Reserved {
                Objects.requireNonNull(reservation, "reservation");
            }
        }

        record OverLimit(P4E1AuditBudget.Exceeded exceeded) implements ReservationResult {
            public OverLimit {
                Objects.requireNonNull(exceeded, "exceeded");
            }
        }
    }

    static final class Reservation {
        private P4E1RawClaimBuffer owner;
        private final int sourceTableIndex;
        private final int claimStart;
        private final int expectedCount;
        private int appended;
        private boolean finished;

        private Reservation(
                P4E1RawClaimBuffer owner,
                int sourceTableIndex,
                int claimStart,
                int expectedCount) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.sourceTableIndex = sourceTableIndex;
            this.claimStart = claimStart;
            this.expectedCount = expectedCount;
        }

        void appendLatest(SkillReference reference) {
            append(P4E1RawClaimBuffer.ClaimKind.PLAYER_LATEST, -1, reference);
        }

        void appendEquipped(int slot, SkillReference reference) {
            if (slot < 0) {
                throw new IllegalArgumentException("equipped slot must be non-negative");
            }
            append(P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED, slot, reference);
        }

        void appendJournal(SkillReference reference) {
            append(P4E1RawClaimBuffer.ClaimKind.JOURNAL_TARGET, -1, reference);
        }

        void finish() {
            requireActive();
            if (appended != expectedCount || owner.size != claimStart + expectedCount) {
                owner.finishReservation();
                owner = null;
                finished = true;
                throw new IllegalStateException("P4E1_RAW_CLAIM_DRAIN_COUNT_MISMATCH");
            }
            owner.finishReservation();
            owner = null;
            finished = true;
        }

        int claimStart() {
            return claimStart;
        }

        int expectedCount() {
            return expectedCount;
        }

        private void append(ClaimKind kind, int slot, SkillReference reference) {
            requireActive();
            if (appended >= expectedCount) {
                throw new IllegalStateException("P4E1_RAW_CLAIM_DRAIN_COUNT_MISMATCH");
            }
            owner.append(sourceTableIndex, appended, kind, slot, reference);
            appended++;
        }

        private void requireActive() {
            if (finished || owner == null) {
                throw new IllegalStateException("P4E1_RAW_CLAIM_RESERVATION_CONSUMED");
            }
        }
    }

    private static final class Segment {
        private final SkillReference[] references = new SkillReference[SEGMENT_LENGTH];
        private final int[] sourceTableIndexes = new int[SEGMENT_LENGTH];
        private final int[] sourceLocalOrdinals = new int[SEGMENT_LENGTH];
        private final ClaimKind[] claimKinds = new ClaimKind[SEGMENT_LENGTH];
        private final int[] equippedSlots = new int[SEGMENT_LENGTH];

        private void clear() {
            java.util.Arrays.fill(references, null);
            java.util.Arrays.fill(claimKinds, null);
        }
    }
}
