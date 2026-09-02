package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionOutcomeTest {
    @Test
    void oneStepDebitSuccessHasExactInvocationAndTraceOrder() {
        List<String> order = new ArrayList<>();
        var resolver = new ActionTransactionTestFixtures.TransactionRecordingResolver(
                ActionTransactionTestFixtures.resolverFor(
                        ActionTransactionTestFixtures.plan(1)),
                order);
        var guard = new ActionTransactionTestFixtures.TransactionRecordingGuard(
                point -> EffectGuardDecision.ALLOWED, order);
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                true, List.of(), order);
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(
                true,
                ActionTransactionTestFixtures.ACCOUNT_ID,
                ManaAvailability.AVAILABLE,
                100L,
                order);

        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(resolver)
                .execute(ActionTransactionTestFixtures.invocation(10L), account, 0, guard, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, result.effectResult().executedStepCount());
        assertEquals(1, result.effectResult().primaryMutationCount());
        assertEquals(
                List.of(
                        "guard-ENTRY--1",
                        "resolver",
                        "port-availability",
                        "guard-PRE_COMMIT--1",
                        "mana-thread",
                        "mana-account",
                        "mana-availability",
                        "mana-balance",
                        "mana-write",
                        "guard-BEFORE_STEP-0",
                        "port-step-0"),
                order);
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED),
                ActionTransactionTestFixtures.stages(result));
    }

    @Test
    void eightStepDebitSuccessExecutesEveryStepExactlyOnce() {
        var guard = ActionTransactionTestFixtures.TransactionRecordingGuard.allowing();
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);

        ActionDamageTransactionResult result = execute(
                ActionTransactionTestFixtures.plan(8), 8L, account, guard, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(1, result.manaMutationCount());
        assertEquals(8, result.effectResult().plannedStepCount());
        assertEquals(8, result.effectResult().executedStepCount());
        assertEquals(8, result.effectResult().primaryMutationCount());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7), port.committedIndexes());
        assertEquals(P6EffectBounds.MAX_DEADLINE_CHECKS_PER_EXECUTION, guard.checks().size());
        assertEquals(1, account.balanceWrites());
        assertEquals(92L, account.currentBalance());
    }

    @Test
    void laterNotAppliedIsPartialAndStopsWithoutRefund() {
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                true,
                List.of(
                        EffectStepOutcome.applied(1),
                        EffectStepOutcome.notApplied(),
                        EffectStepOutcome.applied(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);

        ActionDamageTransactionResult result = execute(
                ActionTransactionTestFixtures.plan(3),
                10L,
                account,
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                port);

        assertPartial(result, EffectFailureReason.PRIMARY_STEP_NOT_APPLIED, 1, 2, 1);
        assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, account.balanceWrites());
        assertEquals(90L, account.currentBalance());
        assertEquals(List.of(0, 1), port.committedIndexes());
        assertTrue(result.provisionalFailure().isEmpty());
    }

    @Test
    void firstAppliedWithFailureIsPartialWithoutRefund() {
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                true,
                List.of(
                        EffectStepOutcome.appliedWithFailure(1),
                        EffectStepOutcome.applied(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);

        ActionDamageTransactionResult result = execute(
                ActionTransactionTestFixtures.plan(3),
                10L,
                account,
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                port);

        assertPartial(
                result,
                EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                0,
                1,
                1);
        assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, account.balanceWrites());
        assertEquals(90L, account.currentBalance());
        assertEquals(List.of(0), port.committedIndexes());
        assertTrue(result.provisionalFailure().isEmpty());
    }

    @Test
    void laterAppliedWithFailureIsPartialAndStopsRemainingSteps() {
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                true,
                List.of(
                        EffectStepOutcome.applied(1),
                        EffectStepOutcome.appliedWithFailure(1),
                        EffectStepOutcome.applied(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);

        ActionDamageTransactionResult result = execute(
                ActionTransactionTestFixtures.plan(3),
                10L,
                account,
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                port);

        assertPartial(
                result,
                EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                1,
                2,
                2);
        assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, account.balanceWrites());
        assertEquals(90L, account.currentBalance());
        assertEquals(List.of(0, 1), port.committedIndexes());
        assertTrue(result.provisionalFailure().isEmpty());
    }

    @Test
    void firstNonAppliedStopsAllLaterCallsWithoutManaOrRefund() {
        var port = new ActionTransactionTestFixtures.TransactionRecordingPort(
                true,
                List.of(
                        EffectStepOutcome.notApplied(),
                        EffectStepOutcome.applied(1),
                        EffectStepOutcome.applied(1)));
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);

        ActionDamageTransactionResult result = execute(
                ActionTransactionTestFixtures.plan(3),
                0L,
                account,
                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                port);

        assertEquals(EffectTerminalStatus.FAILED, result.effectResult().status());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.effectResult().failureReason().orElseThrow());
        assertEquals(0, result.effectResult().failureStepIndex());
        assertEquals(1, result.effectResult().executedStepCount());
        assertEquals(0, result.effectResult().primaryMutationCount());
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
        assertEquals(0, account.totalAccesses());
        assertEquals(List.of(0), port.committedIndexes());
        assertEquals(
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_FAILED),
                ActionTransactionTestFixtures.stages(result));
    }

    private static ActionDamageTransactionResult execute(
            EffectCommitPlan plan,
            long manaCost,
            ActionTransactionTestFixtures.RecordingManaAccount account,
            ActionTransactionTestFixtures.TransactionRecordingGuard guard,
            ActionTransactionTestFixtures.TransactionRecordingPort port) {
        return ActionTransactionTestFixtures.engine(
                        ActionTransactionTestFixtures.resolverFor(plan))
                .execute(
                        ActionTransactionTestFixtures.invocation(manaCost),
                        account,
                        0,
                        guard,
                        port);
    }

    private static void assertPartial(
            ActionDamageTransactionResult result,
            EffectFailureReason reason,
            int failureStep,
            int executedSteps,
            int primaryMutations) {
        assertEquals(EffectTerminalStatus.PARTIALLY_SUCCEEDED, result.effectResult().status());
        assertEquals(reason, result.effectResult().failureReason().orElseThrow());
        assertEquals(failureStep, result.effectResult().failureStepIndex());
        assertEquals(executedSteps, result.effectResult().executedStepCount());
        assertEquals(primaryMutations, result.effectResult().primaryMutationCount());
    }
}
