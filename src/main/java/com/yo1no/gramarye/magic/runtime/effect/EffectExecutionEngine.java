package com.yo1no.gramarye.magic.runtime.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class EffectExecutionEngine {
    private final EffectResolver resolver;
    private final EffectExecutionGuard guard;
    private final DamageEffectCommitPort commitPort;

    EffectExecutionEngine(
            EffectResolver resolver,
            EffectExecutionGuard guard,
            DamageEffectCommitPort commitPort) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.commitPort = Objects.requireNonNull(commitPort, "commitPort");
    }

    EffectExecutionResult execute(EffectRequest request) {
        return execute(request, 0);
    }

    EffectExecutionResult execute(EffectRequest request, int suppliedChildIntentCapacity) {
        List<EffectTraceEntry> trace = new ArrayList<>();

        EffectGuardDecision entryDecision = checkGuard(EffectGuardPoint.entry());
        if (entryDecision != EffectGuardDecision.ALLOWED) {
            return rejected(guardRejectReason(entryDecision), 0, trace);
        }

        if (!validRequestShape(request)) {
            return rejected(EffectRejectReason.INVALID_REQUEST, 0, trace);
        }
        if (suppliedChildIntentCapacity < 0
                || suppliedChildIntentCapacity
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION) {
            return rejected(EffectRejectReason.BOUND_EXCEEDED, 0, trace);
        }
        appendWithoutStep(trace, EffectTraceStage.REQUEST_VALIDATED);

        EffectResolution resolution = resolver.resolve(request, suppliedChildIntentCapacity);
        if (resolution == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.RESOLVER_RETURNED_NULL);
        }
        if (resolution instanceof RejectedEffectResolution rejected) {
            return rejected(rejected.reason(), 0, trace);
        }

        EffectCommitPlan plan = ((AcceptedEffectResolution) resolution).plan();
        if (plan.declaredChildIntentCount() > suppliedChildIntentCapacity) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ACCEPTED_PLAN);
        }
        appendWithoutStep(trace, EffectTraceStage.TARGET_RESOLVED);
        int plannedStepCount = plan.steps().size();

        if (!commitPort.isAvailable()) {
            return rejected(
                    EffectRejectReason.COMMIT_PORT_UNAVAILABLE,
                    plannedStepCount,
                    trace);
        }

        EffectGuardDecision preCommitDecision = checkGuard(EffectGuardPoint.preCommit());
        if (preCommitDecision != EffectGuardDecision.ALLOWED) {
            return rejected(
                    guardRejectReason(preCommitDecision),
                    plannedStepCount,
                    trace);
        }

        int executedStepCount = 0;
        int primaryMutationCount = 0;
        for (EffectStep step : plan.steps()) {
            EffectGuardDecision stepDecision = checkGuard(
                    EffectGuardPoint.beforeStep(step.index()));
            if (stepDecision != EffectGuardDecision.ALLOWED) {
                if (primaryMutationCount == 0) {
                    return rejected(
                            guardRejectReason(stepDecision),
                            plannedStepCount,
                            trace);
                }
                return partiallySucceeded(
                        guardFailureReason(stepDecision),
                        step.index(),
                        plannedStepCount,
                        executedStepCount,
                        primaryMutationCount,
                        trace);
            }

            if (!(step instanceof DamageEffectStep damageStep)) {
                throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.UNSUPPORTED_COMMIT_STEP);
            }
            EffectStepOutcome outcome = commitPort.commitDamage(damageStep);
            if (outcome == null) {
                throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.PORT_RETURNED_NULL);
            }
            executedStepCount++;
            primaryMutationCount = EffectMutationAccumulator.add(
                    primaryMutationCount, step, outcome);
            appendStep(trace, step.index(), traceStage(outcome.kind()));

            if (outcome.kind() == EffectStepOutcomeKind.APPLIED) {
                continue;
            }
            EffectFailureReason failureReason = outcome.failureReason().orElseThrow();
            if (outcome.kind() == EffectStepOutcomeKind.NOT_APPLIED
                    && primaryMutationCount == 0) {
                return failed(
                        failureReason,
                        step.index(),
                        plannedStepCount,
                        executedStepCount,
                        trace);
            }
            return partiallySucceeded(
                    failureReason,
                    step.index(),
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount,
                    trace);
        }

        return succeeded(
                plannedStepCount,
                executedStepCount,
                primaryMutationCount,
                trace);
    }

    private static boolean validRequestShape(EffectRequest request) {
        return request != null
                && request.requestId() != null
                && request.sourceEventId() != null
                && request.target() != null
                && request.compensationPolicy() != null;
    }

    private EffectGuardDecision checkGuard(EffectGuardPoint point) {
        EffectGuardDecision decision = guard.check(point);
        if (decision == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.GUARD_RETURNED_NULL);
        }
        return decision;
    }

    private static EffectRejectReason guardRejectReason(EffectGuardDecision decision) {
        return switch (decision) {
            case CANCELLED -> EffectRejectReason.CANCELLED;
            case DEADLINE_EXCEEDED -> EffectRejectReason.DEADLINE_EXCEEDED;
            case ALLOWED -> throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_GUARD_DECISION_USAGE);
        };
    }

    private static EffectFailureReason guardFailureReason(EffectGuardDecision decision) {
        return switch (decision) {
            case CANCELLED -> EffectFailureReason.EXECUTION_CANCELLED;
            case DEADLINE_EXCEEDED -> EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED;
            case ALLOWED -> throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_GUARD_DECISION_USAGE);
        };
    }

    private static EffectTraceStage traceStage(EffectStepOutcomeKind kind) {
        return switch (kind) {
            case APPLIED -> EffectTraceStage.STEP_APPLIED;
            case NOT_APPLIED -> EffectTraceStage.STEP_NOT_APPLIED;
            case APPLIED_WITH_FAILURE -> EffectTraceStage.STEP_APPLIED_WITH_FAILURE;
        };
    }

    private static EffectExecutionResult succeeded(
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount,
            List<EffectTraceEntry> trace) {
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_SUCCEEDED);
        return new EffectExecutionResult(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                plannedStepCount,
                executedStepCount,
                primaryMutationCount,
                new EffectTrace(trace));
    }

    private static EffectExecutionResult rejected(
            EffectRejectReason reason,
            int plannedStepCount,
            List<EffectTraceEntry> trace) {
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_REJECTED);
        return new EffectExecutionResult(
                EffectTerminalStatus.REJECTED,
                Optional.of(reason),
                Optional.empty(),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                plannedStepCount,
                0,
                0,
                new EffectTrace(trace));
    }

    private static EffectExecutionResult failed(
            EffectFailureReason reason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            List<EffectTraceEntry> trace) {
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_FAILED);
        return new EffectExecutionResult(
                EffectTerminalStatus.FAILED,
                Optional.empty(),
                Optional.of(reason),
                failureStepIndex,
                plannedStepCount,
                executedStepCount,
                0,
                new EffectTrace(trace));
    }

    private static EffectExecutionResult partiallySucceeded(
            EffectFailureReason reason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount,
            List<EffectTraceEntry> trace) {
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_PARTIAL);
        return new EffectExecutionResult(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                Optional.empty(),
                Optional.of(reason),
                failureStepIndex,
                plannedStepCount,
                executedStepCount,
                primaryMutationCount,
                new EffectTrace(trace));
    }

    private static void appendWithoutStep(
            List<EffectTraceEntry> trace, EffectTraceStage stage) {
        requireTraceCapacity(trace);
        trace.add(EffectTraceEntry.withoutStep(trace.size(), stage));
    }

    private static void appendStep(
            List<EffectTraceEntry> trace, int stepIndex, EffectTraceStage stage) {
        requireTraceCapacity(trace);
        trace.add(EffectTraceEntry.forStep(trace.size(), stage, stepIndex));
    }

    private static void requireTraceCapacity(List<EffectTraceEntry> trace) {
        if (trace.size() >= P6EffectBounds.MAX_TRACE_ENTRIES) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.TRACE_CAPACITY_EXCEEDED);
        }
    }
}

final class EffectMutationAccumulator {
    private EffectMutationAccumulator() {}

    static int add(int current, EffectStep step, EffectStepOutcome outcome) {
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(outcome, "outcome");
        if (current < 0
                || outcome.actualPrimaryMutationCount()
                        > step.declaredPrimaryMutationUpperBound()) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.ACTUAL_MUTATION_EXCEEDS_DECLARED);
        }
        int updated = Math.addExact(current, outcome.actualPrimaryMutationCount());
        if (updated > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.TOTAL_MUTATION_EXCEEDS_BOUND);
        }
        return updated;
    }
}
