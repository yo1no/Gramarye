package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EffectExecutionResultTest {
    @Test
    void acceptsEveryLegalTerminalCombination() {
        assertAll(
                () -> assertEquals(
                        EffectTerminalStatus.SUCCEEDED,
                        succeeded().status()),
                () -> assertEquals(
                        EffectTerminalStatus.REJECTED,
                        rejected().status()),
                () -> assertEquals(
                        EffectTerminalStatus.FAILED,
                        failed().status()),
                () -> assertEquals(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        partial().status()),
                () -> assertEquals(
                        EffectTerminalStatus.COMPENSATED,
                        compensated().status()),
                () -> assertEquals(
                        EffectTerminalStatus.COMPENSATION_FAILED,
                        compensationFailed().status()));
    }

    @Test
    void acceptsCompensatedGuardFailureOnlyAtBeforeStepZero() {
        EffectExecutionResult result = result(
                EffectTerminalStatus.COMPENSATED,
                Optional.empty(),
                Optional.of(EffectFailureReason.EXECUTION_CANCELLED),
                0,
                1,
                0,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.REFUND_APPLIED,
                EffectTraceStage.TERMINAL_COMPENSATED);
        assertEquals(EffectTerminalStatus.COMPENSATED, result.status());
        assertThrows(P6ExecutionInvariantException.class, () -> result(
                EffectTerminalStatus.COMPENSATED,
                Optional.empty(),
                Optional.of(EffectFailureReason.EXECUTION_CANCELLED),
                -1,
                1,
                0,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.REFUND_APPLIED,
                EffectTraceStage.TERMINAL_COMPENSATED));
    }

    @Test
    void rejectsMissingRequiredReasons() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.REJECTED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        0,
                        0,
                        0,
                        EffectTraceStage.TERMINAL_REJECTED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.FAILED,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1,
                        1,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_FAILED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                        EffectTraceStage.TERMINAL_PARTIAL)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATED,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1,
                        1,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.REFUND_APPLIED,
                        EffectTraceStage.TERMINAL_COMPENSATED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATION_FAILED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        0,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.REFUND_FAILED,
                        EffectTraceStage.TERMINAL_COMPENSATION_FAILED)));
    }

    @Test
    void rejectsForbiddenReasonsForEveryStatus() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.of(EffectRejectReason.INVALID_REQUEST),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.REJECTED,
                        Optional.of(EffectRejectReason.INVALID_REQUEST),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                        -1,
                        0,
                        0,
                        0,
                        EffectTraceStage.TERMINAL_REJECTED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.FAILED,
                        Optional.of(EffectRejectReason.INVALID_REQUEST),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                        0,
                        1,
                        1,
                        0,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_FAILED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                        -1,
                        1,
                        0,
                        1,
                        EffectTraceStage.TERMINAL_PARTIAL)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                        -1,
                        1,
                        0,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.REFUND_APPLIED,
                        EffectTraceStage.TERMINAL_COMPENSATED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE),
                        0,
                        1,
                        1,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                        EffectTraceStage.REFUND_APPLIED,
                        EffectTraceStage.TERMINAL_COMPENSATED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATION_FAILED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                        -1,
                        1,
                        0,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.REFUND_FAILED,
                        EffectTraceStage.TERMINAL_COMPENSATION_FAILED)));
    }

    @Test
    void rejectsInvalidMutationCountsForEveryStatus() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        succeeded(), 1, 1, 0)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        rejected(), 0, 0, 1)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        failed(), 1, 1, 1)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        partial(), 1, 1, 0)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        compensated(), 1, 1, 1)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        compensationFailed(), 1, 0, 1)));
    }

    @Test
    void rejectsInvalidExecutedCountsForEveryStatus() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        succeeded(), 1, 0, 1)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        rejected(), 1, 1, 0)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        failed(), 1, 0, 0)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        partial(), 1, 0, 1)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        compensated(), 1, 0, 0)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> copyWithCounts(
                        compensationFailed(), 1, 2, 0)));
    }

    @Test
    void rejectsTerminalTraceMismatchForEveryStatus() {
        for (EffectExecutionResult valid : new EffectExecutionResult[] {
            succeeded(), rejected(), failed(), partial(), compensated(), compensationFailed()
        }) {
            EffectTrace wrong = EffectTestFixtures.trace(EffectTraceStage.TERMINAL_REJECTED);
            if (valid.status() == EffectTerminalStatus.REJECTED) {
                wrong = EffectTestFixtures.trace(EffectTraceStage.TERMINAL_SUCCEEDED);
            }
            EffectTrace finalWrong = wrong;
            assertThrows(P6ExecutionInvariantException.class, () -> new EffectExecutionResult(
                    valid.status(),
                    valid.rejectReason(),
                    valid.failureReason(),
                    valid.failureStepIndex(),
                    valid.plannedStepCount(),
                    valid.executedStepCount(),
                    valid.primaryMutationCount(),
                    finalWrong));
        }
    }

    @Test
    void enforcesFailureReasonStepCoordinatesAndExecutedTraceCount() {
        assertThrows(P6ExecutionInvariantException.class, () -> result(
                EffectTerminalStatus.FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                1,
                2,
                1,
                0,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.TERMINAL_FAILED));
        assertThrows(P6ExecutionInvariantException.class, () -> result(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                Optional.empty(),
                Optional.of(EffectFailureReason.EXECUTION_CANCELLED),
                1,
                2,
                0,
                1,
                EffectTraceStage.TERMINAL_PARTIAL));
        assertThrows(P6ExecutionInvariantException.class, () -> new EffectExecutionResult(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                -1,
                1,
                1,
                1,
                EffectTestFixtures.trace(EffectTraceStage.TERMINAL_SUCCEEDED)));
    }

    @Test
    void rejectsStepTraceThatContradictsTerminalAndFailureSemantics() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.FAILED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                        0,
                        1,
                        1,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_FAILED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE),
                        0,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.EXECUTION_CANCELLED),
                        1,
                        2,
                        1,
                        1,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.COMPENSATION_FAILED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                        -1,
                        1,
                        1,
                        0,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.REFUND_FAILED,
                        EffectTraceStage.TERMINAL_COMPENSATION_FAILED)));
    }

    @Test
    void rejectsEarlyTerminalAndRefundStagesOutsideCompensation() {
        assertThrows(P6ExecutionInvariantException.class, () -> new EffectExecutionResult(
                EffectTerminalStatus.REJECTED,
                Optional.of(EffectRejectReason.INVALID_REQUEST),
                Optional.empty(),
                -1,
                0,
                0,
                0,
                EffectTestFixtures.trace(
                        EffectTraceStage.TERMINAL_FAILED,
                        EffectTraceStage.TERMINAL_REJECTED)));
        assertThrows(P6ExecutionInvariantException.class, () -> new EffectExecutionResult(
                EffectTerminalStatus.REJECTED,
                Optional.of(EffectRejectReason.INVALID_REQUEST),
                Optional.empty(),
                -1,
                0,
                0,
                0,
                EffectTestFixtures.trace(
                        EffectTraceStage.REFUND_APPLIED,
                        EffectTraceStage.TERMINAL_REJECTED)));
    }

    @Test
    void compensationFailureRejectsMultipleOriginalStepFailuresOrMissingDebit() {
        assertThrows(P6ExecutionInvariantException.class, () -> result(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                2,
                2,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED));
        assertThrows(P6ExecutionInvariantException.class, () -> result(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                1,
                0,
                0,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED));
    }

    @Test
    void rejectsPartialResultsWithoutARealPriorOrFailingMutation() {
        P6ExecutionInvariantException firstNotApplied = assertThrows(
                P6ExecutionInvariantException.class,
                () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                        0,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL));
        P6ExecutionInvariantException guardBeforeFirstStep = assertThrows(
                P6ExecutionInvariantException.class,
                () -> result(
                        EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                        Optional.empty(),
                        Optional.of(EffectFailureReason.EXECUTION_CANCELLED),
                        0,
                        1,
                        0,
                        1,
                        EffectTraceStage.TERMINAL_PARTIAL));
        assertAll(
                () -> assertEquals(
                        P6ExecutionInvariantCode.IMPOSSIBLE_RESULT,
                        firstNotApplied.code()),
                () -> assertEquals(
                        P6ExecutionInvariantCode.IMPOSSIBLE_RESULT,
                        guardBeforeFirstStep.code()));
    }

    @Test
    void rejectsMutationTotalBelowMutationProducingStepCount() {
        EffectTraceStage[] stages = new EffectTraceStage[9];
        java.util.Arrays.fill(stages, 0, 8, EffectTraceStage.STEP_APPLIED);
        stages[8] = EffectTraceStage.TERMINAL_SUCCEEDED;
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        8,
                        8,
                        1,
                        stages));
        assertEquals(P6ExecutionInvariantCode.IMPOSSIBLE_RESULT, failure.code());
    }

    @Test
    void rejectsDuplicateOrOutOfOrderStateMachineStages() {
        assertAll(
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)),
                () -> assertThrows(P6ExecutionInvariantException.class, () -> result(
                        EffectTerminalStatus.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        -1,
                        1,
                        1,
                        1,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED)));
    }

    @Test
    void compensationFailureMayFollowOneZeroMutationPrimaryFailure() {
        EffectExecutionResult result = result(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                1,
                1,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED);
        assertAll(
                () -> assertEquals(EffectTerminalStatus.COMPENSATION_FAILED, result.status()),
                () -> assertEquals(1, result.executedStepCount()),
                () -> assertEquals(0, result.primaryMutationCount()));
    }

    private static EffectExecutionResult succeeded() {
        return result(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                -1,
                1,
                1,
                1,
                EffectTraceStage.STEP_APPLIED,
                EffectTraceStage.TERMINAL_SUCCEEDED);
    }

    private static EffectExecutionResult rejected() {
        return result(
                EffectTerminalStatus.REJECTED,
                Optional.of(EffectRejectReason.INVALID_REQUEST),
                Optional.empty(),
                -1,
                0,
                0,
                0,
                EffectTraceStage.TERMINAL_REJECTED);
    }

    private static EffectExecutionResult failed() {
        return result(
                EffectTerminalStatus.FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                0,
                1,
                1,
                0,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.TERMINAL_FAILED);
    }

    private static EffectExecutionResult partial() {
        return result(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE),
                0,
                1,
                1,
                1,
                EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                EffectTraceStage.TERMINAL_PARTIAL);
    }

    private static EffectExecutionResult compensated() {
        return result(
                EffectTerminalStatus.COMPENSATED,
                Optional.empty(),
                Optional.of(EffectFailureReason.PRIMARY_STEP_NOT_APPLIED),
                0,
                1,
                1,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.STEP_NOT_APPLIED,
                EffectTraceStage.REFUND_APPLIED,
                EffectTraceStage.TERMINAL_COMPENSATED);
    }

    private static EffectExecutionResult compensationFailed() {
        return result(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                -1,
                1,
                0,
                0,
                EffectTraceStage.MANA_DEBITED,
                EffectTraceStage.REFUND_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED);
    }

    private static EffectExecutionResult result(
            EffectTerminalStatus status,
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int planned,
            int executed,
            int mutations,
            EffectTraceStage... stages) {
        return EffectTestFixtures.result(
                status,
                rejectReason,
                failureReason,
                failureStepIndex,
                planned,
                executed,
                mutations,
                stages);
    }

    private static EffectExecutionResult copyWithCounts(
            EffectExecutionResult source, int planned, int executed, int mutations) {
        return new EffectExecutionResult(
                source.status(),
                source.rejectReason(),
                source.failureReason(),
                source.failureStepIndex(),
                planned,
                executed,
                mutations,
                source.trace());
    }
}
