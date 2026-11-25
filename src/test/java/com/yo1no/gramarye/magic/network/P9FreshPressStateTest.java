package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P9FreshPressStateTest {
    @Test void heldAndRepeatNeverCreateMoreFreshAttempts() {
        var state = new P9FreshPressState();
        state.action(1, true);
        for (int index = 0; index < 20; index++) { state.action(2, true); }
        state.action(1, true);
        assertEquals(1, state.takeEligible(22, true));
        state.action(2, true);
        assertEquals(0, state.takeEligible(1, true));
        state.action(0, true);
        state.action(1, true);
        assertEquals(1, state.takeEligible(1, true));
    }

    @Test void twoDeliveredShortPressesBetweenTicksRemainTwo() {
        var state = new P9FreshPressState();
        state.action(1, true); state.action(0, true);
        state.action(1, true); state.action(0, true);
        assertEquals(2, state.takeEligible(2, true));
        assertEquals(0, state.takeEligible(0, true));
    }

    @Test void eighthEdgeFitsButNinthDropsTheEntireFrameWithoutWrap() {
        for (int count : new int[] {8, 9, 1000}) {
            var state = new P9FreshPressState();
            for (int index = 0; index < count; index++) {
                state.action(1, true); state.action(0, true);
            }
            assertEquals(count == 8 ? 8 : -1, state.takeEligible(count, true));
        }
    }

    @Test void anEdgeWithoutANativeMappingClickCannotCreateAnIntent() {
        var state = new P9FreshPressState();
        state.action(1, true);
        assertEquals(-1, state.takeEligible(0, true));
        assertEquals(0, state.takeEligible(0, true));
    }

    @Test void suppressionInvalidatesAllOldCandidatesAndDoesNotReplayThem() {
        var state = new P9FreshPressState();
        state.action(1, true); state.action(0, true);
        state.invalidate();
        assertEquals(0, state.takeEligible(1, true));
        state.action(2, true);
        assertEquals(0, state.takeEligible(1, true));
        state.action(0, false); state.action(1, true);
        assertEquals(1, state.takeEligible(1, true));
    }

    @Test void ineligibleFreshPressesAreDroppedInsteadOfDeferred() {
        var state = new P9FreshPressState();
        state.action(1, false); state.action(0, false);
        assertEquals(0, state.takeEligible(1, true));
        assertEquals(0, state.takeEligible(0, true));
    }

    @Test void drainCallsTheActualSupplierAtMostThirtyTwoTimes() {
        for (int pending : new int[] {0, 1, 31, 32, 33, Integer.MAX_VALUE}) {
            var calls = new AtomicInteger();
            var drained = P9FreshPressState.drain(() -> calls.getAndIncrement() < pending);
            assertEquals(Math.min(pending, 32), drained.clicks());
            assertEquals(pending < 32, drained.empty());
            assertEquals(pending < 32 ? pending + 1 : 32, calls.get());
        }
    }

    @Test void anUnobservedEmptyDropsEvenAnOtherwiseValidFreshPress() {
        var state = new P9FreshPressState();
        state.action(1, true);
        assertEquals(-1, state.takeEligible(32, false));
        state.invalidate();
        assertEquals(0, state.takeEligible(0, true));
        state.action(2, true);
        assertEquals(0, state.takeEligible(1, true));
        state.action(0, true); state.action(1, true);
        assertEquals(1, state.takeEligible(1, true));
    }
}
