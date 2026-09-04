package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.OptionalLong;

/** Immutable exact-next P7 intent-sequence state machine. */
final class IntentSequenceState {
    enum Classification {
        ACCEPTED,
        DUPLICATE,
        STALE,
        GAP,
        INVALID,
        EXHAUSTED
    }

    private final long expectedSequence;
    private final boolean exhausted;

    private IntentSequenceState(long expectedSequence, boolean exhausted) {
        this.expectedSequence = expectedSequence;
        this.exhausted = exhausted;
    }

    static IntentSequenceState initial() {
        return expecting(P7NetworkBounds.NETWORK_SEQUENCE_MIN);
    }

    static IntentSequenceState expecting(long expectedSequence) {
        if (expectedSequence < P7NetworkBounds.NETWORK_SEQUENCE_MIN
                || expectedSequence > P7NetworkBounds.NETWORK_SEQUENCE_MAX) {
            throw new P7SemanticInvariantException(
                    "expected intent sequence is outside the positive range");
        }
        return new IntentSequenceState(expectedSequence, false);
    }

    boolean exhausted() {
        return exhausted;
    }

    OptionalLong expectedNext() {
        return exhausted ? OptionalLong.empty() : OptionalLong.of(expectedSequence);
    }

    Decision evaluate(long receivedSequence) {
        if (exhausted) {
            return Decision.rejected(Classification.EXHAUSTED, this);
        }
        if (receivedSequence < P7NetworkBounds.NETWORK_SEQUENCE_MIN) {
            return Decision.rejected(Classification.INVALID, this);
        }
        if (receivedSequence == expectedSequence) {
            var nextState = expectedSequence == P7NetworkBounds.NETWORK_SEQUENCE_MAX
                    ? new IntentSequenceState(P7NetworkBounds.NETWORK_SEQUENCE_MAX, true)
                    : new IntentSequenceState(expectedSequence + 1L, false);
            return Decision.accepted(nextState);
        }
        if (expectedSequence > P7NetworkBounds.NETWORK_SEQUENCE_MIN
                && receivedSequence == expectedSequence - 1L) {
            return Decision.rejected(Classification.DUPLICATE, this);
        }
        if (receivedSequence < expectedSequence) {
            return Decision.rejected(Classification.STALE, this);
        }
        return Decision.rejected(Classification.GAP, this);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof IntentSequenceState that
                        && expectedSequence == that.expectedSequence
                        && exhausted == that.exhausted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(expectedSequence, exhausted);
    }

    static final class Decision {
        private final Classification classification;
        private final boolean sequenceConsumed;
        private final IntentSequenceState nextState;
        private final OptionalLong expectedNext;

        private Decision(
                Classification classification,
                boolean sequenceConsumed,
                IntentSequenceState nextState) {
            this.classification = Objects.requireNonNull(classification, "classification");
            this.sequenceConsumed = sequenceConsumed;
            this.nextState = Objects.requireNonNull(nextState, "nextState");
            this.expectedNext = nextState.expectedNext();
        }

        private static Decision accepted(IntentSequenceState nextState) {
            return new Decision(Classification.ACCEPTED, true, nextState);
        }

        private static Decision rejected(
                Classification classification, IntentSequenceState unchangedState) {
            if (classification == Classification.ACCEPTED) {
                throw new P7SemanticInvariantException(
                        "an accepted sequence decision cannot be rejected");
            }
            return new Decision(classification, false, unchangedState);
        }

        Classification classification() {
            return classification;
        }

        boolean sequenceConsumed() {
            return sequenceConsumed;
        }

        IntentSequenceState nextState() {
            return nextState;
        }

        OptionalLong expectedNext() {
            return expectedNext;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Decision that
                            && sequenceConsumed == that.sequenceConsumed
                            && classification == that.classification
                            && nextState.equals(that.nextState)
                            && expectedNext.equals(that.expectedNext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classification, sequenceConsumed, nextState, expectedNext);
        }
    }
}
