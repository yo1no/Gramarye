package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class EffectExecutionEngineSuccessTest {
    @Test
    void oneStepSuccessHasExactCountersOrderAndTrace() {
        RecordingEffectGuard guard = RecordingEffectGuard.allowing();
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = execute(EffectTestFixtures.plan(1), guard, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.status());
        assertEquals(1, result.plannedStepCount());
        assertEquals(1, result.executedStepCount());
        assertEquals(1, result.primaryMutationCount());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED),
                stages(result));
    }

    @Test
    void eightStepSuccessUsesExactOrderOnceAndTenGuardChecks() {
        RecordingEffectGuard guard = RecordingEffectGuard.allowing();
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = execute(EffectTestFixtures.plan(8), guard, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.status());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), port.committedIndexes());
        assertEquals(8, result.executedStepCount());
        assertEquals(8, result.primaryMutationCount());
        assertEquals(P6EffectBounds.MAX_DEADLINE_CHECKS_PER_EXECUTION, guard.checks().size());
        assertEquals(EffectGuardPoint.entry(), guard.checks().getFirst());
        assertEquals(EffectGuardPoint.preCommit(), guard.checks().get(1));
        for (int index = 0; index < 8; index++) {
            assertEquals(EffectGuardPoint.beforeStep(index), guard.checks().get(index + 2));
        }
        List<EffectTraceStage> expectedTrace = new ArrayList<>();
        expectedTrace.add(EffectTraceStage.REQUEST_VALIDATED);
        expectedTrace.add(EffectTraceStage.TARGET_RESOLVED);
        for (int index = 0; index < 8; index++) {
            expectedTrace.add(EffectTraceStage.STEP_APPLIED);
        }
        expectedTrace.add(EffectTraceStage.TERMINAL_SUCCEEDED);
        assertEquals(expectedTrace, stages(result));
    }

    @Test
    void actualPrimaryMutationContributionsAreSummed() {
        EffectCommitPlan plan = new EffectCommitPlan(
                List.of(EffectTestFixtures.step(0, 3, 0)), 0);
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(
                true, List.of(EffectStepOutcome.applied(3)));
        EffectExecutionResult result = execute(plan, RecordingEffectGuard.allowing(), port);
        assertEquals(3, result.primaryMutationCount());
    }

    @Test
    void equivalentRepeatedExecutionsAreDeterministic() {
        EffectCommitPlan plan = EffectTestFixtures.plan(3);
        EffectExecutionResult first = execute(
                plan,
                RecordingEffectGuard.allowing(),
                RecordingDamageCommitPort.applyingAll());
        EffectExecutionResult second = execute(
                plan,
                RecordingEffectGuard.allowing(),
                RecordingDamageCommitPort.applyingAll());
        assertEquals(first, second);
    }

    private static EffectExecutionResult execute(
            EffectCommitPlan plan,
            EffectExecutionGuard guard,
            DamageEffectCommitPort port) {
        return new EffectExecutionEngine().execute(
                EffectTestFixtures.request(),
                EffectTestFixtures.resolverFor(plan),
                guard,
                port);
    }

    private static List<EffectTraceStage> stages(EffectExecutionResult result) {
        return result.trace().entries().stream().map(EffectTraceEntry::stage).toList();
    }
}
