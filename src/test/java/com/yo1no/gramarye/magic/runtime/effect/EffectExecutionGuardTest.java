package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EffectExecutionGuardTest {
    @Test
    void entryCancellationAndDeadlineRejectBeforeResolutionOrCommit() {
        for (EffectGuardDecision decision : List.of(
                EffectGuardDecision.CANCELLED,
                EffectGuardDecision.DEADLINE_EXCEEDED)) {
            AtomicInteger resolutions = new AtomicInteger();
            EffectResolver resolver = (request, capacity) -> {
                resolutions.incrementAndGet();
                return new AcceptedEffectResolution(EffectTestFixtures.plan(1));
            };
            RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
            EffectExecutionResult result = new EffectExecutionEngine(
                    resolver, point -> decision, port).execute(EffectTestFixtures.request());

            assertEquals(EffectTerminalStatus.REJECTED, result.status());
            assertEquals(rejectReason(decision), result.rejectReason().orElseThrow());
            assertEquals(0, resolutions.get());
            assertEquals(0, port.availabilityChecks());
            assertEquals(List.of(), port.committedIndexes());
            assertEquals(
                    List.of(EffectTraceStage.TERMINAL_REJECTED),
                    stages(result));
        }
    }

    @Test
    void preCommitCancellationAndDeadlineRejectBeforeStepInvocation() {
        for (EffectGuardDecision decision : List.of(
                EffectGuardDecision.CANCELLED,
                EffectGuardDecision.DEADLINE_EXCEEDED)) {
            RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
            RecordingEffectGuard guard = new RecordingEffectGuard(point ->
                    point.kind() == EffectGuardPointKind.PRE_COMMIT
                            ? decision
                            : EffectGuardDecision.ALLOWED);
            EffectExecutionResult result = engine(EffectTestFixtures.plan(2), guard, port)
                    .execute(EffectTestFixtures.request());

            assertEquals(EffectTerminalStatus.REJECTED, result.status());
            assertEquals(rejectReason(decision), result.rejectReason().orElseThrow());
            assertEquals(List.of(), port.committedIndexes());
            assertEquals(
                    List.of(EffectGuardPoint.entry(), EffectGuardPoint.preCommit()),
                    guard.checks());
        }
    }

    @Test
    void beforeFirstStepCancellationAndDeadlineRejectWithoutCommit() {
        for (EffectGuardDecision decision : List.of(
                EffectGuardDecision.CANCELLED,
                EffectGuardDecision.DEADLINE_EXCEEDED)) {
            RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
            RecordingEffectGuard guard = new RecordingEffectGuard(point ->
                    point.equals(EffectGuardPoint.beforeStep(0))
                            ? decision
                            : EffectGuardDecision.ALLOWED);
            EffectExecutionResult result = engine(EffectTestFixtures.plan(2), guard, port)
                    .execute(EffectTestFixtures.request());

            assertEquals(EffectTerminalStatus.REJECTED, result.status());
            assertEquals(rejectReason(decision), result.rejectReason().orElseThrow());
            assertEquals(0, result.primaryMutationCount());
            assertEquals(List.of(), port.committedIndexes());
        }
    }

    @Test
    void cancellationAfterAppliedStepIsPartialAndStops() {
        assertGuardAfterMutation(
                EffectGuardDecision.CANCELLED,
                EffectFailureReason.EXECUTION_CANCELLED);
    }

    @Test
    void deadlineAfterAppliedStepIsPartialAndStops() {
        assertGuardAfterMutation(
                EffectGuardDecision.DEADLINE_EXCEEDED,
                EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED);
    }

    @Test
    void eightStepSuccessUsesAllTenAndNeverMoreGuardChecks() {
        RecordingEffectGuard guard = RecordingEffectGuard.allowing();
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        EffectExecutionResult result = engine(EffectTestFixtures.plan(8), guard, port)
                .execute(EffectTestFixtures.request());
        assertEquals(EffectTerminalStatus.SUCCEEDED, result.status());
        assertEquals(P6EffectBounds.MAX_DEADLINE_CHECKS_PER_EXECUTION, guard.checks().size());
    }

    @Test
    void everyLaterBeforeStepSafePointStopsForCancellationAndDeadline() {
        for (EffectGuardDecision decision : List.of(
                EffectGuardDecision.CANCELLED,
                EffectGuardDecision.DEADLINE_EXCEEDED)) {
            for (int stopIndex = 1; stopIndex < 8; stopIndex++) {
                int exactStopIndex = stopIndex;
                RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
                RecordingEffectGuard guard = new RecordingEffectGuard(point ->
                        point.equals(EffectGuardPoint.beforeStep(exactStopIndex))
                                ? decision
                                : EffectGuardDecision.ALLOWED);
                EffectExecutionResult result = engine(EffectTestFixtures.plan(8), guard, port)
                        .execute(EffectTestFixtures.request());
                assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.status());
                assertEquals(exactStopIndex, result.executedStepCount());
                assertEquals(exactStopIndex, result.failureStepIndex());
                assertEquals(exactStopIndex, port.committedIndexes().size());
                assertEquals(exactStopIndex + 3, guard.checks().size());
            }
        }
    }

    @Test
    void nullGuardDecisionIsInvariantFailure() {
        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> engine(
                        EffectTestFixtures.plan(1),
                        point -> null,
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        assertEquals(P6ExecutionInvariantCode.GUARD_RETURNED_NULL, failure.code());
    }

    @Test
    void guardRuntimeExceptionAndErrorPropagateAsSameObject() {
        RuntimeException runtimeFailure = new RuntimeException("guard-test-runtime");
        Error errorFailure = new AssertionError("guard-test-error");
        RuntimeException observedRuntime = assertThrows(
                RuntimeException.class,
                () -> engine(
                        EffectTestFixtures.plan(1),
                        point -> { throw runtimeFailure; },
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        Error observedError = assertThrows(
                Error.class,
                () -> engine(
                        EffectTestFixtures.plan(1),
                        point -> { throw errorFailure; },
                        RecordingDamageCommitPort.applyingAll())
                        .execute(EffectTestFixtures.request()));
        assertSame(runtimeFailure, observedRuntime);
        assertSame(errorFailure, observedError);
    }

    private static void assertGuardAfterMutation(
            EffectGuardDecision decision, EffectFailureReason expectedReason) {
        RecordingDamageCommitPort port = RecordingDamageCommitPort.applyingAll();
        RecordingEffectGuard guard = new RecordingEffectGuard(point ->
                point.equals(EffectGuardPoint.beforeStep(1))
                        ? decision
                        : EffectGuardDecision.ALLOWED);
        EffectExecutionResult result = engine(EffectTestFixtures.plan(3), guard, port)
                .execute(EffectTestFixtures.request());

        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.status());
        assertEquals(expectedReason, result.failureReason().orElseThrow());
        assertEquals(1, result.failureStepIndex());
        assertEquals(1, result.executedStepCount());
        assertEquals(1, result.primaryMutationCount());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(4, guard.checks().size());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL),
                stages(result));
    }

    private static EffectExecutionEngine engine(
            EffectCommitPlan plan,
            EffectExecutionGuard guard,
            DamageEffectCommitPort port) {
        return new EffectExecutionEngine(EffectTestFixtures.resolverFor(plan), guard, port);
    }

    private static EffectRejectReason rejectReason(EffectGuardDecision decision) {
        return decision == EffectGuardDecision.CANCELLED
                ? EffectRejectReason.CANCELLED
                : EffectRejectReason.DEADLINE_EXCEEDED;
    }

    private static List<EffectTraceStage> stages(EffectExecutionResult result) {
        return result.trace().entries().stream().map(EffectTraceEntry::stage).toList();
    }
}
