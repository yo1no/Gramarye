package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayloadMigrationPlanTest {
    @Test
    void emptyPlanIsImmutableDeterministicAndCoversSchemaZero() {
        var plan = PayloadMigrationPlan.empty();

        assertTrue(plan.steps().isEmpty());
        assertTrue(plan.stepFrom(0).isEmpty());
        assertTrue(plan.verifyCoverage(0).error().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.steps().add(new PassThroughStep(0, 1)));
    }

    @Test
    void adjacentStepsAreCopiedSortedAndCoverCurrentVersion() {
        var zero = new PassThroughStep(0, 1);
        var one = new PassThroughStep(1, 2);
        var source = new ArrayList<PayloadMigrationStep>(List.of(one, zero));
        var plan = new PayloadMigrationPlan(source);
        source.clear();

        assertEquals(List.of(zero, one), plan.steps());
        assertSame(zero, plan.stepFrom(0).orElseThrow());
        assertSame(one, plan.stepFrom(1).orElseThrow());
        assertTrue(plan.stepFrom(2).isEmpty());
        assertTrue(plan.verifyCoverage(2).error().isEmpty());
    }

    @Test
    void rejectsNullDuplicateNegativeAndNonAdjacentSteps() {
        assertThrows(NullPointerException.class, () -> new PayloadMigrationPlan(null));
        assertThrows(
                NullPointerException.class,
                () -> new PayloadMigrationPlan(Arrays.asList(new PassThroughStep(0, 1), null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayloadMigrationPlan(List.of(
                        new PassThroughStep(0, 1), new PassThroughStep(0, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayloadMigrationPlan(List.of(new PassThroughStep(-1, 0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayloadMigrationPlan(List.of(new PassThroughStep(0, 2))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PayloadMigrationPlan(List.of(
                        new PassThroughStep(Integer.MAX_VALUE, Integer.MIN_VALUE))));
    }

    @Test
    void coverageRejectsMissingAndExtraEdgesWithoutPartial() {
        var missingMiddle = new PayloadMigrationPlan(List.of(
                new PassThroughStep(0, 1),
                new PassThroughStep(2, 3)));
        var extra = new PayloadMigrationPlan(List.of(new PassThroughStep(0, 1)));

        assertCompleteError(missingMiddle.verifyCoverage(3));
        assertCompleteError(extra.verifyCoverage(0));
        assertCompleteError(PayloadMigrationPlan.empty().verifyCoverage(-1));
    }

    @Test
    void stepOutputContainsOnlyMigratedPayload() {
        var components = PayloadMigrationStepOutput.class.getRecordComponents();
        assertEquals(1, components.length);
        assertEquals("migratedPayload", components[0].getName());
    }

    private static void assertCompleteError(DataResult<?> result) {
        assertTrue(result.error().isPresent());
        assertFalse(result.resultOrPartial().isPresent());
    }

    private record PassThroughStep(int fromVersion, int toVersion)
            implements PayloadMigrationStep {
        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            return DataResult.success(new PayloadMigrationStepOutput<>(defensivePayloadCopy));
        }
    }
}
