package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.OptionalLong;

final class CastIntentAdmissionSemantics {
    enum Outcome {
        ELIGIBLE,
        SERVER_BUSY,
        RATE_LIMITED,
        INVALID_SEQUENCE,
        DUPLICATE_SEQUENCE,
        STALE_SEQUENCE,
        SEQUENCE_GAP,
        SEQUENCE_EXHAUSTED,
        INTERNAL_SERVER_FAULT
    }

    static final class SessionState {
        private final IntentSequenceState sequenceState;
        private final IntentTokenBucket tokenBucket;
        private final IntentTickBudget playerIngressBudget;
        private final RateStrikeState rateStrikeState;

        SessionState(
                IntentSequenceState sequenceState,
                IntentTokenBucket tokenBucket,
                IntentTickBudget playerIngressBudget,
                RateStrikeState rateStrikeState) {
            if (sequenceState == null
                    || tokenBucket == null
                    || playerIngressBudget == null
                    || rateStrikeState == null
                    || playerIngressBudget.kind() != IntentTickBudget.Kind.PLAYER_INGRESS) {
                throw new P7SemanticInvariantException("session semantic state is invalid");
            }
            this.sequenceState = sequenceState;
            this.tokenBucket = tokenBucket;
            this.playerIngressBudget = playerIngressBudget;
            this.rateStrikeState = rateStrikeState;
        }

        static SessionState initial(long authoritativeTick) {
            return new SessionState(
                    IntentSequenceState.initial(),
                    IntentTokenBucket.initial(authoritativeTick),
                    IntentTickBudget.initial(
                            IntentTickBudget.Kind.PLAYER_INGRESS, authoritativeTick),
                    RateStrikeState.initial());
        }

        IntentSequenceState sequenceState() {
            return sequenceState;
        }

        IntentTokenBucket tokenBucket() {
            return tokenBucket;
        }

        IntentTickBudget playerIngressBudget() {
            return playerIngressBudget;
        }

        RateStrikeState rateStrikeState() {
            return rateStrikeState;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof SessionState that
                            && sequenceState.equals(that.sequenceState)
                            && tokenBucket.equals(that.tokenBucket)
                            && playerIngressBudget.equals(that.playerIngressBudget)
                            && rateStrikeState.equals(that.rateStrikeState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    sequenceState, tokenBucket, playerIngressBudget, rateStrikeState);
        }
    }

    static final class Decision {
        private final Outcome outcome;
        private final SessionState nextSessionState;
        private final IntentTickBudget nextGlobalBudget;
        private final boolean sequenceConsumed;
        private final boolean disconnect;
        private final OptionalLong expectedNext;

        private Decision(
                Outcome outcome,
                SessionState nextSessionState,
                IntentTickBudget nextGlobalBudget,
                boolean sequenceConsumed,
                boolean disconnect,
                OptionalLong expectedNext) {
            this.outcome = outcome;
            this.nextSessionState = nextSessionState;
            this.nextGlobalBudget = nextGlobalBudget;
            this.sequenceConsumed = sequenceConsumed;
            this.disconnect = disconnect;
            this.expectedNext = expectedNext;
        }

        Outcome outcome() {
            return outcome;
        }

        SessionState nextSessionState() {
            return nextSessionState;
        }

        IntentTickBudget nextGlobalBudget() {
            return nextGlobalBudget;
        }

        boolean sequenceConsumed() {
            return sequenceConsumed;
        }

        boolean disconnect() {
            return disconnect;
        }

        OptionalLong expectedNext() {
            return expectedNext;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Decision that
                            && sequenceConsumed == that.sequenceConsumed
                            && disconnect == that.disconnect
                            && outcome == that.outcome
                            && nextSessionState.equals(that.nextSessionState)
                            && nextGlobalBudget.equals(that.nextGlobalBudget)
                            && expectedNext.equals(that.expectedNext);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    outcome,
                    nextSessionState,
                    nextGlobalBudget,
                    sequenceConsumed,
                    disconnect,
                    expectedNext);
        }
    }

    private CastIntentAdmissionSemantics() {
        throw new AssertionError("no instances");
    }

    static Decision evaluate(
            SessionState sessionState,
            IntentTickBudget globalBudget,
            long authoritativeTick,
            long receivedSequence) {
        requireInputs(sessionState, globalBudget);
        if (!allTicksMonotonic(sessionState, globalBudget, authoritativeTick)) {
            return decision(
                    Outcome.INTERNAL_SERVER_FAULT,
                    sessionState,
                    globalBudget,
                    false,
                    false,
                    OptionalLong.empty());
        }

        var globalDecision = globalBudget.consume(authoritativeTick);
        if (globalDecision.outcome() == IntentTickBudget.Outcome.DENIED) {
            return decision(
                    Outcome.SERVER_BUSY,
                    sessionState,
                    globalBudget,
                    false,
                    false,
                    sessionState.sequenceState().expectedNext());
        }
        if (globalDecision.outcome() != IntentTickBudget.Outcome.ADMITTED) {
            throw new P7SemanticInvariantException("monotonic global budget faulted");
        }

        var playerDecision = sessionState.playerIngressBudget().consume(authoritativeTick);
        if (playerDecision.outcome() == IntentTickBudget.Outcome.DENIED) {
            return rateLimited(
                    sessionState,
                    globalDecision.nextState(),
                    sessionState.playerIngressBudget(),
                    sessionState.tokenBucket(),
                    authoritativeTick);
        }
        if (playerDecision.outcome() != IntentTickBudget.Outcome.ADMITTED) {
            throw new P7SemanticInvariantException("monotonic player budget faulted");
        }

        var tokenDecision = sessionState.tokenBucket().consume(authoritativeTick);
        if (tokenDecision.outcome() == IntentTokenBucket.Outcome.RATE_LIMITED) {
            return rateLimited(
                    sessionState,
                    globalDecision.nextState(),
                    playerDecision.nextState(),
                    tokenDecision.nextState(),
                    authoritativeTick);
        }
        if (tokenDecision.outcome() != IntentTokenBucket.Outcome.CONSUMED) {
            throw new P7SemanticInvariantException("monotonic token bucket faulted");
        }

        var sequenceDecision = sessionState.sequenceState().evaluate(receivedSequence);
        var nextSession = new SessionState(
                sequenceDecision.nextState(),
                tokenDecision.nextState(),
                playerDecision.nextState(),
                sessionState.rateStrikeState());
        return decision(
                outcomeFor(sequenceDecision.classification()),
                nextSession,
                globalDecision.nextState(),
                sequenceDecision.sequenceConsumed(),
                false,
                sequenceDecision.expectedNext());
    }

    private static void requireInputs(
            SessionState sessionState, IntentTickBudget globalBudget) {
        if (sessionState == null
                || globalBudget == null
                || globalBudget.kind() != IntentTickBudget.Kind.GLOBAL_WORK) {
            throw new P7SemanticInvariantException("admission semantic inputs are invalid");
        }
    }

    private static boolean allTicksMonotonic(
            SessionState sessionState,
            IntentTickBudget globalBudget,
            long authoritativeTick) {
        return globalBudget.tickIsMonotonic(authoritativeTick)
                && sessionState.playerIngressBudget().tickIsMonotonic(authoritativeTick)
                && sessionState.tokenBucket().tickIsMonotonic(authoritativeTick)
                && sessionState.rateStrikeState().tickIsMonotonic(authoritativeTick);
    }

    private static Decision rateLimited(
            SessionState originalSession,
            IntentTickBudget nextGlobalBudget,
            IntentTickBudget nextPlayerBudget,
            IntentTokenBucket nextTokenBucket,
            long authoritativeTick) {
        var strikeDecision =
                originalSession.rateStrikeState().recordRateLimited(authoritativeTick);
        if (strikeDecision.outcome() != RateStrikeState.Outcome.RECORDED) {
            throw new P7SemanticInvariantException("monotonic rate strike faulted");
        }
        var nextSession = new SessionState(
                originalSession.sequenceState(),
                nextTokenBucket,
                nextPlayerBudget,
                strikeDecision.nextState());
        return decision(
                Outcome.RATE_LIMITED,
                nextSession,
                nextGlobalBudget,
                false,
                strikeDecision.disconnect(),
                originalSession.sequenceState().expectedNext());
    }

    private static Outcome outcomeFor(IntentSequenceState.Classification classification) {
        return switch (classification) {
            case ACCEPTED -> Outcome.ELIGIBLE;
            case DUPLICATE -> Outcome.DUPLICATE_SEQUENCE;
            case STALE -> Outcome.STALE_SEQUENCE;
            case GAP -> Outcome.SEQUENCE_GAP;
            case INVALID -> Outcome.INVALID_SEQUENCE;
            case EXHAUSTED -> Outcome.SEQUENCE_EXHAUSTED;
        };
    }

    private static Decision decision(
            Outcome outcome,
            SessionState nextSessionState,
            IntentTickBudget nextGlobalBudget,
            boolean sequenceConsumed,
            boolean disconnect,
            OptionalLong expectedNext) {
        return new Decision(
                outcome,
                nextSessionState,
                nextGlobalBudget,
                sequenceConsumed,
                disconnect,
                expectedNext);
    }
}
