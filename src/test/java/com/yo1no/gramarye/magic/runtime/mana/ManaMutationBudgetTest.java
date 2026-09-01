package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ManaMutationBudgetTest {
    @Test
    void freshBudgetStartsAtZero() {
        var budget = new ManaMutationBudget();
        assertEquals(0, budget.consumed());
        assertEquals(2, budget.remaining());
    }

    @Test
    void firstAndSecondMutationConsumeExactCapacity() {
        var budget = new ManaMutationBudget();
        assertTrue(budget.tryConsume());
        assertEquals(1, budget.consumed());
        assertEquals(1, budget.remaining());
        assertTrue(budget.tryConsume());
        assertEquals(2, budget.consumed());
        assertEquals(0, budget.remaining());
    }

    @Test
    void thirdMutationIsRejectedWithoutNegativeRemaining() {
        var budget = new ManaMutationBudget();
        assertTrue(budget.tryConsume());
        assertTrue(budget.tryConsume());
        assertFalse(budget.tryConsume());
        assertEquals(2, budget.consumed());
        assertEquals(0, budget.remaining());
    }

    @Test
    void rejectedConsumptionDoesNotResetBudget() {
        var budget = new ManaMutationBudget();
        budget.tryConsume();
        budget.tryConsume();
        budget.tryConsume();
        assertFalse(budget.tryConsume());
        assertEquals(2, budget.consumed());
    }

    @Test
    void separateCallsUseSeparateBudgetState() {
        var first = new ManaMutationBudget();
        var second = new ManaMutationBudget();
        first.tryConsume();
        first.tryConsume();
        assertFalse(first.tryConsume());
        assertEquals(0, second.consumed());
        assertTrue(second.tryConsume());
    }
}
