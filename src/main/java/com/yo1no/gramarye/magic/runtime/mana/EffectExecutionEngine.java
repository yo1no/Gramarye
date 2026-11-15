package com.yo1no.gramarye.magic.runtime.mana;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

sealed interface EffectExecutionPreparation
        permits PreparedEffectExecution, TerminalEffectExecution {}

record PreparedEffectExecution(
        EffectRequest request,
        EffectCommitPlan plan,
        int suppliedChildIntentCapacity,
        EffectExecutionGuard guard,
        EffectCommitPort commitPort,
        List<EffectTraceEntry> tracePrefix) implements EffectExecutionPreparation {
    PreparedEffectExecution {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(commitPort, "commitPort");
        Objects.requireNonNull(tracePrefix, "tracePrefix");
        tracePrefix = List.copyOf(tracePrefix);
        if (suppliedChildIntentCapacity < 0
                || suppliedChildIntentCapacity
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION
                || plan.declaredChildIntentCount() > suppliedChildIntentCapacity
                || tracePrefix.size() != 2
                || tracePrefix.get(0).stage() != EffectTraceStage.REQUEST_VALIDATED
                || tracePrefix.get(1).stage() != EffectTraceStage.TARGET_RESOLVED
                || tracePrefix.stream().anyMatch(entry -> entry.stage().isTerminal())) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_PREPARATION);
        }
    }
}

record TerminalEffectExecution(EffectExecutionResult result)
        implements EffectExecutionPreparation {
    TerminalEffectExecution {
        Objects.requireNonNull(result, "result");
    }
}

enum EffectAttemptStatus {
    SUCCEEDED,
    ZERO_MUTATION_FAILURE,
    PARTIALLY_SUCCEEDED
}

enum ManaTraceState {
    NOT_DEBITED,
    DEBITED
}

record EffectExecutionAttempt(
        EffectAttemptStatus status,
        Optional<EffectFailureReason> failureReason,
        int failureStepIndex,
        int plannedStepCount,
        int executedStepCount,
        int primaryMutationCount,
        List<EffectTraceEntry> tracePrefix) {
    EffectExecutionAttempt {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(failureReason, "failureReason");
        Objects.requireNonNull(tracePrefix, "tracePrefix");
        tracePrefix = List.copyOf(tracePrefix);
        if (plannedStepCount <= 0
                || plannedStepCount > P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN
                || executedStepCount < 0
                || executedStepCount > plannedStepCount
                || primaryMutationCount < 0
                || primaryMutationCount
                        > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION
                || tracePrefix.isEmpty()
                || tracePrefix.stream().anyMatch(entry -> entry.stage().isTerminal()
                        || entry.stage() == EffectTraceStage.REFUND_APPLIED
                        || entry.stage() == EffectTraceStage.REFUND_FAILED)) {
            throw invalidAttempt();
        }
        for (int index = 0; index < tracePrefix.size(); index++) {
            if (tracePrefix.get(index).sequence() != index) {
                throw invalidAttempt();
            }
        }
        validateTracePrefix(tracePrefix, executedStepCount);
        switch (status) {
            case SUCCEEDED -> {
                if (failureReason.isPresent()
                        || failureStepIndex
                                != EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX
                        || executedStepCount != plannedStepCount
                        || primaryMutationCount < executedStepCount) {
                    throw invalidAttempt();
                }
            }
            case ZERO_MUTATION_FAILURE -> {
                if (failureReason.isEmpty()
                        || primaryMutationCount != 0
                        || failureStepIndex != 0
                        || !validZeroMutationCoordinate(
                                failureReason.get(), executedStepCount)) {
                    throw invalidAttempt();
                }
            }
            case PARTIALLY_SUCCEEDED -> {
                if (failureReason.isEmpty()
                        || failureReason.get()
                                == EffectFailureReason.COMPENSATION_REFUND_FAILED
                        || primaryMutationCount == 0
                        || failureStepIndex < 0
                        || failureStepIndex >= plannedStepCount) {
                    throw invalidAttempt();
                }
                validatePartialCoordinate(
                        failureReason.get(),
                        failureStepIndex,
                        executedStepCount,
                        primaryMutationCount);
            }
        }
    }

    boolean hasManaDebitTrace() {
        return tracePrefix.stream()
                .anyMatch(entry -> entry.stage() == EffectTraceStage.MANA_DEBITED);
    }

    private static boolean validZeroMutationCoordinate(
            EffectFailureReason reason, int executedStepCount) {
        return switch (reason) {
            case PRIMARY_STEP_NOT_APPLIED -> executedStepCount == 1;
            case EXECUTION_CANCELLED, EXECUTION_DEADLINE_EXCEEDED ->
                    executedStepCount == 0;
            case PRIMARY_STEP_APPLIED_WITH_FAILURE, COMPENSATION_REFUND_FAILED -> false;
        };
    }

    private static void validateTracePrefix(
            List<EffectTraceEntry> trace, int executedStepCount) {
        if (trace.size() < 2
                || trace.get(0).stage() != EffectTraceStage.REQUEST_VALIDATED
                || trace.get(1).stage() != EffectTraceStage.TARGET_RESOLVED) {
            throw invalidAttempt();
        }
        int observedSteps = 0;
        int debitStages = 0;
        for (int index = 0; index < trace.size(); index++) {
            EffectTraceEntry entry = trace.get(index);
            if (entry.stage() == EffectTraceStage.MANA_DEBITED) {
                debitStages++;
                if (index != 2) {
                    throw invalidAttempt();
                }
            }
            if (entry.stage().requiresStepIndex()) {
                if (entry.stepIndex() != observedSteps) {
                    throw invalidAttempt();
                }
                observedSteps++;
            }
        }
        if (debitStages > 1 || observedSteps != executedStepCount) {
            throw invalidAttempt();
        }
    }

    private static void validatePartialCoordinate(
            EffectFailureReason reason,
            int failureStepIndex,
            int executedStepCount,
            int primaryMutationCount) {
        switch (reason) {
            case EXECUTION_CANCELLED, EXECUTION_DEADLINE_EXCEEDED -> {
                if (executedStepCount != failureStepIndex
                        || primaryMutationCount < executedStepCount) {
                    throw invalidAttempt();
                }
            }
            case PRIMARY_STEP_NOT_APPLIED -> {
                if (failureStepIndex == 0
                        || executedStepCount != failureStepIndex + 1
                        || primaryMutationCount < failureStepIndex) {
                    throw invalidAttempt();
                }
            }
            case PRIMARY_STEP_APPLIED_WITH_FAILURE -> {
                if (executedStepCount != failureStepIndex + 1
                        || primaryMutationCount < executedStepCount) {
                    throw invalidAttempt();
                }
            }
            case COMPENSATION_REFUND_FAILED -> throw invalidAttempt();
        }
    }

    private static P6ExecutionInvariantException invalidAttempt() {
        return new P6ExecutionInvariantException(P6ExecutionInvariantCode.INVALID_ATTEMPT);
    }
}

/** Stateless owner of the sole primary-effect step loop. */
final class EffectExecutionEngine {
    EffectExecutionResult execute(
            EffectRequest request,
            EffectResolver resolver,
            EffectExecutionGuard guard,
            EffectCommitPort commitPort) {
        return execute(request, 0, resolver, guard, commitPort);
    }

    EffectExecutionResult execute(
            EffectRequest request,
            int suppliedChildIntentCapacity,
            EffectResolver resolver,
            EffectExecutionGuard guard,
            EffectCommitPort commitPort) {
        EffectExecutionPreparation preparation = prepare(
                request,
                suppliedChildIntentCapacity,
                resolver,
                guard,
                commitPort);
        if (preparation instanceof TerminalEffectExecution terminal) {
            return terminal.result();
        }
        EffectExecutionAttempt attempt = executePrepared(
                (PreparedEffectExecution) preparation,
                ManaTraceState.NOT_DEBITED);
        return finalizeWithoutMana(attempt);
    }

    EffectExecutionPreparation prepare(
            EffectRequest request,
            int suppliedChildIntentCapacity,
            EffectResolver resolver,
            EffectExecutionGuard guard,
            EffectCommitPort commitPort) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(commitPort, "commitPort");
        PreparationResult prepared = preparePlan(request, suppliedChildIntentCapacity,
                resolver, guard, commitPort, null);
        if (prepared.rejection != null) {
            return terminal(prepared.rejection);
        }
        return new PreparedEffectExecution(request, prepared.plan, suppliedChildIntentCapacity,
                guard, commitPort, prepared.trace);
    }

    V0EffectPreparation prepareV0(
            EffectRequest request,
            V0EffectResolver resolver,
            EffectExecutionGuard guard,
            V0EffectCommitPort commitPort) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(commitPort, "commitPort");
        PreparationResult prepared = preparePlan(request, 0, resolver, guard, null, commitPort);
        if (prepared.rejection != null) {
            return new V0EffectPreparationRejected(prepared.rejection);
        }
        return new PreparedV0EffectExecution(request, prepared.plan, guard, commitPort,
                prepared.trace);
    }

    private static final class PreparationResult {
        private final EffectCommitPlan plan;
        private final List<EffectTraceEntry> trace;
        private final EffectExecutionResult rejection;

        private PreparationResult(EffectCommitPlan plan, List<EffectTraceEntry> trace,
                EffectExecutionResult rejection) {
            this.plan = plan;
            this.trace = trace;
            this.rejection = rejection;
        }

        private static PreparationResult rejected(EffectExecutionResult result) {
            return new PreparationResult(null, List.of(), result);
        }
    }

    private PreparationResult preparePlan(
            EffectRequest request,
            int suppliedChildIntentCapacity,
            EffectResolver resolver,
            EffectExecutionGuard guard,
            EffectCommitPort legacyPort,
            V0EffectCommitPort v0Port) {
        List<EffectTraceEntry> trace = new ArrayList<>();

        EffectGuardDecision entryDecision = checkGuard(guard, EffectGuardPoint.entry());
        if (entryDecision != EffectGuardDecision.ALLOWED) {
            return PreparationResult.rejected(rejected(guardRejectReason(entryDecision), 0, 0, trace));
        }

        if (!validRequestShape(request)) {
            return PreparationResult.rejected(rejected(EffectRejectReason.INVALID_REQUEST, 0, 0, trace));
        }
        if (suppliedChildIntentCapacity < 0
                || suppliedChildIntentCapacity
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION) {
            return PreparationResult.rejected(rejected(EffectRejectReason.BOUND_EXCEEDED, 0, 0, trace));
        }
        appendWithoutStep(trace, EffectTraceStage.REQUEST_VALIDATED);

        EffectResolution resolution = resolver.resolve(request, suppliedChildIntentCapacity);
        if (resolution == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.RESOLVER_RETURNED_NULL);
        }
        if (resolution instanceof RejectedEffectResolution rejected) {
            return PreparationResult.rejected(rejected(rejected.reason(), 0, 0, trace));
        }

        EffectCommitPlan plan = ((AcceptedEffectResolution) resolution).plan();
        if (plan == null || plan.declaredChildIntentCount() > suppliedChildIntentCapacity) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ACCEPTED_PLAN);
        }
        appendWithoutStep(trace, EffectTraceStage.TARGET_RESOLVED);
        int plannedStepCount = plan.steps().size();

        if (!(legacyPort != null ? legacyPort.isAvailable() : v0Port.isAvailable())) {
            return PreparationResult.rejected(rejected(
                    EffectRejectReason.COMMIT_PORT_UNAVAILABLE,
                    plannedStepCount,
                    0,
                    trace));
        }

        EffectGuardDecision preCommitDecision = checkGuard(
                guard, EffectGuardPoint.preCommit());
        if (preCommitDecision != EffectGuardDecision.ALLOWED) {
            return PreparationResult.rejected(rejected(
                    guardRejectReason(preCommitDecision),
                    plannedStepCount,
                    0,
                    trace));
        }

        return new PreparationResult(plan, trace, null);
    }

    EffectExecutionAttempt executePrepared(
            PreparedEffectExecution prepared, ManaTraceState manaTraceState) {
        Objects.requireNonNull(prepared, "prepared");
        return ((V0KnownAttempt) executeSteps(prepared.request(), prepared.plan(),
                prepared.guard(), prepared.commitPort(), null, prepared.tracePrefix(),
                manaTraceState)).attempt();
    }

    V0EffectAttempt executePreparedV0(
            PreparedV0EffectExecution prepared, ManaTraceState manaTraceState) {
        Objects.requireNonNull(prepared, "prepared");
        return executeSteps(prepared.request(), prepared.plan(), prepared.guard(), null,
                prepared.commitPort(), prepared.tracePrefix(), manaTraceState);
    }

    private V0EffectAttempt executeSteps(
            EffectRequest request,
            EffectCommitPlan plan,
            EffectExecutionGuard guard,
            EffectCommitPort legacyPort,
            V0EffectCommitPort v0Port,
            List<EffectTraceEntry> tracePrefix,
            ManaTraceState manaTraceState) {
        Objects.requireNonNull(manaTraceState, "manaTraceState");
        List<EffectTraceEntry> trace = new ArrayList<>(tracePrefix);
        if (manaTraceState == ManaTraceState.DEBITED) {
            appendWithoutStep(trace, EffectTraceStage.MANA_DEBITED);
        }

        int plannedStepCount = plan.steps().size();
        int executedStepCount = 0;
        int primaryMutationCount = 0;
        for (EffectStep step : plan.steps()) {
            EffectGuardDecision stepDecision = checkGuard(
                    guard, EffectGuardPoint.beforeStep(step.index()));
            if (stepDecision != EffectGuardDecision.ALLOWED) {
                EffectFailureReason reason = guardFailureReason(stepDecision);
                if (primaryMutationCount == 0) {
                    return new V0KnownAttempt(zeroMutationFailure(
                            reason,
                            step.index(),
                            plannedStepCount,
                            executedStepCount,
                            trace));
                }
                return new V0KnownAttempt(partial(
                        reason,
                        step.index(),
                        plannedStepCount,
                        executedStepCount,
                        primaryMutationCount,
                        trace));
            }

            V0PrimaryOutcome primary = commit(request, step, legacyPort, v0Port);
            if (primary == V0NativeUnknown.INSTANCE) {
                return new V0UnresolvedAttempt(step.index(), primaryMutationCount);
            }
            if (!(primary instanceof V0Confirmed confirmed) || confirmed.outcome() == null) {
                throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.PORT_RETURNED_NULL);
            }
            EffectStepOutcome outcome = confirmed.outcome();
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
                return new V0KnownAttempt(zeroMutationFailure(
                        failureReason,
                        step.index(),
                        plannedStepCount,
                        executedStepCount,
                        trace));
            }
            return new V0KnownAttempt(partial(
                    failureReason,
                    step.index(),
                    plannedStepCount,
                    executedStepCount,
                    primaryMutationCount,
                    trace));
        }

        return new V0KnownAttempt(new EffectExecutionAttempt(
                EffectAttemptStatus.SUCCEEDED,
                Optional.empty(),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                plannedStepCount,
                executedStepCount,
                primaryMutationCount,
                trace));
    }

    private static V0PrimaryOutcome commit(
            EffectRequest request, EffectStep step,
            EffectCommitPort legacyPort, V0EffectCommitPort v0Port) {
        if (request instanceof SpawnProjectileRequest spawn
                && step instanceof SpawnProjectileStep spawnStep) {
            return legacyPort != null
                    ? new V0Confirmed(legacyPort.commitSpawn(spawn, spawnStep))
                    : v0Port.commitSpawn(spawn, spawnStep);
        }
        if (request instanceof DamageEffectRequest damage
                && step instanceof DamageEffectStep damageStep) {
            return legacyPort != null
                    ? new V0Confirmed(legacyPort.commitDamage(damage, damageStep))
                    : v0Port.commitDamage(damage, damageStep);
        }
        throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.UNSUPPORTED_COMMIT_STEP);
    }

    EffectExecutionResult finalizeWithoutMana(EffectExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (attempt.hasManaDebitTrace()) {
            throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.INVALID_ATTEMPT);
        }
        return switch (attempt.status()) {
            case SUCCEEDED -> succeeded(attempt);
            case PARTIALLY_SUCCEEDED -> partiallySucceeded(attempt);
            case ZERO_MUTATION_FAILURE -> {
                EffectFailureReason reason = attempt.failureReason().orElseThrow();
                if (reason == EffectFailureReason.PRIMARY_STEP_NOT_APPLIED) {
                    yield failed(attempt);
                }
                yield rejected(
                        guardRejectReason(reason),
                        attempt.plannedStepCount(),
                        attempt.executedStepCount(),
                        attempt.tracePrefix());
            }
        };
    }

    EffectExecutionResult finalizeDebited(EffectExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (!attempt.hasManaDebitTrace()
                || attempt.status() == EffectAttemptStatus.ZERO_MUTATION_FAILURE) {
            throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.INVALID_ATTEMPT);
        }
        return attempt.status() == EffectAttemptStatus.SUCCEEDED
                ? succeeded(attempt)
                : partiallySucceeded(attempt);
    }

    EffectExecutionResult finalizeCompensated(EffectExecutionAttempt attempt) {
        requireCompensableAttempt(attempt);
        List<EffectTraceEntry> trace = new ArrayList<>(attempt.tracePrefix());
        appendWithoutStep(trace, EffectTraceStage.REFUND_APPLIED);
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_COMPENSATED);
        return new EffectExecutionResult(
                EffectTerminalStatus.COMPENSATED,
                Optional.empty(),
                attempt.failureReason(),
                attempt.failureStepIndex(),
                attempt.plannedStepCount(),
                attempt.executedStepCount(),
                0,
                new EffectTrace(trace));
    }

    EffectExecutionResult finalizeCompensationFailed(EffectExecutionAttempt attempt) {
        requireCompensableAttempt(attempt);
        List<EffectTraceEntry> trace = new ArrayList<>(attempt.tracePrefix());
        appendWithoutStep(trace, EffectTraceStage.REFUND_FAILED);
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_COMPENSATION_FAILED);
        return new EffectExecutionResult(
                EffectTerminalStatus.COMPENSATION_FAILED,
                Optional.empty(),
                Optional.of(EffectFailureReason.COMPENSATION_REFUND_FAILED),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                attempt.plannedStepCount(),
                attempt.executedStepCount(),
                0,
                new EffectTrace(trace));
    }

    EffectExecutionResult rejectBeforePreparation(EffectRejectReason reason) {
        return rejected(Objects.requireNonNull(reason, "reason"), 0, 0, List.of());
    }

    EffectExecutionResult rejectPrepared(
            PreparedEffectExecution prepared, EffectRejectReason reason) {
        Objects.requireNonNull(prepared, "prepared");
        return rejected(
                Objects.requireNonNull(reason, "reason"),
                prepared.plan().steps().size(),
                0,
                prepared.tracePrefix());
    }

    private static TerminalEffectExecution terminal(EffectExecutionResult result) {
        return new TerminalEffectExecution(result);
    }

    private static EffectExecutionAttempt zeroMutationFailure(
            EffectFailureReason reason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            List<EffectTraceEntry> trace) {
        return new EffectExecutionAttempt(
                EffectAttemptStatus.ZERO_MUTATION_FAILURE,
                Optional.of(reason),
                failureStepIndex,
                plannedStepCount,
                executedStepCount,
                0,
                trace);
    }

    private static EffectExecutionAttempt partial(
            EffectFailureReason reason,
            int failureStepIndex,
            int plannedStepCount,
            int executedStepCount,
            int primaryMutationCount,
            List<EffectTraceEntry> trace) {
        return new EffectExecutionAttempt(
                EffectAttemptStatus.PARTIALLY_SUCCEEDED,
                Optional.of(reason),
                failureStepIndex,
                plannedStepCount,
                executedStepCount,
                primaryMutationCount,
                trace);
    }

    private static EffectExecutionResult succeeded(EffectExecutionAttempt attempt) {
        List<EffectTraceEntry> trace = new ArrayList<>(attempt.tracePrefix());
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_SUCCEEDED);
        return new EffectExecutionResult(
                EffectTerminalStatus.SUCCEEDED,
                Optional.empty(),
                Optional.empty(),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                attempt.plannedStepCount(),
                attempt.executedStepCount(),
                attempt.primaryMutationCount(),
                new EffectTrace(trace));
    }

    private static EffectExecutionResult failed(EffectExecutionAttempt attempt) {
        List<EffectTraceEntry> trace = new ArrayList<>(attempt.tracePrefix());
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_FAILED);
        return new EffectExecutionResult(
                EffectTerminalStatus.FAILED,
                Optional.empty(),
                attempt.failureReason(),
                attempt.failureStepIndex(),
                attempt.plannedStepCount(),
                attempt.executedStepCount(),
                0,
                new EffectTrace(trace));
    }

    private static EffectExecutionResult partiallySucceeded(EffectExecutionAttempt attempt) {
        List<EffectTraceEntry> trace = new ArrayList<>(attempt.tracePrefix());
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_PARTIAL);
        return new EffectExecutionResult(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                Optional.empty(),
                attempt.failureReason(),
                attempt.failureStepIndex(),
                attempt.plannedStepCount(),
                attempt.executedStepCount(),
                attempt.primaryMutationCount(),
                new EffectTrace(trace));
    }

    private static EffectExecutionResult rejected(
            EffectRejectReason reason,
            int plannedStepCount,
            int executedStepCount,
            List<EffectTraceEntry> tracePrefix) {
        List<EffectTraceEntry> trace = new ArrayList<>(tracePrefix);
        appendWithoutStep(trace, EffectTraceStage.TERMINAL_REJECTED);
        return new EffectExecutionResult(
                EffectTerminalStatus.REJECTED,
                Optional.of(reason),
                Optional.empty(),
                EffectExecutionResult.NOT_APPLICABLE_STEP_INDEX,
                plannedStepCount,
                executedStepCount,
                0,
                new EffectTrace(trace));
    }

    private static void requireCompensableAttempt(EffectExecutionAttempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        if (!attempt.hasManaDebitTrace()
                || attempt.status() != EffectAttemptStatus.ZERO_MUTATION_FAILURE
                || attempt.primaryMutationCount() != 0) {
            throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.INVALID_ATTEMPT);
        }
    }

    private static boolean validRequestShape(EffectRequest request) {
        if (request == null
                || request.requestId() == null
                || request.sourceEventId() == null
                || request.compensationPolicy() == null
                || request.manaCost() < 0
                || request.manaCost() > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            return false;
        }
        if (request instanceof SpawnProjectileRequest spawn) {
            return spawn.dimension() != null
                    && Double.isFinite(spawn.originX())
                    && Double.isFinite(spawn.originY())
                    && Double.isFinite(spawn.originZ())
                    && spawn.originX() >= -30_000_000.0
                    && spawn.originX() < 30_000_000.0
                    && spawn.originY() >= -20_000_000.0
                    && spawn.originY() < 20_000_000.0
                    && spawn.originZ() >= -30_000_000.0
                    && spawn.originZ() < 30_000_000.0
                    && spawn.directionXQ15() >= -32_767
                    && spawn.directionXQ15() <= 32_767
                    && spawn.directionYQ15() >= -32_767
                    && spawn.directionYQ15() <= 32_767
                    && spawn.directionZQ15() >= -32_767
                    && spawn.directionZQ15() <= 32_767
                    && (spawn.directionXQ15() != 0
                            || spawn.directionYQ15() != 0
                            || spawn.directionZQ15() != 0)
                    && spawn.profileCode() == 0;
        }
        if (request instanceof DamageEffectRequest damage) {
            return damage.target() != null
                    && damage.magnitude() > 0
                    && damage.magnitude() <= P6EffectBounds.MAX_EFFECT_MAGNITUDE;
        }
        throw new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }

    private static EffectGuardDecision checkGuard(
            EffectExecutionGuard guard, EffectGuardPoint point) {
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

    private static EffectRejectReason guardRejectReason(EffectFailureReason reason) {
        return switch (reason) {
            case EXECUTION_CANCELLED -> EffectRejectReason.CANCELLED;
            case EXECUTION_DEADLINE_EXCEEDED -> EffectRejectReason.DEADLINE_EXCEEDED;
            case PRIMARY_STEP_NOT_APPLIED,
                    PRIMARY_STEP_APPLIED_WITH_FAILURE,
                    COMPENSATION_REFUND_FAILED -> throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.INVALID_ATTEMPT);
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
