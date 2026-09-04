package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Exhaustive network-local mapping from S1/root outcomes to immutable S3 results. */
final class P7AdmissionDispositionMapper {
    private P7AdmissionDispositionMapper() {
        throw new AssertionError("no instances");
    }

    static P7ServerIntentResult fromAdmissionSemantics(
            P7SessionIdentity identity,
            long receivedSequence,
            CastIntentAdmissionSemantics.Decision decision) {
        Objects.requireNonNull(decision, "decision");
        return switch (decision.outcome()) {
            case SERVER_BUSY -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.SERVER_BUSY,
                    IntentAcknowledgement.Disposition.SERVER_BUSY,
                    decision.expectedNext(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    false);
            case RATE_LIMITED -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.RATE_LIMITED,
                    IntentAcknowledgement.Disposition.RATE_LIMITED,
                    decision.expectedNext(),
                    false,
                    decision.disconnect(),
                    false,
                    true,
                    false,
                    false);
            case INVALID_SEQUENCE -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.INVALID_SEQUENCE,
                    IntentAcknowledgement.Disposition.REJECTED,
                    OptionalLong.empty(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    false);
            case DUPLICATE_SEQUENCE -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.DUPLICATE_SEQUENCE,
                    IntentAcknowledgement.Disposition.DUPLICATE,
                    decision.expectedNext(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    false);
            case STALE_SEQUENCE -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.STALE_SEQUENCE,
                    IntentAcknowledgement.Disposition.STALE,
                    decision.expectedNext(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    true);
            case SEQUENCE_GAP -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.SEQUENCE_GAP,
                    IntentAcknowledgement.Disposition.SEQUENCE_GAP,
                    decision.expectedNext(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    true);
            case SEQUENCE_EXHAUSTED -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.SEQUENCE_EXHAUSTED,
                    IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED,
                    OptionalLong.empty(),
                    false,
                    false,
                    false,
                    true,
                    false,
                    false);
            case INTERNAL_SERVER_FAULT -> withoutAcknowledgement(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.INTERNAL_SERVER_FAULT,
                    false,
                    false,
                    false,
                    false);
            case ELIGIBLE -> throw new P7SemanticInvariantException(
                    "eligible admission requires root authorization");
        };
    }

    static P7ServerIntentResult fromRootDisposition(
            P7SessionIdentity identity,
            long receivedSequence,
            P7ServerAuthorizationBoundary.AdmissionDisposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        return switch (disposition) {
            case ACCEPTED -> accepted(identity, receivedSequence);
            case UNKNOWN_SKILL -> consumedRejection(
                    identity, receivedSequence, P7IntentFailureReason.UNKNOWN_SKILL, false);
            case UNAUTHORIZED_INTENT -> consumedRejection(
                    identity, receivedSequence, P7IntentFailureReason.UNAUTHORIZED_INTENT, false);
            case INVALID_TARGET -> consumedRejection(
                    identity, receivedSequence, P7IntentFailureReason.INVALID_TARGET, false);
            case TARGET_UNAVAILABLE -> consumedRejection(
                    identity, receivedSequence, P7IntentFailureReason.TARGET_UNAVAILABLE, false);
            case P5_ADMISSION_REJECTED -> consumedRejection(
                    identity, receivedSequence, P7IntentFailureReason.P5_ADMISSION_REJECTED, true);
            case P5_UNAVAILABLE -> result(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.P5_UNAVAILABLE,
                    IntentAcknowledgement.Disposition.UNAVAILABLE,
                    OptionalLong.empty(),
                    true,
                    false,
                    false,
                    false,
                    false,
                    true);
            case INTERNAL_SERVER_FAULT -> withoutAcknowledgement(
                    identity,
                    receivedSequence,
                    P7IntentFailureReason.INTERNAL_SERVER_FAULT,
                    true,
                    false,
                    true,
                    false);
        };
    }

    static P7ServerIntentResult reloadInProgress(
            P7SessionIdentity identity, long receivedSequence, OptionalLong expectedNext) {
        return result(
                identity,
                receivedSequence,
                P7IntentFailureReason.RELOAD_IN_PROGRESS,
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                expectedNext,
                false,
                false,
                false,
                true,
                false,
                false);
    }

    static P7ServerIntentResult unauthorizedAfterConsumption(
            P7SessionIdentity identity, long receivedSequence) {
        return consumedRejection(
                identity, receivedSequence, P7IntentFailureReason.UNAUTHORIZED_INTENT, false);
    }

    static P7ServerIntentResult disconnected(
            P7SessionIdentity identity, long receivedSequence, boolean sequenceConsumed) {
        return withoutAcknowledgement(
                identity,
                receivedSequence,
                P7IntentFailureReason.DISCONNECTED,
                sequenceConsumed,
                false,
                false,
                false);
    }

    static P7ServerIntentResult serverUnavailable(
            P7SessionIdentity identity,
            long receivedSequence,
            OptionalLong expectedNext) {
        return result(
                identity,
                receivedSequence,
                P7IntentFailureReason.P5_UNAVAILABLE,
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                expectedNext,
                false,
                false,
                false,
                true,
                false,
                true);
    }

    private static P7ServerIntentResult accepted(
            P7SessionIdentity identity, long receivedSequence) {
        return new P7ServerIntentResult(
                identity,
                receivedSequence,
                Optional.empty(),
                Optional.of(new IntentAcknowledgement(
                        receivedSequence,
                        IntentAcknowledgement.Disposition.ACCEPTED,
                        IntentAcknowledgement.SEQUENCE_CONSUMED,
                        null)),
                true,
                false,
                true,
                true,
                true);
    }

    private static P7ServerIntentResult consumedRejection(
            P7SessionIdentity identity,
            long receivedSequence,
            P7IntentFailureReason reason,
            boolean p5Attempted) {
        return result(
                identity,
                receivedSequence,
                reason,
                IntentAcknowledgement.Disposition.REJECTED,
                OptionalLong.empty(),
                true,
                false,
                p5Attempted,
                true,
                false,
                false);
    }

    private static P7ServerIntentResult withoutAcknowledgement(
            P7SessionIdentity identity,
            long receivedSequence,
            P7IntentFailureReason reason,
            boolean sequenceConsumed,
            boolean disconnectRequested,
            boolean p5Attempted,
            boolean p5Accepted) {
        return new P7ServerIntentResult(
                identity,
                receivedSequence,
                Optional.of(reason),
                Optional.empty(),
                sequenceConsumed,
                disconnectRequested,
                p5Attempted,
                true,
                p5Accepted);
    }

    private static P7ServerIntentResult result(
            P7SessionIdentity identity,
            long receivedSequence,
            P7IntentFailureReason reason,
            IntentAcknowledgement.Disposition disposition,
            OptionalLong expectedNext,
            boolean sequenceConsumed,
            boolean disconnectRequested,
            boolean p5Attempted,
            boolean p5AttemptKnown,
            boolean p5Accepted,
            boolean resyncRecommended) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(expectedNext, "expectedNext");
        var flags = sequenceConsumed ? IntentAcknowledgement.SEQUENCE_CONSUMED : 0;
        if (expectedNext.isPresent()) {
            flags |= IntentAcknowledgement.HAS_EXPECTED_NEXT;
        }
        if (resyncRecommended) {
            flags |= IntentAcknowledgement.RESYNC_RECOMMENDED;
        }
        return new P7ServerIntentResult(
                identity,
                receivedSequence,
                Optional.of(reason),
                Optional.of(new IntentAcknowledgement(
                        receivedSequence,
                        disposition,
                        flags,
                        expectedNext.isPresent() ? expectedNext.getAsLong() : null)),
                sequenceConsumed,
                disconnectRequested,
                p5Attempted,
                p5AttemptKnown,
                p5Accepted);
    }
}
