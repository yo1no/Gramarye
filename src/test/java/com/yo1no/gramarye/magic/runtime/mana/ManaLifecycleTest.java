package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.junit.jupiter.api.Test;

final class ManaLifecycleTest {
    @Test
    void availableDeathCopyPreservesExactBalance() {
        assertFreshExactCopy(ManaState.available(41L));
    }

    @Test
    void availableNonDeathCopyPreservesExactBalance() {
        assertFreshExactCopy(ManaState.available(P6ManaBounds.MAX_MANA_VALUE));
    }

    @Test
    void unavailableDeathCopyRemainsUnavailable() {
        assertFreshExactCopy(ManaState.unavailable(ManaDecodeFailure.EXTRA_FIELD));
    }

    @Test
    void unavailableNonDeathCopyRemainsUnavailable() {
        assertFreshExactCopy(ManaState.unavailable(ManaDecodeFailure.WRONG_ROOT_TYPE));
    }

    @Test
    void copyDoesNotMutateSource() {
        var source = ManaState.available(777L);
        ManaLifecycle.copy(source, null, null);
        assertEquals(ManaAvailability.AVAILABLE, source.availability());
        assertEquals(777L, source.balance());
    }

    @Test
    void unavailableCopyPreservesClosedFailureWithoutRawPayload() {
        var source = ManaState.unavailable(ManaDecodeFailure.WRONG_BALANCE_TYPE);
        var copied = ManaLifecycle.copy(source, null, null);
        assertEquals(ManaAvailability.UNAVAILABLE, copied.availability());
        assertEquals(ManaDecodeFailure.WRONG_BALANCE_TYPE, copied.unavailableReason());
    }

    private static void assertFreshExactCopy(ManaState source) {
        var copied = ManaLifecycle.copy(source, null, null);
        assertEquals(source, copied);
        assertNotSame(source, copied);
    }
}
