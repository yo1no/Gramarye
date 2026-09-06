package com.yo1no.gramarye.magic.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

final class PresentationBudgetTest {
    @Test
    void everyScopeAcceptsZeroMinimumAndExactMaximumButRejectsOneOver() {
        for (var scope : PresentationBudget.Scope.values()) {
            var zero = PresentationBudget.initial(scope).consume(PresentationCost.zero());
            assertEquals(PresentationBudget.Outcome.ACCEPTED, zero.outcome(), scope.name());
            assertEquals(PresentationCost.zero(), zero.nextState().used(), scope.name());

            var minimum = supportedCost(scope, 1L, 1L);
            assertEquals(
                    PresentationBudget.Outcome.ACCEPTED,
                    PresentationBudget.initial(scope).consume(minimum).outcome(),
                    scope.name());

            var maximum = supportedCost(
                    scope,
                    scope.maximumCount().orElse(0L),
                    scope.maximumBytes().orElse(0L));
            var atMaximum = PresentationBudget.initial(scope).consume(maximum);
            assertEquals(PresentationBudget.Outcome.ACCEPTED, atMaximum.outcome(), scope.name());
            assertEquals(maximum, atMaximum.nextState().used(), scope.name());

            if (scope.maximumCount().isPresent()) {
                var oneOver = supportedCost(
                        scope,
                        scope.maximumCount().orElseThrow() + 1L,
                        0L);
                assertRejectedUnchanged(
                        PresentationBudget.initial(scope),
                        oneOver,
                        PresentationBudget.Outcome.LIMIT_EXCEEDED);
            }
            if (scope.maximumBytes().isPresent()) {
                var oneOver = supportedCost(
                        scope,
                        0L,
                        scope.maximumBytes().orElseThrow() + 1L);
                assertRejectedUnchanged(
                        PresentationBudget.initial(scope),
                        oneOver,
                        PresentationBudget.Outcome.LIMIT_EXCEEDED);
            }
        }
    }

    @Test
    void exactScopeBoundsRemainIndependent() {
        assertScope(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SKILL, 8L, null);
        assertScope(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SOURCE, 32L, null);
        assertScope(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SERVER, 128L, null);
        assertScope(PresentationBudget.Scope.SERVER_EVENT_BUFFER, 128L, 131_072L);
        assertScope(PresentationBudget.Scope.DELIVERIES_PER_PLAYER, 16L, 32_768L);
        assertScope(PresentationBudget.Scope.DELIVERIES_PER_SERVER, 512L, 524_288L);
        assertScope(PresentationBudget.Scope.CLIENT_PENDING_EVENTS, 64L, 65_536L);
        assertScope(PresentationBudget.Scope.CLIENT_COMBINED_QUEUE, null, 573_440L);
        assertScope(PresentationBudget.Scope.CLIENT_EVENT_APPLICATION, 32L, null);
        assertScope(PresentationBudget.Scope.CLIENT_ACTIVE_PRESENTATIONS, 128L, null);
        assertScope(PresentationBudget.Scope.ONLINE_RECIPIENT_SCAN, 512L, null);
        assertScope(PresentationBudget.Scope.RECIPIENT_EVALUATIONS, 65_536L, null);
        assertScope(PresentationBudget.Scope.SELECTED_RECIPIENTS, 32L, null);
        assertScope(PresentationBudget.Scope.CANDIDATE_DELIVERIES, 4_096L, null);
        assertScope(PresentationBudget.Scope.CLIENT_FACTORY_CALLS, 192L, null);
        assertScope(PresentationBudget.Scope.ACTUAL_PARTICLE_STARTS, 512L, null);
        assertScope(PresentationBudget.Scope.LIVE_PARTICLE_CREDITS, 4_096L, null);
        assertScope(PresentationBudget.Scope.SOUND_STARTS, 8L, null);
        assertScope(PresentationBudget.Scope.ACTIVE_SOUNDS, 32L, null);
        assertScope(PresentationBudget.Scope.ACTIVE_TRAILS, 64L, null);
        assertScope(PresentationBudget.Scope.TOTAL_TRAIL_SEGMENTS, 1_536L, null);

        var skill = PresentationBudget.initial(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SKILL);
        var source = PresentationBudget.initial(PresentationBudget.Scope.LOGICAL_EVENTS_PER_SOURCE);
        var consumedSkill = skill.consume(PresentationCost.count(8L));

        assertEquals(PresentationCost.zero(), source.used());
        assertEquals(PresentationCost.count(8L), consumedSkill.nextState().used());
        assertEquals(PresentationCost.zero(), skill.used());
    }

    @Test
    void countAndBytesAreConjunctiveAndRejectionIsAtomic() {
        assertTrue(Modifier.isPrivate(
                PresentationBudget.Decision.class.getDeclaredConstructors()[0].getModifiers()));
        var budget = PresentationBudget.initial(PresentationBudget.Scope.SERVER_EVENT_BUFFER)
                .consume(new PresentationCost(127L, 131_000L))
                .nextState();

        assertRejectedUnchanged(
                budget,
                new PresentationCost(1L, 73L),
                PresentationBudget.Outcome.LIMIT_EXCEEDED);
        assertEquals(new PresentationCost(127L, 131_000L), budget.used());
    }

    @Test
    void unsupportedDimensionsAndCheckedOverflowFailClosed() {
        var countOnly = PresentationBudget.initial(
                PresentationBudget.Scope.LOGICAL_EVENTS_PER_SKILL);
        assertRejectedUnchanged(
                countOnly,
                PresentationCost.bytes(1L),
                PresentationBudget.Outcome.INCOMPATIBLE_COST);

        var byteOnly = PresentationBudget.initial(PresentationBudget.Scope.CLIENT_COMBINED_QUEUE);
        assertRejectedUnchanged(
                byteOnly,
                PresentationCost.count(1L),
                PresentationBudget.Outcome.INCOMPATIBLE_COST);

        var withOne = countOnly.consume(PresentationCost.count(1L)).nextState();
        assertRejectedUnchanged(
                withOne,
                PresentationCost.count(Long.MAX_VALUE),
                PresentationBudget.Outcome.ARITHMETIC_OVERFLOW);
    }

    @Test
    void checkedMaximumEventFanOutFitsTheIndependentServerDeliveryBudget() {
        var maximumEvent = new PresentationCost(1L, 926L);
        var fanOut = ((PresentationCost.Arithmetic.Exact) maximumEvent.multiply(512L)).value();

        var decision = PresentationBudget.initial(PresentationBudget.Scope.DELIVERIES_PER_SERVER)
                .consume(fanOut);

        assertEquals(PresentationBudget.Outcome.ACCEPTED, decision.outcome());
        assertEquals(new PresentationCost(512L, 474_112L), decision.nextState().used());
    }

    private static PresentationCost supportedCost(
            PresentationBudget.Scope scope, long count, long bytes) {
        return new PresentationCost(
                scope.maximumCount().isPresent() ? count : 0L,
                scope.maximumBytes().isPresent() ? bytes : 0L);
    }

    private static void assertScope(
            PresentationBudget.Scope scope, Long maximumCount, Long maximumBytes) {
        assertEquals(
                maximumCount == null ? null : maximumCount.longValue(),
                scope.maximumCount().isEmpty() ? null : scope.maximumCount().orElseThrow());
        assertEquals(
                maximumBytes == null ? null : maximumBytes.longValue(),
                scope.maximumBytes().isEmpty() ? null : scope.maximumBytes().orElseThrow());
    }

    private static void assertRejectedUnchanged(
            PresentationBudget budget,
            PresentationCost cost,
            PresentationBudget.Outcome outcome) {
        var decision = budget.consume(cost);
        assertEquals(outcome, decision.outcome());
        assertSame(budget, decision.nextState());
    }
}
