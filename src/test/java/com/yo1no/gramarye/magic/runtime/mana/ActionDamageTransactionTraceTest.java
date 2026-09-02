package com.yo1no.gramarye.magic.runtime.mana;

import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.engine;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.invocation;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.plan;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.resolverFor;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.stages;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.RecordingManaAccount;
import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.TransactionRecordingGuard;
import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.TransactionRecordingPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionTraceTest {
    private static final long INITIAL_BALANCE = 100L;
    private static final long MANA_COST = 10L;

    @Test
    void noManaSuccessTraceIsExactContiguousAndSingleTerminal() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ActionDamageTransactionResult result = execute(
                0L, account, TransactionRecordingPort.applyingAll());

        assertExactTrace(
                result,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED));
        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        assertEquals(0, account.totalAccesses());
    }

    @Test
    void debitSuccessTracePlacesManaBeforeSteps() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        ActionDamageTransactionResult result = execute(
                MANA_COST, account, TransactionRecordingPort.applyingAll());

        assertExactTrace(
                result,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.TERMINAL_SUCCEEDED));
        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        assertEquals(1, account.balanceWrites());
    }

    @Test
    void compensatedTraceHasDebitRefundAndOnlyFinalCompensatedTerminal() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort port = new TransactionRecordingPort(
                true, List.of(EffectStepOutcome.notApplied()));
        ActionDamageTransactionResult result = execute(MANA_COST, account, port);

        assertExactTrace(
                result,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.REFUND_APPLIED,
                        EffectTraceStage.TERMINAL_COMPENSATED));
        assertEquals(EffectTerminalStatus.COMPENSATED, result.effectResult().status());
        assertEquals(2, account.balanceWrites());
    }

    @Test
    void compensationFailedTraceHasDebitRefundFailureAndOnlyFinalFailureTerminal() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        AvailabilityChangingPort port = new AvailabilityChangingPort(account);
        ActionDamageTransactionResult result = execute(MANA_COST, account, port);

        assertExactTrace(
                result,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.REFUND_FAILED,
                        EffectTraceStage.TERMINAL_COMPENSATION_FAILED));
        assertEquals(
                EffectTerminalStatus.COMPENSATION_FAILED,
                result.effectResult().status());
        assertEquals(1, account.balanceWrites());
    }

    @Test
    void partialTraceHasNoRefundAndOrdersAllStepStages() {
        RecordingManaAccount notAppliedAccount = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort notAppliedPort = new TransactionRecordingPort(
                true,
                List.of(EffectStepOutcome.applied(1), EffectStepOutcome.notApplied()));
        ActionDamageTransactionResult laterNotApplied = engine(resolverFor(plan(3)))
                .execute(
                        invocation(MANA_COST),
                        notAppliedAccount,
                        0,
                        TransactionRecordingGuard.allowing(),
                        notAppliedPort);

        assertExactTrace(
                laterNotApplied,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED,
                        EffectTraceStage.STEP_NOT_APPLIED,
                        EffectTraceStage.TERMINAL_PARTIAL));
        assertEquals(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                laterNotApplied.effectResult().status());
        assertInstanceOf(ManaDebited.class, laterNotApplied.manaSummary());
        assertEquals(1, laterNotApplied.manaMutationCount());
        assertEquals(1, notAppliedAccount.balanceWrites());
        assertEquals(INITIAL_BALANCE - MANA_COST, notAppliedAccount.currentBalance());

        RecordingManaAccount appliedWithFailureAccount =
                new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort appliedWithFailurePort = new TransactionRecordingPort(
                true, List.of(EffectStepOutcome.appliedWithFailure(1)));
        ActionDamageTransactionResult appliedWithFailure = execute(
                MANA_COST, appliedWithFailureAccount, appliedWithFailurePort);

        assertExactTrace(
                appliedWithFailure,
                List.of(
                        EffectTraceStage.REQUEST_VALIDATED,
                        EffectTraceStage.TARGET_RESOLVED,
                        EffectTraceStage.MANA_DEBITED,
                        EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                        EffectTraceStage.TERMINAL_PARTIAL));
        assertEquals(
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                appliedWithFailure.effectResult().status());
        assertInstanceOf(ManaDebited.class, appliedWithFailure.manaSummary());
        assertEquals(1, appliedWithFailure.manaMutationCount());
        assertEquals(1, appliedWithFailureAccount.balanceWrites());
        assertEquals(
                INITIAL_BALANCE - MANA_COST,
                appliedWithFailureAccount.currentBalance());
    }

    @Test
    void allSixTerminalsMapOneToOneWithinThirtyTwoEntries() {
        ActionDamageTransactionResult succeeded = execute(
                0L,
                new RecordingManaAccount(INITIAL_BALANCE),
                TransactionRecordingPort.applyingAll());
        ActionDamageTransactionResult rejected = engine(
                        new ActionExecutorRegistry(List.of()), resolverFor(plan(1)))
                .execute(
                        invocation(0L),
                        new RecordingManaAccount(INITIAL_BALANCE),
                        0,
                        TransactionRecordingGuard.allowing(),
                        TransactionRecordingPort.applyingAll());
        ActionDamageTransactionResult failed = execute(
                0L,
                new RecordingManaAccount(INITIAL_BALANCE),
                new TransactionRecordingPort(
                        true, List.of(EffectStepOutcome.notApplied())));
        ActionDamageTransactionResult partial = execute(
                MANA_COST,
                new RecordingManaAccount(INITIAL_BALANCE),
                new TransactionRecordingPort(
                        true, List.of(EffectStepOutcome.appliedWithFailure(1))));
        ActionDamageTransactionResult compensated = execute(
                MANA_COST,
                new RecordingManaAccount(INITIAL_BALANCE),
                new TransactionRecordingPort(
                        true, List.of(EffectStepOutcome.notApplied())));
        RecordingManaAccount failedRefundAccount =
                new RecordingManaAccount(INITIAL_BALANCE);
        ActionDamageTransactionResult compensationFailed = execute(
                MANA_COST,
                failedRefundAccount,
                new AvailabilityChangingPort(failedRefundAccount));

        Map<EffectTerminalStatus, EffectTraceStage> terminalStages = Map.of(
                EffectTerminalStatus.SUCCEEDED,
                EffectTraceStage.TERMINAL_SUCCEEDED,
                EffectTerminalStatus.REJECTED,
                EffectTraceStage.TERMINAL_REJECTED,
                EffectTerminalStatus.FAILED,
                EffectTraceStage.TERMINAL_FAILED,
                EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                EffectTraceStage.TERMINAL_PARTIAL,
                EffectTerminalStatus.COMPENSATED,
                EffectTraceStage.TERMINAL_COMPENSATED,
                EffectTerminalStatus.COMPENSATION_FAILED,
                EffectTraceStage.TERMINAL_COMPENSATION_FAILED);

        List<ActionDamageTransactionResult> results = List.of(
                succeeded,
                rejected,
                failed,
                partial,
                compensated,
                compensationFailed);
        assertEquals(32, P6EffectBounds.MAX_TRACE_ENTRIES);
        assertEquals(6, terminalStages.size());
        for (ActionDamageTransactionResult result : results) {
            EffectExecutionResult effect = result.effectResult();
            assertEquals(terminalStages.get(effect.status()), effect.trace().terminalStage());
            assertTraceShape(effect.trace());
        }
        assertEquals(6, results.stream()
                .map(result -> result.effectResult().status())
                .distinct()
                .count());
        assertArrayEquals(
                new Class<?>[] {int.class, EffectTraceStage.class, int.class},
                Arrays.stream(EffectTraceEntry.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getType)
                        .toArray(Class<?>[]::new));
    }

    private static ActionDamageTransactionResult execute(
            long manaCost,
            RecordingManaAccount account,
            DamageEffectCommitPort port) {
        return engine(resolverFor(plan(1)))
                .execute(
                        invocation(manaCost),
                        account,
                        0,
                        TransactionRecordingGuard.allowing(),
                        port);
    }

    private static void assertExactTrace(
            ActionDamageTransactionResult result, List<EffectTraceStage> expected) {
        assertEquals(expected, stages(result));
        assertTraceShape(result.effectResult().trace());
    }

    private static void assertTraceShape(EffectTrace trace) {
        List<EffectTraceEntry> entries = trace.entries();
        assertTrue(entries.size() <= P6EffectBounds.MAX_TRACE_ENTRIES);
        int nextStepIndex = 0;
        for (int index = 0; index < entries.size(); index++) {
            EffectTraceEntry entry = entries.get(index);
            assertEquals(index, entry.sequence());
            if (entry.stage().requiresStepIndex()) {
                assertEquals(nextStepIndex++, entry.stepIndex());
            } else {
                assertEquals(EffectTraceEntry.NOT_APPLICABLE_STEP_INDEX, entry.stepIndex());
            }
        }
        assertEquals(
                1L,
                entries.stream().filter(entry -> entry.stage().isTerminal()).count());
        assertTrue(entries.get(entries.size() - 1).stage().isTerminal());
    }

    private static final class AvailabilityChangingPort implements DamageEffectCommitPort {
        private final RecordingManaAccount account;

        private AvailabilityChangingPort(RecordingManaAccount account) {
            this.account = account;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            account.setAvailability(ManaAvailability.UNAVAILABLE);
            return EffectStepOutcome.notApplied();
        }
    }
}
