package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class P6ManaBoundsTest {
    @Test
    void fixedBoundsMatchP6Authority() {
        assertEquals(1_000_000_000L, P6ManaBounds.MAX_MANA_VALUE);
        assertEquals(1_000_000_000L, P6ManaBounds.MAX_MANA_OPERATION_AMOUNT);
        assertEquals(2, P6ManaBounds.MAX_MANA_MUTATIONS_PER_EFFECT_EXECUTION);
        assertEquals(1L, P6ManaBounds.MIN_MUTATION_AMOUNT);
    }

    @Test
    void zeroBalanceIsAvailable() {
        assertEquals(0L, ManaState.available(0L).balance());
    }

    @Test
    void maximumBalanceIsAvailable() {
        assertEquals(
                P6ManaBounds.MAX_MANA_VALUE,
                ManaState.available(P6ManaBounds.MAX_MANA_VALUE).balance());
    }

    @Test
    void negativeBalanceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ManaState.available(-1L));
    }

    @Test
    void balanceAboveMaximumIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ManaState.available(P6ManaBounds.MAX_MANA_VALUE + 1L));
    }
}
