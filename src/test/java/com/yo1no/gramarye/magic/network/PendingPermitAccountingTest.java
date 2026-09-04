package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PendingPermitAccountingTest {
    @Test
    void acquireFromEmptyIncrementsBothCountsAndReturnsOpenPermit() {
        var decision = PendingPermitAccounting.empty().acquire();

        assertEquals(PendingPermitAccounting.AcquireOutcome.GRANTED, decision.outcome());
        assertEquals(1, decision.nextState().playerPending());
        assertEquals(1, decision.nextState().serverPending());
        assertTrue(decision.permit().isPresent());
        assertFalse(decision.permit().orElseThrow().released());
    }

    @Test
    void acquireAtBothUpperEdgesReachesTheExactLimits() {
        var state = new PendingPermitAccounting(7, 63);

        var decision = state.acquire();

        assertEquals(PendingPermitAccounting.AcquireOutcome.GRANTED, decision.outcome());
        assertEquals(8, decision.nextState().playerPending());
        assertEquals(64, decision.nextState().serverPending());
        assertTrue(decision.permit().isPresent());
        assertFalse(decision.permit().orElseThrow().released());
        assertEquals(7, state.playerPending());
        assertEquals(63, state.serverPending());
    }

    @Test
    void playerLimitRejectsWithoutChangingState() {
        var state = new PendingPermitAccounting(8, 8);

        var decision = state.acquire();

        assertEquals(PendingPermitAccounting.AcquireOutcome.SERVER_BUSY, decision.outcome());
        assertSame(state, decision.nextState());
        assertTrue(decision.permit().isEmpty());
    }

    @Test
    void serverLimitRejectsWithoutChangingState() {
        var state = new PendingPermitAccounting(1, 64);

        var decision = state.acquire();

        assertEquals(PendingPermitAccounting.AcquireOutcome.SERVER_BUSY, decision.outcome());
        assertSame(state, decision.nextState());
    }

    @Test
    void releaseDecrementsBothCountsAndClosesModeledPermit() {
        var acquired = PendingPermitAccounting.empty().acquire();
        var openPermit = acquired.permit().orElseThrow();

        var released = acquired.nextState().release(openPermit);

        assertEquals(PendingPermitAccounting.ReleaseOutcome.RELEASED, released.outcome());
        assertEquals(PendingPermitAccounting.empty(), released.nextState());
        assertFalse(openPermit.released());
        assertTrue(released.nextPermit().released());
    }

    @Test
    void secondReleaseOfReturnedClosedPermitIsRejected() {
        var acquired = PendingPermitAccounting.empty().acquire();
        var released = acquired.nextState().release(acquired.permit().orElseThrow());

        var duplicate = released.nextState().release(released.nextPermit());

        assertEquals(PendingPermitAccounting.ReleaseOutcome.REJECTED, duplicate.outcome());
        assertSame(released.nextState(), duplicate.nextState());
        assertSame(released.nextPermit(), duplicate.nextPermit());
    }

    @Test
    void releaseAgainstEmptyAccountingIsRejectedAsUnderflow() {
        var openPermit = PendingPermitAccounting.empty()
                .acquire()
                .permit()
                .orElseThrow();
        var empty = PendingPermitAccounting.empty();

        var decision = empty.release(openPermit);

        assertEquals(PendingPermitAccounting.ReleaseOutcome.REJECTED, decision.outcome());
        assertSame(empty, decision.nextState());
    }

    @Test
    void releaseRejectsWhenEitherPendingCountWouldUnderflow() {
        var openPermit = PendingPermitAccounting.empty()
                .acquire()
                .permit()
                .orElseThrow();
        var noPlayerPending = new PendingPermitAccounting(0, 1);

        var decision = noPlayerPending.release(openPermit);

        assertEquals(PendingPermitAccounting.ReleaseOutcome.REJECTED, decision.outcome());
        assertSame(noPlayerPending, decision.nextState());
        assertSame(openPermit, decision.nextPermit());
    }

    @Test
    void invalidNegativeOrInvertedCountsAreRejected() {
        assertThrows(P7SemanticInvariantException.class, () -> new PendingPermitAccounting(-1, 0));
        assertThrows(P7SemanticInvariantException.class, () -> new PendingPermitAccounting(1, 0));
        assertThrows(P7SemanticInvariantException.class, () -> new PendingPermitAccounting(9, 9));
        assertThrows(P7SemanticInvariantException.class, () -> new PendingPermitAccounting(1, 65));
    }

    @Test
    void equalInputsProduceEqualAccountingDecisions() {
        var left = PendingPermitAccounting.empty().acquire();
        var right = PendingPermitAccounting.empty().acquire();

        assertEquals(left.outcome(), right.outcome());
        assertEquals(left.nextState(), right.nextState());
        assertEquals(
                left.permit().orElseThrow().released(),
                right.permit().orElseThrow().released());
    }
}
