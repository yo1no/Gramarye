package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.Optional;

enum EffectTerminalStatus {
    SUCCEEDED,
    REJECTED,
    FAILED,
    PARTIALLY_SUCCEEDED,
    COMPENSATED,
    COMPENSATION_FAILED
}

enum EffectRejectReason {
    UNSUPPORTED_ACTION,
    INVALID_REQUEST,
    INVALID_TARGET,
    TARGET_UNAVAILABLE,
    INSUFFICIENT_MANA,
    MANA_STATE_UNAVAILABLE,
    BOUND_EXCEEDED,
    CANCELLED,
    DEADLINE_EXCEEDED,
    COMMIT_PORT_UNAVAILABLE
}

enum EffectFailureReason {
    PRIMARY_STEP_NOT_APPLIED,
    PRIMARY_STEP_APPLIED_WITH_FAILURE,
    EXECUTION_CANCELLED,
    EXECUTION_DEADLINE_EXCEEDED,
    COMPENSATION_REFUND_FAILED
}

record EffectExecutionResult(
        EffectTerminalStatus status,
        Optional<EffectRejectReason> rejectReason,
        Optional<EffectFailureReason> failureReason,
        int failureStepIndex,
        int plannedStepCount,
        int executedStepCount,
        int primaryMutationCount,
        EffectTrace trace) {
    static final int NOT_APPLICABLE_STEP_INDEX = -1;

    EffectExecutionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(rejectReason, "rejectReason");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(trace, "trace");
        trace = new EffectTrace(trace.entries());

        if (plannedStepCount < 0
                || plannedStepCount > P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN
                || executedStepCount < 0
                || executedStepCount > plannedStepCount
                || primaryMutationCount < 0
                || primaryMutationCount
                        > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION) {
            throw impossibleResult();
        }
        if (trace.terminalStage() != terminalStage(status)) {
            throw impossibleResult();
        }
        validateExecutedTrace(trace, executedStepCount);

        switch (status) {
            case SUCCEEDED -> validateSucceeded(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount);
            case REJECTED -> validateRejected(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    executedStepCount,
                    primaryMutationCount);
            case FAILED -> validateFailed(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount);
            case PARTIALLY_SUCCEEDED -> validatePartial(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount);
            case COMPENSATED -> validateCompensated(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount,
                    trace);
            case COMPENSATION_FAILED -> validateCompensationFailed(
                    rejectReason,
                    failureReason,
                    failureStepIndex,
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount,
                    trace);
        }
        validateTraceSemantics(status, failureReason, executedStepCount, trace);
    }

    private static void validateSucceeded(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount) {
        if (rejectReason.isPresent()
                || failureReason.isPresent()
                || failureStepIndex != NOT_APPLICABLE_STEP_INDEX
                || plannedStepCount == 0
                || executedStepCount != plannedStepCount
                || primaryMutationCount < executedStepCount) {
            throw impossibleResult();
        }
    }

    private static void validateRejected(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int executedStepCount,
            int primaryMutationCount) {
        if (rejectReason.isEmpty()
                || failureReason.isPresent()
                || failureStepIndex != NOT_APPLICABLE_STEP_INDEX
                || executedStepCount != 0
                || primaryMutationCount != 0) {
            throw impossibleResult();
        }
    }

    private static void validateFailed(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount) {
        if (rejectReason.isPresent()
                || !failureReason.equals(Optional.of(
                        EffectFailureReason.PRIMARY_STEP_NOT_APPLIED))
                || plannedStepCount == 0
                || executedStepCount != 1
                || failureStepIndex != 0
                || primaryMutationCount != 0) {
            throw impossibleResult();
        }
    }

    private static void validatePartial(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount) {
        if (rejectReason.isPresent()
                || failureReason.isEmpty()
                || failureReason.get() == EffectFailureReason.COMPENSATION_REFUND_FAILED
                || plannedStepCount == 0
                || primaryMutationCount == 0) {
            throw impossibleResult();
        }

        EffectFailureReason reason = failureReason.get();
        if (isGuardFailure(reason)) {
            validateGuardFailureIndex(failureStepIndex, plannedStepCount, executedStepCount);
            if (executedStepCount == 0 || primaryMutationCount < executedStepCount) {
                throw impossibleResult();
            }
        } else {
            validateInvokedFailureIndex(failureStepIndex, plannedStepCount, executedStepCount);
            if (reason == EffectFailureReason.PRIMARY_STEP_NOT_APPLIED) {
                if (failureStepIndex == 0 || primaryMutationCount < failureStepIndex) {
                    throw impossibleResult();
                }
            } else if (primaryMutationCount < executedStepCount) {
                throw impossibleResult();
            }
        }
    }

    private static void validateCompensated(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount,
            EffectTrace trace) {
        if (rejectReason.isPresent()
                || failureReason.isEmpty()
                || failureReason.get() == EffectFailureReason.COMPENSATION_REFUND_FAILED
                || failureReason.get()
                        == EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE
                || plannedStepCount == 0
                || primaryMutationCount != 0
                || !hasPenultimateStage(trace, EffectTraceStage.REFUND_APPLIED)) {
            throw impossibleResult();
        }
        if (isGuardFailure(failureReason.get())) {
            if (executedStepCount != 0 || failureStepIndex != 0) {
                throw impossibleResult();
            }
        } else if (failureStepIndex != 0 || executedStepCount != 1) {
            throw impossibleResult();
        }
    }

    private static void validateCompensationFailed(
            Optional<EffectRejectReason> rejectReason,
            Optional<EffectFailureReason> failureReason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount,
            EffectTrace trace) {
        if (rejectReason.isPresent()
                || !failureReason.equals(Optional.of(
                        EffectFailureReason.COMPENSATION_REFUND_FAILED))
                || failureStepIndex != NOT_APPLICABLE_STEP_INDEX
                || plannedStepCount == 0
                || executedStepCount > 1
                || primaryMutationCount != 0
                || !hasPenultimateStage(trace, EffectTraceStage.REFUND_FAILED)) {
            throw impossibleResult();
        }
    }

    private static boolean isGuardFailure(EffectFailureReason reason) {
        return reason == EffectFailureReason.EXECUTION_CANCELLED
                || reason == EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED;
    }

    private static void validateInvokedFailureIndex(
            int failureStepIndex, int plannedStepCount, int executedStepCount) {
        if (failureStepIndex < 0
                || failureStepIndex >= plannedStepCount
                || executedStepCount != failureStepIndex + 1) {
            throw impossibleResult();
        }
    }

    private static void validateGuardFailureIndex(
            int failureStepIndex, int plannedStepCount, int executedStepCount) {
        if (failureStepIndex < 0
                || failureStepIndex >= plannedStepCount
                || executedStepCount != failureStepIndex) {
            throw impossibleResult();
        }
    }

    private static void validateExecutedTrace(EffectTrace trace, int executedStepCount) {
        int observedStepCount = 0;
        int lastPhase = -1;
        int requestValidatedCount = 0;
        int targetResolvedCount = 0;
        int manaDebitedCount = 0;
        int refundCount = 0;
        for (int traceIndex = 0; traceIndex < trace.entries().size(); traceIndex++) {
            EffectTraceEntry entry = trace.entries().get(traceIndex);
            if (entry.stage().isTerminal()
                    && traceIndex != trace.entries().size() - 1) {
                throw impossibleResult();
            }

            int phase = tracePhase(entry.stage());
            if (phase < lastPhase) {
                throw impossibleResult();
            }
            lastPhase = phase;

            switch (entry.stage()) {
                case REQUEST_VALIDATED -> requestValidatedCount++;
                case TARGET_RESOLVED -> targetResolvedCount++;
                case MANA_DEBITED -> manaDebitedCount++;
                case REFUND_APPLIED, REFUND_FAILED -> refundCount++;
                default -> {
                    // Cardinality is validated only for unique state-machine stages.
                }
            }

            if (entry.stage().requiresStepIndex()) {
                if (entry.stepIndex() != observedStepCount) {
                    throw impossibleResult();
                }
                observedStepCount++;
            }
        }
        if (observedStepCount != executedStepCount
                || requestValidatedCount > 1
                || targetResolvedCount > 1
                || manaDebitedCount > 1
                || refundCount > 1) {
            throw impossibleResult();
        }
    }

    private static int tracePhase(EffectTraceStage stage) {
        return switch (stage) {
            case REQUEST_VALIDATED -> 0;
            case TARGET_RESOLVED -> 1;
            case MANA_DEBITED -> 2;
            case STEP_APPLIED, STEP_NOT_APPLIED, STEP_APPLIED_WITH_FAILURE -> 3;
            case REFUND_APPLIED, REFUND_FAILED -> 4;
            case TERMINAL_REJECTED,
                    TERMINAL_SUCCEEDED,
                    TERMINAL_FAILED,
                    TERMINAL_PARTIAL,
                    TERMINAL_COMPENSATED,
                    TERMINAL_COMPENSATION_FAILED -> 5;
        };
    }

    private static void validateTraceSemantics(
            EffectTerminalStatus status,
            Optional<EffectFailureReason> failureReason,
            int executedStepCount,
            EffectTrace trace) {
        int stepOrdinal = 0;
        int manaDebitedCount = 0;
        int refundAppliedCount = 0;
        int refundFailedCount = 0;
        int manaDebitedIndex = -1;
        int firstStepIndex = -1;
        int refundIndex = -1;
        for (int index = 0; index < trace.entries().size(); index++) {
            EffectTraceEntry entry = trace.entries().get(index);
            if (entry.stage() == EffectTraceStage.MANA_DEBITED) {
                manaDebitedCount++;
                manaDebitedIndex = index;
            }
            if (entry.stage() == EffectTraceStage.REFUND_APPLIED) {
                refundAppliedCount++;
                refundIndex = index;
            } else if (entry.stage() == EffectTraceStage.REFUND_FAILED) {
                refundFailedCount++;
                refundIndex = index;
            }
            if (!entry.stage().requiresStepIndex()) {
                continue;
            }
            if (firstStepIndex < 0) {
                firstStepIndex = index;
            }
            EffectTraceStage expected = expectedStepStage(
                    status, failureReason, stepOrdinal, executedStepCount);
            if (entry.stage() != expected) {
                throw impossibleResult();
            }
            stepOrdinal++;
        }

        if (manaDebitedCount == 1
                && firstStepIndex >= 0
                && manaDebitedIndex >= firstStepIndex) {
            throw impossibleResult();
        }
        if (status == EffectTerminalStatus.COMPENSATED) {
            if (manaDebitedCount != 1
                    || refundAppliedCount != 1
                    || refundFailedCount != 0
                    || manaDebitedIndex >= refundIndex) {
                throw impossibleResult();
            }
        } else if (status == EffectTerminalStatus.COMPENSATION_FAILED) {
            if (manaDebitedCount != 1
                    || refundAppliedCount != 0
                    || refundFailedCount != 1
                    || manaDebitedIndex >= refundIndex) {
                throw impossibleResult();
            }
        } else if (refundAppliedCount != 0 || refundFailedCount != 0) {
            throw impossibleResult();
        } else if (((status == EffectTerminalStatus.REJECTED
                                || status == EffectTerminalStatus.FAILED)
                        && manaDebitedCount != 0)
                || manaDebitedCount > 1) {
            throw impossibleResult();
        }
    }

    private static EffectTraceStage expectedStepStage(
            EffectTerminalStatus status,
            Optional<EffectFailureReason> failureReason,
            int stepOrdinal,
            int executedStepCount) {
        boolean lastExecuted = stepOrdinal == executedStepCount - 1;
        return switch (status) {
            case SUCCEEDED -> EffectTraceStage.STEP_APPLIED;
            case REJECTED -> throw impossibleResult();
            case FAILED -> EffectTraceStage.STEP_NOT_APPLIED;
            case PARTIALLY_SUCCEEDED -> {
                EffectFailureReason reason = failureReason.orElseThrow(
                        EffectExecutionResult::impossibleResult);
                if (!lastExecuted || isGuardFailure(reason)) {
                    yield EffectTraceStage.STEP_APPLIED;
                }
                yield reason == EffectFailureReason.PRIMARY_STEP_NOT_APPLIED
                        ? EffectTraceStage.STEP_NOT_APPLIED
                        : EffectTraceStage.STEP_APPLIED_WITH_FAILURE;
            }
            case COMPENSATED, COMPENSATION_FAILED -> EffectTraceStage.STEP_NOT_APPLIED;
        };
    }

    private static boolean hasPenultimateStage(EffectTrace trace, EffectTraceStage stage) {
        return trace.entries().size() >= 2
                && trace.entries().get(trace.entries().size() - 2).stage() == stage;
    }

    private static EffectTraceStage terminalStage(EffectTerminalStatus status) {
        return switch (status) {
            case SUCCEEDED -> EffectTraceStage.TERMINAL_SUCCEEDED;
            case REJECTED -> EffectTraceStage.TERMINAL_REJECTED;
            case FAILED -> EffectTraceStage.TERMINAL_FAILED;
            case PARTIALLY_SUCCEEDED -> EffectTraceStage.TERMINAL_PARTIAL;
            case COMPENSATED -> EffectTraceStage.TERMINAL_COMPENSATED;
            case COMPENSATION_FAILED -> EffectTraceStage.TERMINAL_COMPENSATION_FAILED;
        };
    }

    private static P6ExecutionInvariantException impossibleResult() {
        return new P6ExecutionInvariantException(P6ExecutionInvariantCode.IMPOSSIBLE_RESULT);
    }
}
