package com.yo1no.gramarye.magic.network;

import java.util.Optional;
import java.util.OptionalLong;

final class IntentAcknowledgement {
    static final int HAS_EXPECTED_NEXT = 1 << 0;
    static final int SEQUENCE_CONSUMED = 1 << 1;
    static final int RESYNC_RECOMMENDED = 1 << 2;
    static final int ALLOWED_FLAGS =
            HAS_EXPECTED_NEXT | SEQUENCE_CONSUMED | RESYNC_RECOMMENDED;

    enum Disposition {
        ACCEPTED(0),
        REJECTED(1),
        DUPLICATE(2),
        STALE(3),
        SEQUENCE_GAP(4),
        SEQUENCE_EXHAUSTED(5),
        RATE_LIMITED(6),
        SERVER_BUSY(7),
        UNAVAILABLE(8);

        private final int semanticCode;

        Disposition(int semanticCode) {
            this.semanticCode = semanticCode;
        }

        int semanticCode() {
            return semanticCode;
        }

        static Optional<Disposition> fromSemanticCode(int rawCode) {
            return switch (rawCode) {
                case 0 -> Optional.of(ACCEPTED);
                case 1 -> Optional.of(REJECTED);
                case 2 -> Optional.of(DUPLICATE);
                case 3 -> Optional.of(STALE);
                case 4 -> Optional.of(SEQUENCE_GAP);
                case 5 -> Optional.of(SEQUENCE_EXHAUSTED);
                case 6 -> Optional.of(RATE_LIMITED);
                case 7 -> Optional.of(SERVER_BUSY);
                case 8 -> Optional.of(UNAVAILABLE);
                default -> Optional.empty();
            };
        }
    }

    private final long sequence;
    private final Disposition disposition;
    private final int flags;
    private final Long expectedNext;

    IntentAcknowledgement(
            long sequence, Disposition disposition, int flags, Long expectedNext) {
        if (disposition == null) {
            throw new P7SemanticInvariantException("acknowledgement disposition is absent");
        }
        if ((flags & ~ALLOWED_FLAGS) != 0) {
            throw new P7SemanticInvariantException("acknowledgement flags are reserved");
        }
        var hasExpectedNext = (flags & HAS_EXPECTED_NEXT) != 0;
        var consumed = (flags & SEQUENCE_CONSUMED) != 0;
        if (hasExpectedNext != (expectedNext != null)
                || expectedNext != null && expectedNext <= 0) {
            throw new P7SemanticInvariantException("expected-next invariant violated");
        }
        if (disposition == Disposition.ACCEPTED && !consumed) {
            throw new P7SemanticInvariantException("accepted acknowledgement is not consumed");
        }
        if (nonConsumedDisposition(disposition) && consumed) {
            throw new P7SemanticInvariantException("non-consumed acknowledgement is consumed");
        }
        if (requiresExpectedNext(disposition) && expectedNext == null) {
            throw new P7SemanticInvariantException("sequence repair value is absent");
        }
        if (disposition == Disposition.SEQUENCE_EXHAUSTED && expectedNext != null) {
            throw new P7SemanticInvariantException("exhausted sequence has a successor");
        }
        if ((flags & RESYNC_RECOMMENDED) != 0 && !allowsResync(disposition)) {
            throw new P7SemanticInvariantException(
                    "acknowledgement disposition cannot recommend resynchronization");
        }
        this.sequence = sequence;
        this.disposition = disposition;
        this.flags = flags;
        this.expectedNext = expectedNext;
    }

    private static boolean nonConsumedDisposition(Disposition disposition) {
        return disposition == Disposition.DUPLICATE
                || disposition == Disposition.STALE
                || disposition == Disposition.SEQUENCE_GAP
                || disposition == Disposition.SEQUENCE_EXHAUSTED
                || disposition == Disposition.RATE_LIMITED
                || disposition == Disposition.SERVER_BUSY;
    }

    private static boolean requiresExpectedNext(Disposition disposition) {
        return disposition == Disposition.DUPLICATE
                || disposition == Disposition.STALE
                || disposition == Disposition.SEQUENCE_GAP;
    }

    private static boolean allowsResync(Disposition disposition) {
        return disposition == Disposition.STALE
                || disposition == Disposition.SEQUENCE_GAP
                || disposition == Disposition.UNAVAILABLE;
    }

    static Optional<Disposition> dispositionFor(P7IntentFailureReason reason) {
        if (reason == null) {
            throw new P7SemanticInvariantException("failure reason is absent");
        }
        return switch (reason) {
            case MALFORMED_PAYLOAD,
                    PROTOCOL_VERSION_MISMATCH,
                    UNAUTHENTICATED_SENDER,
                    INTERNAL_SERVER_FAULT,
                    DISCONNECTED -> Optional.empty();
            case UNAUTHORIZED_INTENT,
                    UNKNOWN_SKILL,
                    UNKNOWN_ACTION,
                    INVALID_TARGET,
                    TARGET_UNAVAILABLE,
                    INVALID_SEQUENCE,
                    P5_ADMISSION_REJECTED -> Optional.of(Disposition.REJECTED);
            case DUPLICATE_SEQUENCE -> Optional.of(Disposition.DUPLICATE);
            case STALE_SEQUENCE -> Optional.of(Disposition.STALE);
            case SEQUENCE_GAP -> Optional.of(Disposition.SEQUENCE_GAP);
            case SEQUENCE_EXHAUSTED -> Optional.of(Disposition.SEQUENCE_EXHAUSTED);
            case RATE_LIMITED -> Optional.of(Disposition.RATE_LIMITED);
            case SERVER_BUSY -> Optional.of(Disposition.SERVER_BUSY);
            case P5_UNAVAILABLE, RELOAD_IN_PROGRESS -> Optional.of(Disposition.UNAVAILABLE);
        };
    }

    long sequence() {
        return sequence;
    }

    Disposition disposition() {
        return disposition;
    }

    int flags() {
        return flags;
    }

    OptionalLong expectedNext() {
        return expectedNext == null ? OptionalLong.empty() : OptionalLong.of(expectedNext);
    }

    int encodedBodySize() {
        return expectedNext == null ? 10 : P7NetworkBounds.ACTUAL_MAX_ACK_BODY_BYTES;
    }
}
