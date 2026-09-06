package com.yo1no.gramarye.magic.presentation;

import java.util.Objects;
import java.util.OptionalLong;

/** Pure immutable allocation state and deterministic visual-seed arithmetic. */
final class PresentationSequence {
    enum Outcome {
        ALLOCATED,
        EXHAUSTED
    }

    static final class Allocation {
        private final Outcome outcome;
        private final OptionalLong sequence;
        private final OptionalLong visualSeed;
        private final PresentationSequence nextState;

        private Allocation(
                Outcome outcome,
                OptionalLong sequence,
                OptionalLong visualSeed,
                PresentationSequence nextState) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.sequence = Objects.requireNonNull(sequence, "sequence");
            this.visualSeed = Objects.requireNonNull(visualSeed, "visualSeed");
            this.nextState = Objects.requireNonNull(nextState, "nextState");
        }

        Outcome outcome() {
            return outcome;
        }

        OptionalLong sequence() {
            return sequence;
        }

        OptionalLong visualSeed() {
            return visualSeed;
        }

        PresentationSequence nextState() {
            return nextState;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Allocation that
                            && outcome == that.outcome
                            && sequence.equals(that.sequence)
                            && visualSeed.equals(that.visualSeed)
                            && nextState.equals(that.nextState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, sequence, visualSeed, nextState);
        }
    }

    private final long nextValue;
    private final boolean exhausted;

    private PresentationSequence(long nextValue, boolean exhausted) {
        if (nextValue < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("next presentation sequence must be positive");
        }
        this.nextValue = nextValue;
        this.exhausted = exhausted;
    }

    static PresentationSequence initial() {
        return expecting(PresentationLimits.MIN_SEQUENCE);
    }

    static PresentationSequence expecting(long nextValue) {
        if (nextValue < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("next presentation sequence must be positive");
        }
        return new PresentationSequence(nextValue, false);
    }

    OptionalLong nextValue() {
        return exhausted ? OptionalLong.empty() : OptionalLong.of(nextValue);
    }

    boolean exhausted() {
        return exhausted;
    }

    Allocation allocate() {
        if (exhausted) {
            return new Allocation(Outcome.EXHAUSTED, OptionalLong.empty(), OptionalLong.empty(), this);
        }

        var nextState = nextValue == PresentationLimits.MAX_SEQUENCE
                ? new PresentationSequence(PresentationLimits.MAX_SEQUENCE, true)
                : new PresentationSequence(nextValue + 1L, false);
        return new Allocation(
                Outcome.ALLOCATED,
                OptionalLong.of(nextValue),
                OptionalLong.of(visualSeed(nextValue)),
                nextState);
    }

    static long visualSeed(long sequence) {
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("presentation sequence must be positive");
        }
        var value = 0x4752414D41525945L ^ sequence;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PresentationSequence that
                        && nextValue == that.nextValue
                        && exhausted == that.exhausted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nextValue, exhausted);
    }
}
