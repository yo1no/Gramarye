package com.yo1no.gramarye;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure, finite application of the fixed P8 degradation stages. */
final class PresentationDegradation {
    enum Stage {
        PARTICLE_REDUCTION,
        FREQUENCY_OR_SAMPLE_REDUCTION,
        SECONDARY_TRAIL_REMOVAL,
        EXACT_IDENTITY_COALESCING,
        DROP
    }

    enum Outcome {
        UNCHANGED,
        DEGRADED,
        COALESCED,
        DROPPED
    }

    record Candidate(
            int particleCount,
            int sampleIntervalTicks,
            boolean primaryTrail,
            boolean secondaryTrail) {
        Candidate {
            if (particleCount < 0
                    || particleCount > PresentationLimits.MAX_PROFILE_PARTICLE_STARTS) {
                throw new IllegalArgumentException("particle count is outside the P8 bound");
            }
            if (sampleIntervalTicks < PresentationLimits.MIN_SAMPLE_INTERVAL_TICKS
                    || sampleIntervalTicks > PresentationLimits.MAX_SAMPLE_INTERVAL_TICKS) {
                throw new IllegalArgumentException("sample interval is outside the P8 bound");
            }
            if (secondaryTrail && !primaryTrail) {
                throw new IllegalArgumentException("a secondary trail requires a primary trail");
            }
        }
    }

    record Constraints(
            long remainingParticleCredits,
            boolean suppressCurrentParticleEmission,
            int minimumSampleIntervalTicks,
            boolean removeSecondaryTrail,
            boolean remainsOverBudget) {
        Constraints {
            if (remainingParticleCredits < 0L
                    || remainingParticleCredits
                            > PresentationLimits.MAX_ACTUAL_PARTICLE_STARTS_PER_TICK) {
                throw new IllegalArgumentException(
                        "remaining particle credits are outside the P8 tick bound");
            }
            if (minimumSampleIntervalTicks < PresentationLimits.MIN_SAMPLE_INTERVAL_TICKS
                    || minimumSampleIntervalTicks > PresentationLimits.MAX_SAMPLE_INTERVAL_TICKS) {
                throw new IllegalArgumentException("minimum sample interval is outside the P8 bound");
            }
        }
    }

    static final class Decision {
        private final Outcome outcome;
        private final List<Stage> appliedStages;
        private final Optional<Candidate> candidate;

        private Decision(
                Outcome outcome, List<Stage> appliedStages, Optional<Candidate> candidate) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(appliedStages, "appliedStages");
            this.candidate = Objects.requireNonNull(candidate, "candidate");
            if (appliedStages.size() > Stage.values().length) {
                throw new IllegalArgumentException("degradation cannot exceed its fixed stage count");
            }
            this.appliedStages = List.copyOf(appliedStages);
            for (var index = 1; index < this.appliedStages.size(); index++) {
                if (this.appliedStages.get(index - 1).ordinal()
                        >= this.appliedStages.get(index).ordinal()) {
                    throw new IllegalArgumentException("degradation stages must be strictly ordered");
                }
            }
            requireConsistentOutcome();
        }

        Outcome outcome() {
            return outcome;
        }

        List<Stage> appliedStages() {
            return appliedStages;
        }

        Optional<Candidate> candidate() {
            return candidate;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Decision that
                            && outcome == that.outcome
                            && appliedStages.equals(that.appliedStages)
                            && candidate.equals(that.candidate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, appliedStages, candidate);
        }

        private void requireConsistentOutcome() {
            var last = appliedStages.isEmpty() ? null : appliedStages.getLast();
            var consistent = switch (outcome) {
                case UNCHANGED -> appliedStages.isEmpty() && candidate.isPresent();
                case DEGRADED -> !appliedStages.isEmpty()
                        && last.ordinal() <= Stage.SECONDARY_TRAIL_REMOVAL.ordinal()
                        && candidate.isPresent();
                case COALESCED -> last == Stage.EXACT_IDENTITY_COALESCING
                        && candidate.isEmpty();
                case DROPPED -> last == Stage.DROP
                        && !appliedStages.contains(Stage.EXACT_IDENTITY_COALESCING)
                        && candidate.isEmpty();
            };
            if (!consistent) {
                throw new IllegalArgumentException(
                        "degradation outcome, stages, and candidate are inconsistent");
            }
        }
    }

    private PresentationDegradation() {}

    static Decision decide(
            Candidate input,
            Constraints constraints,
            Optional<PresentationCoalescing.Decision> coalescingDecision) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(coalescingDecision, "coalescingDecision");

        var stages = new ArrayList<Stage>(Stage.values().length);
        var particleCount = input.particleCount();
        if (constraints.remainingParticleCredits() < particleCount) {
            particleCount = (int) constraints.remainingParticleCredits();
            stages.add(Stage.PARTICLE_REDUCTION);
        }

        var frequencyOrSampleReduced = false;
        if (constraints.suppressCurrentParticleEmission() && particleCount > 0) {
            particleCount = 0;
            frequencyOrSampleReduced = true;
        }

        var sampleInterval = input.sampleIntervalTicks();
        if (input.primaryTrail() && constraints.minimumSampleIntervalTicks() > sampleInterval) {
            sampleInterval = constraints.minimumSampleIntervalTicks();
            frequencyOrSampleReduced = true;
        }
        if (frequencyOrSampleReduced) {
            stages.add(Stage.FREQUENCY_OR_SAMPLE_REDUCTION);
        }

        var secondaryTrail = input.secondaryTrail();
        if (constraints.removeSecondaryTrail() && secondaryTrail) {
            secondaryTrail = false;
            stages.add(Stage.SECONDARY_TRAIL_REMOVAL);
        }

        var degraded = new Candidate(
                particleCount, sampleInterval, input.primaryTrail(), secondaryTrail);
        if (!constraints.remainsOverBudget()) {
            return new Decision(
                    stages.isEmpty() ? Outcome.UNCHANGED : Outcome.DEGRADED,
                    stages,
                    Optional.of(degraded));
        }
        if (coalescingDecision.stream().anyMatch(
                decision -> decision.outcome()
                        == PresentationCoalescing.Outcome.EXACT_IDENTITY_COALESCED)) {
            stages.add(Stage.EXACT_IDENTITY_COALESCING);
            return new Decision(Outcome.COALESCED, stages, Optional.empty());
        }
        stages.add(Stage.DROP);
        return new Decision(Outcome.DROPPED, stages, Optional.empty());
    }
}
