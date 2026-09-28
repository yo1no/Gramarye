package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P11ProvisionalAssociationTest {
    private final Object pa = new Object();
    private final Object a = new Object();
    private final Object b = new Object();
    private final Object source = new Object();

    private P11ProvisionalAssociation receipt() {
        return new P11ProvisionalAssociation(pa, a, b, source, 7, 11);
    }

    private boolean allowed(P11ProvisionalAssociation receipt) {
        return receipt.withdrawable(pa, b, source, 7, 11, true, true);
    }

    @Test void pristineExistingAssociationRestoresOnlyOnce() {
        var receipt = receipt();
        var calls = new AtomicInteger();
        assertTrue(receipt.withdraw(pa, b, source, 7, 11, true, true, calls::incrementAndGet));
        assertFalse(receipt.withdraw(pa, b, source, 7, 11, true, true, calls::incrementAndGet));
        assertEquals(1, calls.get());
        assertSame(a, receipt.previousActor());
        assertSame(b, receipt.candidateActor());
    }

    @Test void identitySourceVersionAndCompletenessAreIndependentRequirements() {
        var receipt = receipt();
        assertFalse(receipt.withdrawable(new Object(), b, source, 7, 11, true, true));
        assertFalse(receipt.withdrawable(pa, a, source, 7, 11, true, true));
        assertFalse(receipt.withdrawable(pa, b, new Object(), 7, 11, true, true));
        assertFalse(receipt.withdrawable(pa, b, source, 8, 11, true, true));
        assertFalse(receipt.withdrawable(pa, b, source, 7, 12, true, true));
        assertFalse(receipt.withdrawable(pa, b, source, 7, 11, false, true));
        assertFalse(receipt.withdrawable(pa, b, source, 7, 11, true, false));
        assertTrue(allowed(receipt));
    }

    @Test void progressMutationUnknownCallbackAndCopyAreMonotonicExclusions() {
        var escaped = receipt();
        escaped.effectOrUnknownEscape();
        escaped.pendingConsumer();
        escaped.consumerTerminal();
        assertFalse(allowed(escaped));
        var copied = receipt();
        copied.copyStarted();
        assertFalse(allowed(copied));
    }

    @Test void pendingAndUnbalancedConsumersCannotBeGuessedTerminal() {
        var receipt = receipt();
        receipt.pendingConsumer();
        assertFalse(allowed(receipt));
        receipt.consumerTerminal();
        assertTrue(allowed(receipt));
        receipt.consumerTerminal();
        assertFalse(allowed(receipt));
    }

    @Test void restorationFailurePropagatesSameFaultAndCannotBeReplayed() {
        var receipt = receipt();
        var fault = new AssertionError("native fault");
        assertSame(fault, assertThrows(AssertionError.class,
                () -> receipt.withdraw(pa, b, source, 7, 11, true, true, () -> { throw fault; })));
        assertFalse(allowed(receipt));
    }

    @Test void deniedUnwindDoesNotRunRestorationOrBecomeReusable() {
        var receipt = receipt();
        assertFalse(receipt.withdraw(pa, b, source, 7, 11, false, true,
                () -> fail("unqualified restoration")));
        assertFalse(allowed(receipt));
    }
}
