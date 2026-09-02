package com.yo1no.gramarye.magic.runtime.mana;

import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.engine;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.invocation;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.plan;
import static com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.resolverFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.RecordingManaAccount;
import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.TransactionRecordingGuard;
import com.yo1no.gramarye.magic.runtime.mana.ActionTransactionTestFixtures.TransactionRecordingPort;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionCompensationTest {
    private static final long INITIAL_BALANCE = 100L;
    private static final long MANA_COST = 10L;

    @Test
    void firstNotAppliedAfterDebitRefundsOnceAndRetainsOriginalReason() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort port = new TransactionRecordingPort(
                true, List.of(EffectStepOutcome.notApplied()));

        ActionDamageTransactionResult result = execute(
                MANA_COST, account, TransactionRecordingGuard.allowing(), port);

        assertEquals(EffectTerminalStatus.COMPENSATED, result.effectResult().status());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.provisionalFailure().orElseThrow().reason());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.effectResult().failureReason().orElseThrow());
        assertInstanceOf(ManaRefunded.class, result.manaSummary());
        assertEquals(2, result.manaMutationCount());
        assertEquals(2, account.balanceWrites());
        assertEquals(INITIAL_BALANCE, account.currentBalance());
        assertEquals(List.of(0), port.committedIndexes());
    }

    @Test
    void beforeFirstStepCancellationAndDeadlineRefundOnce() {
        assertGuardFailureRefunds(
                EffectGuardDecision.CANCELLED,
                EffectFailureReason.EXECUTION_CANCELLED);
        assertGuardFailureRefunds(
                EffectGuardDecision.DEADLINE_EXCEEDED,
                EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED);
    }

    @Test
    void successfulRefundRestoresBalanceMarksReceiptAndConsumesSecondBudgetUnit() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort port = new TransactionRecordingPort(
                true, List.of(EffectStepOutcome.notApplied()));

        ActionDamageTransactionResult result = execute(
                MANA_COST, account, TransactionRecordingGuard.allowing(), port);
        ManaRefunded refunded = assertInstanceOf(ManaRefunded.class, result.manaSummary());

        assertEquals(ManaExecutionSummaryKind.REFUNDED, refunded.kind());
        assertEquals(ManaRefundState.REFUNDED, refunded.debitReceipt().refundState());
        assertEquals(
                ManaRefundState.NON_REFUNDABLE,
                refunded.refundReceipt().refundState());
        assertEquals(ManaOperationKind.DEBIT, refunded.debitReceipt().identity().operation());
        assertEquals(ManaOperationKind.REFUND, refunded.refundReceipt().identity().operation());
        assertEquals(ManaReason.SKILL_COST, refunded.debitReceipt().identity().reason());
        assertEquals(
                ManaReason.COMPENSATION_REFUND,
                refunded.refundReceipt().identity().reason());
        assertEquals(MANA_COST, refunded.debitReceipt().identity().amount());
        assertEquals(MANA_COST, refunded.refundReceipt().identity().amount());
        assertEquals(INITIAL_BALANCE, account.currentBalance());
        assertEquals(2, account.balanceWrites());
        assertEquals(2, refunded.mutationCount());
        assertEquals(P6EffectBounds.MAX_MANA_MUTATIONS_PER_EXECUTION, result.manaMutationCount());
    }

    @Test
    void zeroCostFailureDoesNotRefundAndKeepsOriginalFailure() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingPort port = new TransactionRecordingPort(
                true, List.of(EffectStepOutcome.notApplied()));

        ActionDamageTransactionResult result = execute(
                0L, account, TransactionRecordingGuard.allowing(), port);

        assertEquals(EffectTerminalStatus.FAILED, result.effectResult().status());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.effectResult().failureReason().orElseThrow());
        assertInstanceOf(ManaNotRequired.class, result.manaSummary());
        assertEquals(0, result.manaMutationCount());
        assertTrue(result.provisionalFailure().isEmpty());
        assertEquals(0, account.totalAccesses());
        assertEquals(INITIAL_BALANCE, account.currentBalance());
    }

    @Test
    void unavailableRefundProducesCompensationFailedAndKeepsOpenDebit() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        HookedOutcomePort port = new HookedOutcomePort(
                () -> account.setAvailability(ManaAvailability.UNAVAILABLE),
                EffectStepOutcome.notApplied());

        ActionDamageTransactionResult result = execute(
                MANA_COST, account, TransactionRecordingGuard.allowing(), port);
        ManaRefundFailed failed = assertInstanceOf(
                ManaRefundFailed.class, result.manaSummary());

        assertEquals(EffectTerminalStatus.COMPENSATION_FAILED, result.effectResult().status());
        assertEquals(
                EffectFailureReason.COMPENSATION_REFUND_FAILED,
                result.effectResult().failureReason().orElseThrow());
        assertEquals(ManaRejectReason.MANA_STATE_UNAVAILABLE, failed.refundRejectReason());
        assertEquals(ManaRefundState.OPEN, failed.debitReceipt().refundState());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.provisionalFailure().orElseThrow().reason());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, account.balanceWrites());
        assertEquals(INITIAL_BALANCE - MANA_COST, account.currentBalance());
        assertEquals(2, account.availabilityReads());
        assertEquals(1, port.commitCalls());
    }

    @Test
    void changedBalanceRefundFailureProducesCompensationFailedAndPreservesBothReasons() {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        long changedBalance = INITIAL_BALANCE - MANA_COST - 1L;
        HookedOutcomePort port = new HookedOutcomePort(
                () -> account.setBalance(changedBalance), EffectStepOutcome.notApplied());

        ActionDamageTransactionResult result = execute(
                MANA_COST, account, TransactionRecordingGuard.allowing(), port);
        ManaRefundFailed failed = assertInstanceOf(
                ManaRefundFailed.class, result.manaSummary());

        assertEquals(EffectTerminalStatus.COMPENSATION_FAILED, result.effectResult().status());
        assertEquals(
                EffectFailureReason.COMPENSATION_REFUND_FAILED,
                result.effectResult().failureReason().orElseThrow());
        assertEquals(
                EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                result.provisionalFailure().orElseThrow().reason());
        assertEquals(ManaRejectReason.INVALID_TRANSACTION_STATE, failed.refundRejectReason());
        assertEquals(ManaRefundState.OPEN, failed.debitReceipt().refundState());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, account.balanceWrites());
        assertEquals(changedBalance, account.currentBalance());
        assertEquals(2, account.balanceReads());
        assertEquals(1, port.commitCalls());
    }

    @Test
    void impossibleRefundRejectionsThrowInvariantWithoutRetryOrThirdMutation() {
        assertImpossibleRefundRejection(
                account -> account.setLogicThread(false), 2, 1, 1, 1);
        assertImpossibleRefundRejection(
                account -> account.setAccountId(UUID.fromString(
                        "70000000-0000-4000-8000-000000000099")),
                2,
                2,
                1,
                1);
    }

    private static ActionDamageTransactionResult execute(
            long manaCost,
            RecordingManaAccount account,
            EffectExecutionGuard guard,
            DamageEffectCommitPort port) {
        return engine(resolverFor(plan(1)))
                .execute(invocation(manaCost), account, 0, guard, port);
    }

    private static void assertGuardFailureRefunds(
            EffectGuardDecision decision, EffectFailureReason expectedReason) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        TransactionRecordingGuard guard = new TransactionRecordingGuard(point ->
                point.kind() == EffectGuardPointKind.BEFORE_STEP
                        ? decision
                        : EffectGuardDecision.ALLOWED);
        TransactionRecordingPort port = TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = execute(MANA_COST, account, guard, port);

        assertEquals(EffectTerminalStatus.COMPENSATED, result.effectResult().status());
        assertEquals(expectedReason, result.effectResult().failureReason().orElseThrow());
        assertEquals(expectedReason, result.provisionalFailure().orElseThrow().reason());
        assertInstanceOf(ManaRefunded.class, result.manaSummary());
        assertEquals(2, result.manaMutationCount());
        assertEquals(2, account.balanceWrites());
        assertEquals(INITIAL_BALANCE, account.currentBalance());
        assertTrue(port.committedIndexes().isEmpty());
    }

    private static void assertImpossibleRefundRejection(
            Consumer<RecordingManaAccount> makeRefundImpossible,
            int expectedThreadChecks,
            int expectedAccountIdReads,
            int expectedAvailabilityReads,
            int expectedBalanceReads) {
        RecordingManaAccount account = new RecordingManaAccount(INITIAL_BALANCE);
        HookedOutcomePort port = new HookedOutcomePort(
                () -> makeRefundImpossible.accept(account), EffectStepOutcome.notApplied());

        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> execute(MANA_COST, account, TransactionRecordingGuard.allowing(), port));

        assertEquals(P6ExecutionInvariantCode.IMPOSSIBLE_MANA_REJECTION, failure.code());
        assertEquals(1, account.balanceWrites());
        assertEquals(INITIAL_BALANCE - MANA_COST, account.currentBalance());
        assertEquals(1, port.commitCalls());
        assertEquals(expectedThreadChecks, account.threadChecks());
        assertEquals(expectedAccountIdReads, account.accountIdReads());
        assertEquals(expectedAvailabilityReads, account.availabilityReads());
        assertEquals(expectedBalanceReads, account.balanceReads());
    }

    private static final class HookedOutcomePort implements DamageEffectCommitPort {
        private final Runnable beforeOutcome;
        private final EffectStepOutcome outcome;
        private int commitCalls;

        private HookedOutcomePort(Runnable beforeOutcome, EffectStepOutcome outcome) {
            this.beforeOutcome = beforeOutcome;
            this.outcome = outcome;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public EffectStepOutcome commitDamage(DamageEffectStep step) {
            commitCalls++;
            beforeOutcome.run();
            return outcome;
        }

        private int commitCalls() {
            return commitCalls;
        }
    }
}
