package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class PresentationSequenceTest {
    @Test
    void initialAllocationIsOneAndLeavesOriginalStateImmutable() {
        var initial = PresentationSequence.initial();

        var allocation = initial.allocate();

        assertEquals(PresentationSequence.Outcome.ALLOCATED, allocation.outcome());
        assertEquals(1L, allocation.sequence().orElseThrow());
        assertEquals(1_240_066_097_855_784_765L, allocation.visualSeed().orElseThrow());
        assertEquals(1L, initial.nextValue().orElseThrow());
        assertEquals(2L, allocation.nextState().nextValue().orElseThrow());
        assertFalse(initial.exhausted());
    }

    @Test
    void exactStaffordMixThirteenVectorsUseJavaWraparound() {
        assertEquals(0x1135998BBCB2E73DL, PresentationSequence.visualSeed(1L));
        assertEquals(0xFCF2E89F62C430B5L, PresentationSequence.visualSeed(2L));
        assertEquals(0x6BBA659B3D932220L, PresentationSequence.visualSeed(3L));
        assertEquals(
                0x43C5A0F2DD7630A5L,
                PresentationSequence.visualSeed(Long.MAX_VALUE));
    }

    @Test
    void maximumIsAllocatedOnceThenStateIsExhaustedWithoutWrap() {
        assertTrue(Modifier.isPrivate(
                PresentationSequence.Allocation.class.getDeclaredConstructors()[0].getModifiers()));
        var maximum = PresentationSequence.expecting(Long.MAX_VALUE);

        var last = maximum.allocate();

        assertEquals(Long.MAX_VALUE, last.sequence().orElseThrow());
        assertTrue(last.nextState().exhausted());
        assertTrue(last.nextState().nextValue().isEmpty());

        var exhausted = last.nextState().allocate();
        assertEquals(PresentationSequence.Outcome.EXHAUSTED, exhausted.outcome());
        assertTrue(exhausted.sequence().isEmpty());
        assertTrue(exhausted.visualSeed().isEmpty());
        assertSame(last.nextState(), exhausted.nextState());
    }

    @Test
    void nonpositiveSequenceIsAnInvariantFailure() {
        assertThrows(IllegalArgumentException.class, () -> PresentationSequence.expecting(0L));
        assertThrows(IllegalArgumentException.class, () -> PresentationSequence.expecting(-1L));
        assertThrows(IllegalArgumentException.class, () -> PresentationSequence.visualSeed(0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationSequence.visualSeed(Long.MIN_VALUE));
    }
}
