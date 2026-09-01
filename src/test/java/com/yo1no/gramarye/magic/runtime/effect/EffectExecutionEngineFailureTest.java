package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class EffectExecutionEngineFailureTest {
    @Test
    void firstNotAppliedFailsAndStopsWithoutMutationOrRetry() {
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(
                true,
                List.of(
                        EffectStepOutcome.notApplied(),
                        EffectStepOutcome.applied(1)));
        EffectExecutionResult result = execute(EffectTestFixtures.plan(3), port);

        assertEquals(EffectTerminalStatus.FAILED, result.status());
        assertEquals(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED, result.failureReason().orElseThrow());
        assertEquals(0, result.failureStepIndex());
        assertEquals(1, result.executedStepCount());
        assertEquals(0, result.primaryMutationCount());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_FAILED),
                stages(result));
    }

    @Test
    void laterNotAppliedIsPartialAndStopsRemainingSteps() {
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(
                true,
                List.of(
                        EffectStepOutcome.applied(1),
                        EffectStepOutcome.notApplied(),
                        EffectStepOutcome.applied(1)));
        EffectExecutionResult result = execute(EffectTestFixtures.plan(3), port);

        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.status());
        assertEquals(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED, result.failureReason().orElseThrow());
        assertEquals(1, result.failureStepIndex());
        assertEquals(2, result.executedStepCount());
        assertEquals(1, result.primaryMutationCount());
        assertEquals(List.of(0, 1), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL),
                stages(result));
    }

    @Test
    void firstAppliedWithFailureIsAlwaysPartial() {
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(
                true, List.of(EffectStepOutcome.appliedWithFailure(1)));
        EffectExecutionResult result = execute(EffectTestFixtures.plan(3), port);

        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.status());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                result.failureReason().orElseThrow());
        assertEquals(0, result.failureStepIndex());
        assertEquals(1, result.executedStepCount());
        assertEquals(1, result.primaryMutationCount());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                        EffectTraceStage.TERMINAL_PARTIAL),
                stages(result));
    }

    @Test
    void laterAppliedWithFailureIsPartialAndStopsRemainingSteps() {
        RecordingDamageCommitPort port = new RecordingDamageCommitPort(
                true,
                List.of(
                        EffectStepOutcome.applied(1),
                        EffectStepOutcome.appliedWithFailure(1),
                        EffectStepOutcome.applied(1)));
        EffectExecutionResult result = execute(EffectTestFixtures.plan(3), port);

        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.status());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                result.failureReason().orElseThrow());
        assertEquals(1, result.failureStepIndex());
        assertEquals(2, result.executedStepCount());
        assertEquals(2, result.primaryMutationCount());
        assertEquals(List.of(0, 1), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                        EffectTraceStage.TERMINAL_PARTIAL),
                stages(result));
    }

    @Test
    void normalS1EngineNeverProducesCompensationTerminalOrStage() {
        for (RecordingDamageCommitPort port : List.of(
                RecordingDamageCommitPort.applyingAll(),
                new RecordingDamageCommitPort(true, List.of(EffectStepOutcome.notApplied())),
                new RecordingDamageCommitPort(
                        true, List.of(EffectStepOutcome.appliedWithFailure(1))))) {
            EffectExecutionResult result = execute(EffectTestFixtures.plan(1), port);
            if (result.status() == EffectTerminalStatus.COMPENSATED
                    || result.status() == EffectTerminalStatus.COMPENSATION_FAILED
                    || stages(result).stream().anyMatch(stage -> stage == EffectTraceStage.REFUND_APPLIED
                            || stage == EffectTraceStage.REFUND_FAILED
                            || stage == EffectTraceStage.TERMINAL_COMPENSATED
                            || stage == EffectTraceStage.TERMINAL_COMPENSATION_FAILED)) {
                throw new AssertionError(result);
            }
        }
    }

    private static EffectExecutionResult execute(
            EffectCommitPlan plan, RecordingDamageCommitPort port) {
        return new EffectExecutionEngine(
                EffectTestFixtures.resolverFor(plan), RecordingEffectGuard.allowing(), port)
                .execute(EffectTestFixtures.request());
    }

    private static List<EffectTraceStage> stages(EffectExecutionResult result) {
        return result.trace().entries().stream().map(EffectTraceEntry::stage).toList();
    }
}
