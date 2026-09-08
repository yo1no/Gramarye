package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PresentationCostTest {
    @Test
    void costIsNonnegativeAndKeepsCountSeparateFromBytes() {
        assertEquals(new PresentationCost(0L, 0L), PresentationCost.zero());
        assertEquals(new PresentationCost(7L, 0L), PresentationCost.count(7L));
        assertEquals(new PresentationCost(0L, 11L), PresentationCost.bytes(11L));
        assertThrows(IllegalArgumentException.class, () -> new PresentationCost(-1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PresentationCost(0L, -1L));
    }

    @Test
    void checkedAdditionReturnsExactOrClosedOverflow() {
        var exact = new PresentationCost(3L, 5L).add(new PresentationCost(7L, 11L));
        assertEquals(
                new PresentationCost(10L, 16L),
                ((PresentationCost.Arithmetic.Exact) exact).value());

        assertSame(
                PresentationCost.Arithmetic.Overflow.INSTANCE,
                new PresentationCost(Long.MAX_VALUE, 0L).add(PresentationCost.count(1L)));
        assertSame(
                PresentationCost.Arithmetic.Overflow.INSTANCE,
                new PresentationCost(0L, Long.MAX_VALUE).add(PresentationCost.bytes(1L)));
    }

    @Test
    void checkedFanOutUsesBothDimensionsAndNeverAdoptsSeedOverflowRules() {
        var oneMaximumEvent = new PresentationCost(
                1L,
                PresentationLimits.MAX_LEGAL_EVENT_BODY_BYTES
                        + PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES);

        var fanOut = oneMaximumEvent.multiply(512L);

        assertEquals(
                new PresentationCost(512L, 474_112L),
                ((PresentationCost.Arithmetic.Exact) fanOut).value());
        assertEquals(
                PresentationCost.zero(),
                ((PresentationCost.Arithmetic.Exact) oneMaximumEvent.multiply(0L)).value());
        assertSame(
                PresentationCost.Arithmetic.Overflow.INSTANCE,
                PresentationCost.count(Long.MAX_VALUE).multiply(2L));
        assertSame(
                PresentationCost.Arithmetic.Overflow.INSTANCE,
                PresentationCost.bytes(Long.MAX_VALUE).multiply(2L));
        assertThrows(IllegalArgumentException.class, () -> oneMaximumEvent.multiply(-1L));
        assertTrue(PresentationSequence.visualSeed(2L) < 0L);
    }
}
