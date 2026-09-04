package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

final class IntentTickBudgetTest {
    @Test
    void initialStateRetainsKindTickAndZeroUsage() {
        assertArrayEquals(
                new IntentTickBudget.Kind[] {
                    IntentTickBudget.Kind.PLAYER_INGRESS,
                    IntentTickBudget.Kind.GLOBAL_WORK
                },
                IntentTickBudget.Kind.values());
        assertArrayEquals(
                new IntentTickBudget.Outcome[] {
                    IntentTickBudget.Outcome.ADMITTED,
                    IntentTickBudget.Outcome.DENIED,
                    IntentTickBudget.Outcome.INTERNAL_SERVER_FAULT
                },
                IntentTickBudget.Outcome.values());
        assertEquals(8, IntentTickBudget.Kind.PLAYER_INGRESS.limit());
        assertEquals(64, IntentTickBudget.Kind.GLOBAL_WORK.limit());
        for (var kind : IntentTickBudget.Kind.values()) {
            var budget = IntentTickBudget.initial(kind, 9L);
            assertEquals(kind, budget.kind());
            assertEquals(9L, budget.currentTick());
            assertEquals(0, budget.used());
            var first = budget.consume(9L);
            assertEquals(IntentTickBudget.Outcome.ADMITTED, first.outcome());
            assertEquals(1, first.nextState().used());
        }
    }

    @Test
    void playerBudgetAdmitsEightAndRejectsOneOver() {
        var budget = IntentTickBudget.initial(IntentTickBudget.Kind.PLAYER_INGRESS, 2L);
        for (var index = 0; index < 8; index++) {
            var decision = budget.consume(2L);
            assertEquals(IntentTickBudget.Outcome.ADMITTED, decision.outcome());
            budget = decision.nextState();
        }

        var denied = budget.consume(2L);
        assertEquals(IntentTickBudget.Outcome.DENIED, denied.outcome());
        assertSame(budget, denied.nextState());
    }

    @Test
    void globalBudgetAdmitsSixtyFourAndRejectsOneOver() {
        var budget = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 4L);
        for (var index = 0; index < 64; index++) {
            var decision = budget.consume(4L);
            assertEquals(IntentTickBudget.Outcome.ADMITTED, decision.outcome());
            budget = decision.nextState();
        }

        var denied = budget.consume(4L);
        assertEquals(64, budget.used());
        assertEquals(IntentTickBudget.Outcome.DENIED, denied.outcome());
        assertSame(budget, denied.nextState());
    }

    @Test
    void greaterTickResetsThenConsumesOne() {
        for (var kind : IntentTickBudget.Kind.values()) {
            var budget = IntentTickBudget.initial(kind, 4L)
                    .consume(4L)
                    .nextState()
                    .consume(4L)
                    .nextState();

            var decision = budget.consume(5L);

            assertEquals(IntentTickBudget.Outcome.ADMITTED, decision.outcome());
            assertEquals(1, decision.nextState().used());
            assertEquals(5L, decision.nextState().currentTick());
        }
    }

    @Test
    void tickRegressionIsAnInternalFaultWithOriginalState() {
        for (var kind : IntentTickBudget.Kind.values()) {
            var budget = IntentTickBudget.initial(kind, 5L);

            var decision = budget.consume(4L);

            assertEquals(IntentTickBudget.Outcome.INTERNAL_SERVER_FAULT, decision.outcome());
            assertSame(budget, decision.nextState());
        }
    }

    @Test
    void extremeMonotonicTickAdvanceDoesNotOverflow() {
        for (var kind : IntentTickBudget.Kind.values()) {
            var budget = IntentTickBudget.initial(kind, Long.MIN_VALUE);

            var decision = budget.consume(Long.MAX_VALUE);

            assertEquals(IntentTickBudget.Outcome.ADMITTED, decision.outcome());
            assertEquals(1, decision.nextState().used());
            assertEquals(Long.MAX_VALUE, decision.nextState().currentTick());
        }
    }

    @Test
    void equalInputsProduceEqualDecisionsAndStates() {
        for (var kind : IntentTickBudget.Kind.values()) {
            var left = IntentTickBudget.initial(kind, 3L).consume(3L);
            var right = IntentTickBudget.initial(kind, 3L).consume(3L);

            assertEquals(left.outcome(), right.outcome());
            assertEquals(left.nextState(), right.nextState());
        }
    }
}
