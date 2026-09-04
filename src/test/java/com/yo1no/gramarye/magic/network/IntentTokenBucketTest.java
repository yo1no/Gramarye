package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

final class IntentTokenBucketTest {
    @Test
    void initialStateUsesEightTokensAtTheCallerTick() {
        var bucket = IntentTokenBucket.initial(41L);

        assertEquals(8, bucket.tokens());
        assertEquals(41L, bucket.lastRefillTick());
    }

    @Test
    void eightSameTickCastsConsumeAndTheNinthIsRateLimited() {
        var bucket = IntentTokenBucket.initial(7L);
        for (var index = 0; index < 8; index++) {
            var decision = bucket.consume(7L);
            assertEquals(IntentTokenBucket.Outcome.CONSUMED, decision.outcome());
            bucket = decision.nextState();
        }

        var ninth = bucket.consume(7L);
        assertEquals(IntentTokenBucket.Outcome.RATE_LIMITED, ninth.outcome());
        assertSame(bucket, ninth.nextState());
    }

    @Test
    void oneElapsedTickRefillsExactlyTwoTokens() {
        var bucket = exhaustedAt(10L);

        var first = bucket.consume(11L);
        var second = first.nextState().consume(11L);
        var third = second.nextState().consume(11L);

        assertEquals(IntentTokenBucket.Outcome.CONSUMED, first.outcome());
        assertEquals(IntentTokenBucket.Outcome.CONSUMED, second.outcome());
        assertEquals(IntentTokenBucket.Outcome.RATE_LIMITED, third.outcome());
    }

    @Test
    void refillSaturatesAtCapacityAcrossManyTicks() {
        var consumedOnce = IntentTokenBucket.initial(0L).consume(0L).nextState();
        var afterLongGap = consumedOnce.consume(1_000_000L).nextState();

        assertEquals(7, afterLongGap.tokens());
        assertEquals(1_000_000L, afterLongGap.lastRefillTick());
    }

    @Test
    void tickRegressionIsAnInternalFaultWithOriginalState() {
        var bucket = IntentTokenBucket.initial(12L);

        var decision = bucket.consume(11L);

        assertEquals(IntentTokenBucket.Outcome.INTERNAL_SERVER_FAULT, decision.outcome());
        assertSame(bucket, decision.nextState());
    }

    @Test
    void hugeElapsedRangeCannotOverflowRefillArithmetic() {
        var consumedOnce = IntentTokenBucket.initial(Long.MIN_VALUE)
                .consume(Long.MIN_VALUE)
                .nextState();

        var decision = consumedOnce.consume(Long.MAX_VALUE);

        assertEquals(IntentTokenBucket.Outcome.CONSUMED, decision.outcome());
        assertEquals(7, decision.nextState().tokens());
    }

    @Test
    void equalInputsProduceEqualDecisionsAndStates() {
        var left = exhaustedAt(5L).consume(5L);
        var right = exhaustedAt(5L).consume(5L);

        assertEquals(left.outcome(), right.outcome());
        assertEquals(left.nextState(), right.nextState());
    }

    private static IntentTokenBucket exhaustedAt(long tick) {
        var bucket = IntentTokenBucket.initial(tick);
        for (var index = 0; index < 8; index++) {
            bucket = bucket.consume(tick).nextState();
        }
        return bucket;
    }
}
