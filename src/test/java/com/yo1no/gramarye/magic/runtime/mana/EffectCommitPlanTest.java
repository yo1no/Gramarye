package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EffectCommitPlanTest {
    @Test
    void acceptsOneAndEightOrderedSteps() {
        EffectCommitPlan one = EffectTestFixtures.plan(1);
        EffectCommitPlan eight = EffectTestFixtures.plan(8);
        assertEquals(1, one.steps().size());
        assertEquals(8, eight.steps().size());
        assertEquals(8, eight.declaredPrimaryMutationCount());
        for (int index = 0; index < eight.steps().size(); index++) {
            assertEquals(index, eight.steps().get(index).index());
        }
    }

    @Test
    void rejectsEmptyAndNineStepPlans() {
        assertThrows(IllegalArgumentException.class, () -> new EffectCommitPlan(List.of(), 0));
        List<EffectStep> nineSteps = Collections.nCopies(9, EffectTestFixtures.step(0));
        assertThrows(IllegalArgumentException.class, () -> new EffectCommitPlan(nineSteps, 0));
    }

    @Test
    void rejectsNullListAndNullStep() {
        assertThrows(NullPointerException.class, () -> new EffectCommitPlan(null, 0));
        List<EffectStep> withNull = Arrays.asList(EffectTestFixtures.step(0), null);
        assertThrows(NullPointerException.class, () -> new EffectCommitPlan(withNull, 0));
    }

    @Test
    void rejectsDuplicateGapAndOutOfOrderIndexes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(EffectTestFixtures.step(0), EffectTestFixtures.step(0)), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(EffectTestFixtures.step(0), EffectTestFixtures.step(2)), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(EffectTestFixtures.step(1), EffectTestFixtures.step(0)), 0));
    }

    @Test
    void rejectsNonpositiveDeclaredPrimaryMutation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTestFixtures.step(0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTestFixtures.step(0, -1, 0));
    }

    @Test
    void rejectsDeclaredPrimaryMutationSumAboveEight() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(
                                EffectTestFixtures.step(0, 8, 0),
                                EffectTestFixtures.step(1, 1, 0)),
                        0));
    }

    @Test
    void checksDeclaredChildDemandAgainstSuppliedCapacity() {
        EffectCommitPlan exact = new EffectCommitPlan(
                List.of(EffectTestFixtures.step(0, 1, 32)), 32);
        assertEquals(32, exact.declaredChildIntentCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(EffectTestFixtures.step(0, 1, 2)), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTestFixtures.step(0, 1, 33));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(
                                EffectTestFixtures.step(0, 1, 16),
                                EffectTestFixtures.step(1, 1, 17)),
                        32));
    }

    @Test
    void rejectsInvalidSuppliedChildCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(List.of(EffectTestFixtures.step(0)), -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectCommitPlan(
                        List.of(EffectTestFixtures.step(0)),
                        P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION + 1));
    }

    @Test
    void planDefensivelyCopiesAndReturnsImmutableSteps() {
        List<EffectStep> original = new ArrayList<>();
        original.add(EffectTestFixtures.step(0));
        EffectCommitPlan plan = new EffectCommitPlan(original, 0);
        original.clear();
        assertEquals(1, plan.steps().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.steps().add(EffectTestFixtures.step(1)));
    }
}
