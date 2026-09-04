package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CastIntentAdmissionSemanticsTest {
    @Test
    void admissionOutcomeVocabularyIsExactAndClosed() {
        assertArrayEquals(
                new CastIntentAdmissionSemantics.Outcome[] {
                    CastIntentAdmissionSemantics.Outcome.ELIGIBLE,
                    CastIntentAdmissionSemantics.Outcome.SERVER_BUSY,
                    CastIntentAdmissionSemantics.Outcome.RATE_LIMITED,
                    CastIntentAdmissionSemantics.Outcome.INVALID_SEQUENCE,
                    CastIntentAdmissionSemantics.Outcome.DUPLICATE_SEQUENCE,
                    CastIntentAdmissionSemantics.Outcome.STALE_SEQUENCE,
                    CastIntentAdmissionSemantics.Outcome.SEQUENCE_GAP,
                    CastIntentAdmissionSemantics.Outcome.SEQUENCE_EXHAUSTED,
                    CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT
                },
                CastIntentAdmissionSemantics.Outcome.values());
    }

    @Test
    void exactNextConsumesGlobalPlayerTokenAndSequenceInOrder() {
        var session = CastIntentAdmissionSemantics.SessionState.initial(0L);
        var global = globalAt(0L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 0L, 1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.ELIGIBLE, decision.outcome());
        assertEquals(1, decision.nextGlobalBudget().used());
        assertEquals(1, decision.nextSessionState().playerIngressBudget().used());
        assertEquals(7, decision.nextSessionState().tokenBucket().tokens());
        assertEquals(2L, decision.nextSessionState().sequenceState()
                .expectedNext()
                .orElseThrow());
        assertEquals(2L, decision.expectedNext().orElseThrow());
        assertTrue(decision.sequenceConsumed());
        assertFalse(decision.disconnect());
    }

    @Test
    void globalBusyPrecedesAndPreservesEverySessionComponent() {
        var session = session(
                IntentSequenceState.expecting(3L),
                drainBucket(0L),
                consumeToLimit(playerAt(0L)),
                strikesAt(0L, 3));
        var global = consumeToLimit(globalAt(0L));

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 0L, 0L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.SERVER_BUSY, decision.outcome());
        assertSame(session, decision.nextSessionState());
        assertSame(global, decision.nextGlobalBudget());
        assertFalse(decision.sequenceConsumed());
        assertEquals(3, session.rateStrikeState().strikeCount());
        assertEquals(3L, decision.expectedNext().orElseThrow());
    }

    @Test
    void playerLimitConsumesGlobalButNotBucketOrSequence() {
        var player = consumeToLimit(playerAt(0L));
        var bucket = drainBucket(0L);
        var sequence = IntentSequenceState.expecting(3L);
        var session = session(
                sequence,
                bucket,
                player,
                RateStrikeState.initial());

        var decision = CastIntentAdmissionSemantics.evaluate(session, globalAt(0L), 0L, 0L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.RATE_LIMITED, decision.outcome());
        assertEquals(1, decision.nextGlobalBudget().used());
        assertSame(player, decision.nextSessionState().playerIngressBudget());
        assertSame(bucket, decision.nextSessionState().tokenBucket());
        assertSame(sequence, decision.nextSessionState().sequenceState());
        assertEquals(1, decision.nextSessionState().rateStrikeState().strikeCount());
        assertFalse(decision.sequenceConsumed());
        assertEquals(3L, decision.expectedNext().orElseThrow());
    }

    @Test
    void emptyBucketConsumesGlobalAndPlayerBeforeRateLimiting() {
        var emptyBucket = drainBucket(0L);
        var session = session(
                IntentSequenceState.initial(),
                emptyBucket,
                playerAt(0L),
                RateStrikeState.initial());

        var decision = CastIntentAdmissionSemantics.evaluate(session, globalAt(0L), 0L, 0L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.RATE_LIMITED, decision.outcome());
        assertEquals(1, decision.nextGlobalBudget().used());
        assertEquals(1, decision.nextSessionState().playerIngressBudget().used());
        assertSame(emptyBucket, decision.nextSessionState().tokenBucket());
        assertSame(session.sequenceState(), decision.nextSessionState().sequenceState());
        assertEquals(1, decision.nextSessionState().rateStrikeState().strikeCount());
    }

    @Test
    void eighthRateStrikeRequestsDisconnectWithoutConsumingSequence() {
        var strikes = strikesAt(0L, 7);
        var session = session(
                IntentSequenceState.initial(),
                IntentTokenBucket.initial(0L),
                consumeToLimit(playerAt(0L)),
                strikes);

        var decision = CastIntentAdmissionSemantics.evaluate(session, globalAt(0L), 0L, 1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.RATE_LIMITED, decision.outcome());
        assertTrue(decision.disconnect());
        assertFalse(decision.sequenceConsumed());
        assertEquals(8, decision.nextSessionState().rateStrikeState().strikeCount());
        assertEquals(1L, decision.expectedNext().orElseThrow());
    }

    @Test
    void serverBusyDoesNotAddARateStrike() {
        var strikes = strikesAt(0L, 3);
        var session = session(
                IntentSequenceState.initial(),
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                strikes);

        var decision = CastIntentAdmissionSemantics.evaluate(
                session, consumeToLimit(globalAt(0L)), 0L, 1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.SERVER_BUSY, decision.outcome());
        assertSame(strikes, decision.nextSessionState().rateStrikeState());
    }

    @Test
    void duplicateConsumesEarlierBudgetsAndTokenButDoesNotAdvanceSequence() {
        var sequence = IntentSequenceState.expecting(2L);
        var strikes = strikesAt(0L, 3);
        var session = session(
                sequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                strikes);

        var decision = CastIntentAdmissionSemantics.evaluate(session, globalAt(0L), 0L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.DUPLICATE_SEQUENCE,
                decision.outcome());
        assertEquals(1, decision.nextGlobalBudget().used());
        assertEquals(1, decision.nextSessionState().playerIngressBudget().used());
        assertEquals(7, decision.nextSessionState().tokenBucket().tokens());
        assertSame(sequence, decision.nextSessionState().sequenceState());
        assertSame(strikes, decision.nextSessionState().rateStrikeState());
        assertFalse(decision.sequenceConsumed());
    }

    @Test
    void staleAndGapRemainUnconsumedAfterEarlierAccounting() {
        var staleSequence = IntentSequenceState.expecting(3L);
        var staleStrikes = strikesAt(0L, 2);
        var staleSession = session(
                staleSequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                staleStrikes);
        var gapSequence = IntentSequenceState.expecting(2L);
        var gapStrikes = strikesAt(0L, 2);
        var gapSession = session(
                gapSequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                gapStrikes);
        var stale = CastIntentAdmissionSemantics.evaluate(
                staleSession,
                globalAt(0L),
                0L,
                1L);
        var gap = CastIntentAdmissionSemantics.evaluate(
                gapSession,
                globalAt(0L),
                0L,
                3L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.STALE_SEQUENCE, stale.outcome());
        assertEquals(CastIntentAdmissionSemantics.Outcome.SEQUENCE_GAP, gap.outcome());
        assertFalse(stale.sequenceConsumed());
        assertFalse(gap.sequenceConsumed());
        assertEquals(1, stale.nextGlobalBudget().used());
        assertEquals(1, gap.nextGlobalBudget().used());
        assertEquals(1, stale.nextSessionState().playerIngressBudget().used());
        assertEquals(1, gap.nextSessionState().playerIngressBudget().used());
        assertEquals(7, stale.nextSessionState().tokenBucket().tokens());
        assertEquals(7, gap.nextSessionState().tokenBucket().tokens());
        assertSame(staleSequence, stale.nextSessionState().sequenceState());
        assertSame(gapSequence, gap.nextSessionState().sequenceState());
        assertSame(staleStrikes, stale.nextSessionState().rateStrikeState());
        assertSame(gapStrikes, gap.nextSessionState().rateStrikeState());
        assertEquals(3L, stale.expectedNext().orElseThrow());
        assertEquals(2L, gap.expectedNext().orElseThrow());
    }

    @Test
    void nonpositiveRawSequenceIsSemanticInvalidNotMalformed() {
        var zeroSequence = IntentSequenceState.initial();
        var zeroStrikes = strikesAt(0L, 2);
        var zeroSession = session(
                zeroSequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                zeroStrikes);
        var negativeSequence = IntentSequenceState.initial();
        var negativeStrikes = strikesAt(0L, 2);
        var negativeSession = session(
                negativeSequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                negativeStrikes);
        var zero = CastIntentAdmissionSemantics.evaluate(
                zeroSession,
                globalAt(0L),
                0L,
                0L);
        var negative = CastIntentAdmissionSemantics.evaluate(
                negativeSession,
                globalAt(0L),
                0L,
                -1L);

        assertEquals(CastIntentAdmissionSemantics.Outcome.INVALID_SEQUENCE, zero.outcome());
        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INVALID_SEQUENCE, negative.outcome());
        assertFalse(zero.sequenceConsumed());
        assertFalse(negative.sequenceConsumed());
        assertEquals(1, zero.nextGlobalBudget().used());
        assertEquals(1, negative.nextGlobalBudget().used());
        assertEquals(1, zero.nextSessionState().playerIngressBudget().used());
        assertEquals(1, negative.nextSessionState().playerIngressBudget().used());
        assertEquals(7, zero.nextSessionState().tokenBucket().tokens());
        assertEquals(7, negative.nextSessionState().tokenBucket().tokens());
        assertSame(zeroSequence, zero.nextSessionState().sequenceState());
        assertSame(negativeSequence, negative.nextSessionState().sequenceState());
        assertSame(zeroStrikes, zero.nextSessionState().rateStrikeState());
        assertSame(negativeStrikes, negative.nextSessionState().rateStrikeState());
        assertEquals(1L, zero.expectedNext().orElseThrow());
        assertEquals(1L, negative.expectedNext().orElseThrow());
    }

    @Test
    void exhaustedSequenceStillConsumesPriorAccountingAndHasNoExpectedNext() {
        var sequence = exhaustedSequenceState();
        var strikes = strikesAt(0L, 2);
        var session = session(
                sequence,
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                strikes);

        var decision = CastIntentAdmissionSemantics.evaluate(session, globalAt(0L), 0L, 0L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.SEQUENCE_EXHAUSTED,
                decision.outcome());
        assertEquals(1, decision.nextGlobalBudget().used());
        assertEquals(1, decision.nextSessionState().playerIngressBudget().used());
        assertEquals(7, decision.nextSessionState().tokenBucket().tokens());
        assertSame(sequence, decision.nextSessionState().sequenceState());
        assertSame(strikes, decision.nextSessionState().rateStrikeState());
        assertTrue(decision.expectedNext().isEmpty());
        assertFalse(decision.sequenceConsumed());
    }

    @Test
    void anyTickRegressionRollsBackAllComponentsBeforePartialMutation() {
        var session = CastIntentAdmissionSemantics.SessionState.initial(10L);
        var global = globalAt(10L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 9L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                decision.outcome());
        assertSame(session, decision.nextSessionState());
        assertSame(global, decision.nextGlobalBudget());
        assertFalse(decision.sequenceConsumed());
        assertTrue(decision.expectedNext().isEmpty());
    }

    @Test
    void rateStrikeTickRegressionAlsoRollsBackBeforeBudgetConsumption() {
        var session = session(
                IntentSequenceState.initial(),
                IntentTokenBucket.initial(0L),
                playerAt(0L),
                RateStrikeState.initial().recordRateLimited(10L).nextState());
        var global = globalAt(0L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 9L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                decision.outcome());
        assertSame(session, decision.nextSessionState());
        assertSame(global, decision.nextGlobalBudget());
    }

    @Test
    void playerBudgetTickRegressionRollsBackBeforeGlobalConsumption() {
        var session = session(
                IntentSequenceState.initial(),
                IntentTokenBucket.initial(0L),
                playerAt(10L),
                RateStrikeState.initial());
        var global = globalAt(0L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 9L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                decision.outcome());
        assertSame(session, decision.nextSessionState());
        assertSame(global, decision.nextGlobalBudget());
    }

    @Test
    void tokenBucketTickRegressionRollsBackBeforeEitherBudgetConsumption() {
        var session = session(
                IntentSequenceState.initial(),
                IntentTokenBucket.initial(10L),
                playerAt(0L),
                RateStrikeState.initial());
        var global = globalAt(0L);

        var decision = CastIntentAdmissionSemantics.evaluate(session, global, 9L, 1L);

        assertEquals(
                CastIntentAdmissionSemantics.Outcome.INTERNAL_SERVER_FAULT,
                decision.outcome());
        assertSame(session, decision.nextSessionState());
        assertSame(global, decision.nextGlobalBudget());
    }

    @Test
    void equalInputAndStateProduceEqualDecisions() {
        var left = CastIntentAdmissionSemantics.evaluate(
                CastIntentAdmissionSemantics.SessionState.initial(4L),
                globalAt(4L),
                4L,
                1L);
        var right = CastIntentAdmissionSemantics.evaluate(
                CastIntentAdmissionSemantics.SessionState.initial(4L),
                globalAt(4L),
                4L,
                1L);

        assertEquals(left, right);
    }

    private static CastIntentAdmissionSemantics.SessionState session(
            IntentSequenceState sequence,
            IntentTokenBucket bucket,
            IntentTickBudget player,
            RateStrikeState strikes) {
        return new CastIntentAdmissionSemantics.SessionState(
                sequence, bucket, player, strikes);
    }

    private static IntentTickBudget globalAt(long tick) {
        return IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, tick);
    }

    private static IntentTickBudget playerAt(long tick) {
        return IntentTickBudget.initial(IntentTickBudget.Kind.PLAYER_INGRESS, tick);
    }

    private static IntentTickBudget consumeToLimit(IntentTickBudget budget) {
        for (var count = budget.used(); count < budget.kind().limit(); count++) {
            budget = budget.consume(budget.currentTick()).nextState();
        }
        return budget;
    }

    private static IntentTokenBucket drainBucket(long tick) {
        var bucket = IntentTokenBucket.initial(tick);
        for (var count = 0; count < P7NetworkBounds.RATE_BUCKET_CAPACITY; count++) {
            bucket = bucket.consume(tick).nextState();
        }
        return bucket;
    }

    private static RateStrikeState strikesAt(long tick, int count) {
        var state = RateStrikeState.initial();
        for (var index = 0; index < count; index++) {
            state = state.recordRateLimited(tick).nextState();
        }
        return state;
    }

    private static IntentSequenceState exhaustedSequenceState() {
        return IntentSequenceState.expecting(Long.MAX_VALUE)
                .evaluate(Long.MAX_VALUE)
                .nextState();
    }
}
