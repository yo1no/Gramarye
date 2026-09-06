package com.yo1no.gramarye.magic.presentation;

import java.util.Objects;
import java.util.OptionalLong;

/** Pure immutable transition for one explicitly scoped P8 count/byte budget. */
final class PresentationBudget {
    enum Scope {
        LOGICAL_EVENTS_PER_SKILL(
                PresentationLimits.MAX_LOGICAL_EVENTS_PER_SKILL_PER_TICK, null),
        LOGICAL_EVENTS_PER_SOURCE(
                PresentationLimits.MAX_LOGICAL_EVENTS_PER_SOURCE_PER_TICK, null),
        LOGICAL_EVENTS_PER_SERVER(
                PresentationLimits.MAX_LOGICAL_EVENTS_PER_SERVER_PER_TICK, null),
        SERVER_EVENT_BUFFER(
                PresentationLimits.MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS,
                PresentationLimits.MAX_SERVER_EVENT_BODY_BYTES_PER_TICK),
        DELIVERIES_PER_PLAYER(
                PresentationLimits.MAX_DELIVERIES_PER_PLAYER_PER_TICK,
                PresentationLimits.MAX_DELIVERY_BYTES_PER_PLAYER_PER_TICK),
        DELIVERIES_PER_SERVER(
                PresentationLimits.MAX_DELIVERIES_PER_SERVER_PER_TICK,
                PresentationLimits.MAX_DELIVERY_BYTES_PER_SERVER_PER_TICK),
        CLIENT_PENDING_EVENTS(
                PresentationLimits.MAX_CLIENT_PENDING_EVENTS,
                PresentationLimits.MAX_CLIENT_PENDING_EVENT_BODY_BYTES),
        CLIENT_COMBINED_QUEUE(null, PresentationLimits.MAX_CLIENT_COMBINED_QUEUED_BYTES),
        CLIENT_EVENT_APPLICATION(
                PresentationLimits.MAX_CLIENT_EVENT_APPLICATIONS_PER_TICK, null),
        CLIENT_ACTIVE_PRESENTATIONS(
                PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS, null),
        ONLINE_RECIPIENT_SCAN(
                PresentationLimits.MAX_ONLINE_RECIPIENT_SCAN_PER_EVENT, null),
        RECIPIENT_EVALUATIONS(
                PresentationLimits.MAX_RECIPIENT_EVALUATIONS_PER_TICK, null),
        SELECTED_RECIPIENTS((long) PresentationLimits.MAX_SELECTED_RECIPIENTS, null),
        CANDIDATE_DELIVERIES(
                PresentationLimits.MAX_CANDIDATE_DELIVERIES_PER_TICK, null),
        CLIENT_FACTORY_CALLS(
                PresentationLimits.MAX_CLIENT_FACTORY_CALLS_PER_TICK, null),
        ACTUAL_PARTICLE_STARTS(
                PresentationLimits.MAX_ACTUAL_PARTICLE_STARTS_PER_TICK, null),
        LIVE_PARTICLE_CREDITS(
                PresentationLimits.MAX_LIVE_PARTICLE_CREDITS, null),
        SOUND_STARTS(PresentationLimits.MAX_SOUND_STARTS_PER_TICK, null),
        ACTIVE_SOUNDS(PresentationLimits.MAX_ACTIVE_SOUNDS, null),
        ACTIVE_TRAILS(PresentationLimits.MAX_ACTIVE_TRAILS, null),
        TOTAL_TRAIL_SEGMENTS(PresentationLimits.MAX_TOTAL_TRAIL_SEGMENTS, null);

        private final Long maximumCount;
        private final Long maximumBytes;

        Scope(Long maximumCount, Long maximumBytes) {
            if (maximumCount == null && maximumBytes == null) {
                throw new IllegalArgumentException("budget scope must own at least one dimension");
            }
            this.maximumCount = maximumCount;
            this.maximumBytes = maximumBytes;
        }

        OptionalLong maximumCount() {
            return maximumCount == null ? OptionalLong.empty() : OptionalLong.of(maximumCount);
        }

        OptionalLong maximumBytes() {
            return maximumBytes == null ? OptionalLong.empty() : OptionalLong.of(maximumBytes);
        }
    }

    enum Outcome {
        ACCEPTED,
        LIMIT_EXCEEDED,
        ARITHMETIC_OVERFLOW,
        INCOMPATIBLE_COST
    }

    static final class Decision {
        private final Outcome outcome;
        private final PresentationBudget nextState;

        private Decision(Outcome outcome, PresentationBudget nextState) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.nextState = Objects.requireNonNull(nextState, "nextState");
        }

        Outcome outcome() {
            return outcome;
        }

        PresentationBudget nextState() {
            return nextState;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Decision that
                            && outcome == that.outcome
                            && nextState.equals(that.nextState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, nextState);
        }
    }

    private final Scope scope;
    private final PresentationCost used;

    private PresentationBudget(Scope scope, PresentationCost used) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.used = Objects.requireNonNull(used, "used");
        if (!compatible(used) || exceedsLimit(used)) {
            throw new IllegalArgumentException("presentation budget state is invalid");
        }
    }

    static PresentationBudget initial(Scope scope) {
        return new PresentationBudget(scope, PresentationCost.zero());
    }

    Scope scope() {
        return scope;
    }

    PresentationCost used() {
        return used;
    }

    Decision consume(PresentationCost cost) {
        Objects.requireNonNull(cost, "cost");
        if (!compatible(cost)) {
            return rejected(Outcome.INCOMPATIBLE_COST);
        }
        var sum = used.add(cost);
        if (sum instanceof PresentationCost.Arithmetic.Overflow) {
            return rejected(Outcome.ARITHMETIC_OVERFLOW);
        }
        var nextCost = ((PresentationCost.Arithmetic.Exact) sum).value();
        if (exceedsLimit(nextCost)) {
            return rejected(Outcome.LIMIT_EXCEEDED);
        }
        return new Decision(Outcome.ACCEPTED, new PresentationBudget(scope, nextCost));
    }

    private Decision rejected(Outcome outcome) {
        return new Decision(outcome, this);
    }

    private boolean compatible(PresentationCost cost) {
        return (scope.maximumCount().isPresent() || cost.count() == 0L)
                && (scope.maximumBytes().isPresent() || cost.bytes() == 0L);
    }

    private boolean exceedsLimit(PresentationCost cost) {
        return scope.maximumCount().stream().anyMatch(maximum -> cost.count() > maximum)
                || scope.maximumBytes().stream().anyMatch(maximum -> cost.bytes() > maximum);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PresentationBudget that
                        && scope == that.scope
                        && used.equals(that.used);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, used);
    }
}
