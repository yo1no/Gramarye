package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RateStrikeStateTest {
    @Test
    void initialStateHasNoWindowAndNoStrikes() {
        var state = RateStrikeState.initial();

        assertFalse(state.windowActive());
        assertEquals(0, state.strikeCount());
    }

    @Test
    void firstStrikeOpensWindowWithCountOne() {
        var decision = RateStrikeState.initial().recordRateLimited(20L);

        assertEquals(RateStrikeState.Outcome.RECORDED, decision.outcome());
        assertTrue(decision.nextState().windowActive());
        assertEquals(20L, decision.nextState().windowStartTick());
        assertEquals(20L, decision.nextState().lastStrikeTick());
        assertEquals(1, decision.nextState().strikeCount());
        assertFalse(decision.disconnect());
    }

    @Test
    void seventhStrikeDoesNotDisconnectAndEighthDoes() {
        var state = RateStrikeState.initial();
        RateStrikeState.Decision decision = null;
        for (var count = 1; count <= 8; count++) {
            decision = state.recordRateLimited(count);
            state = decision.nextState();
            assertEquals(count == 8, decision.disconnect());
        }

        assertEquals(8, state.strikeCount());
    }

    @Test
    void tickNinetyNineRemainsInTheFirstStrikeWindow() {
        var first = RateStrikeState.initial().recordRateLimited(0L).nextState();

        var decision = first.recordRateLimited(99L);

        assertEquals(0L, decision.nextState().windowStartTick());
        assertEquals(2, decision.nextState().strikeCount());
    }

    @Test
    void tickOneHundredStartsANewWindow() {
        var first = RateStrikeState.initial().recordRateLimited(0L).nextState();

        var decision = first.recordRateLimited(100L);

        assertEquals(100L, decision.nextState().windowStartTick());
        assertEquals(1, decision.nextState().strikeCount());
        assertFalse(decision.disconnect());
    }

    @Test
    void tickRegressionIsAnInternalFaultWithOriginalState() {
        var state = RateStrikeState.initial().recordRateLimited(10L).nextState();

        var decision = state.recordRateLimited(9L);

        assertEquals(RateStrikeState.Outcome.INTERNAL_SERVER_FAULT, decision.outcome());
        assertSame(state, decision.nextState());
        assertFalse(decision.disconnect());
    }

    @Test
    void strikeCountSaturatesAtEight() {
        var state = RateStrikeState.initial();
        for (var count = 0; count < 8; count++) {
            state = state.recordRateLimited(count).nextState();
        }

        var ninth = state.recordRateLimited(8L);

        assertEquals(8, ninth.nextState().strikeCount());
        assertTrue(ninth.disconnect());
    }

    @Test
    void extremeElapsedRangeResetsWithoutArithmeticOverflow() {
        var state = RateStrikeState.initial().recordRateLimited(Long.MIN_VALUE).nextState();

        var decision = state.recordRateLimited(Long.MAX_VALUE);

        assertEquals(RateStrikeState.Outcome.RECORDED, decision.outcome());
        assertEquals(Long.MAX_VALUE, decision.nextState().windowStartTick());
        assertEquals(1, decision.nextState().strikeCount());
    }

    @Test
    void equalInputsProduceEqualStatesAndDisconnectDecisions() {
        var left = RateStrikeState.initial().recordRateLimited(3L);
        var right = RateStrikeState.initial().recordRateLimited(3L);

        assertEquals(left.outcome(), right.outcome());
        assertEquals(left.nextState(), right.nextState());
        assertEquals(left.disconnect(), right.disconnect());
    }
}
