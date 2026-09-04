package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ConnectionEpochStateTest {
    @Test
    void initialAllocationStartsAtOneAndAdvancesExactlyOnce() {
        var initial = ConnectionEpochState.initial();
        var allocation = initial.allocate();

        assertTrue(allocation.accepted());
        assertEquals(1L, allocation.allocatedEpoch().orElseThrow());
        assertEquals(2L, allocation.nextState().nextEpoch().orElseThrow());
        assertFalse(allocation.nextState().exhausted());
        assertEquals(1L, initial.nextEpoch().orElseThrow());
    }

    @Test
    void maximumEpochIsAllocatedBeforeStateExhausts() {
        var maximum = ConnectionEpochState.activeAt(Long.MAX_VALUE);
        var allocation = maximum.allocate();

        assertTrue(allocation.accepted());
        assertEquals(Long.MAX_VALUE, allocation.allocatedEpoch().orElseThrow());
        assertTrue(allocation.nextState().exhausted());
        assertTrue(allocation.nextState().nextEpoch().isEmpty());
    }

    @Test
    void exhaustedStateRejectsEveryLaterAllocationWithoutMutation() {
        var exhausted = ConnectionEpochState.activeAt(Long.MAX_VALUE).allocate().nextState();
        var rejected = exhausted.allocate();

        assertFalse(rejected.accepted());
        assertTrue(rejected.allocatedEpoch().isEmpty());
        assertSame(exhausted, rejected.nextState());
        assertEquals(rejected, exhausted.allocate());
    }

    @Test
    void activeStateRejectsNonpositiveNextEpoch() {
        assertThrows(P7SemanticInvariantException.class, () -> ConnectionEpochState.activeAt(0L));
        assertThrows(P7SemanticInvariantException.class, () -> ConnectionEpochState.activeAt(-1L));
    }
}
