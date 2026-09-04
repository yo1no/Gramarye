package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class IntentSequenceStateTest {
    @Test
    void initialStateAcceptsOneAndAdvancesToTwo() {
        var decision = IntentSequenceState.initial().evaluate(1L);

        assertEquals(IntentSequenceState.Classification.ACCEPTED, decision.classification());
        assertTrue(decision.sequenceConsumed());
        assertEquals(2L, decision.expectedNext().orElseThrow());
        assertEquals(2L, decision.nextState().expectedNext().orElseThrow());
    }

    @Test
    void immediatelyPreviousSequenceIsDuplicate() {
        var state = IntentSequenceState.expecting(7L);
        assertUnchanged(
                state,
                state.evaluate(6L),
                IntentSequenceState.Classification.DUPLICATE,
                7L);
    }

    @Test
    void olderPositiveSequenceIsStale() {
        var state = IntentSequenceState.expecting(7L);
        assertUnchanged(state, state.evaluate(5L), IntentSequenceState.Classification.STALE, 7L);
    }

    @Test
    void futureSequenceIsGapAndIsNeverBuffered() {
        var state = IntentSequenceState.expecting(7L);
        assertUnchanged(state, state.evaluate(8L), IntentSequenceState.Classification.GAP, 7L);
        assertEquals(state.evaluate(8L), state.evaluate(8L));
    }

    @Test
    void everyNonpositiveRawSequenceIsInvalid() {
        var state = IntentSequenceState.initial();
        assertUnchanged(state, state.evaluate(0L), IntentSequenceState.Classification.INVALID, 1L);
        assertUnchanged(
                state,
                state.evaluate(Long.MIN_VALUE),
                IntentSequenceState.Classification.INVALID,
                1L);
    }

    @Test
    void maximumSequenceIsAcceptedOnceThenExhaustsWithoutWrap() {
        var decision = IntentSequenceState.expecting(Long.MAX_VALUE).evaluate(Long.MAX_VALUE);

        assertEquals(IntentSequenceState.Classification.ACCEPTED, decision.classification());
        assertTrue(decision.sequenceConsumed());
        assertTrue(decision.nextState().exhausted());
        assertTrue(decision.expectedNext().isEmpty());

        var afterExhaustion = decision.nextState().evaluate(Long.MAX_VALUE);
        assertEquals(
                IntentSequenceState.Classification.EXHAUSTED,
                afterExhaustion.classification());
        assertFalse(afterExhaustion.sequenceConsumed());
        assertSame(decision.nextState(), afterExhaustion.nextState());
        assertTrue(afterExhaustion.expectedNext().isEmpty());
    }

    @Test
    void exhaustedStateClassifiesEveryLongAsExhausted() {
        var exhausted = IntentSequenceState.expecting(Long.MAX_VALUE)
                .evaluate(Long.MAX_VALUE)
                .nextState();

        for (long received : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            var decision = exhausted.evaluate(received);
            assertEquals(IntentSequenceState.Classification.EXHAUSTED, decision.classification());
            assertFalse(decision.sequenceConsumed());
            assertSame(exhausted, decision.nextState());
            assertTrue(decision.expectedNext().isEmpty());
        }
    }

    private static void assertUnchanged(
            IntentSequenceState state,
            IntentSequenceState.Decision decision,
            IntentSequenceState.Classification classification,
            long expectedNext) {
        assertEquals(classification, decision.classification());
        assertFalse(decision.sequenceConsumed());
        assertSame(state, decision.nextState());
        assertEquals(expectedNext, decision.expectedNext().orElseThrow());
    }
}
