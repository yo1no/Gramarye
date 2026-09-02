package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ActionDamageTransactionDebitTest {
    @Test
    void sufficientDebitUsesExactSkillCostReceiptAndOneMutation() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(100L);
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = execute(30L, account, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        ManaDebited debit = assertInstanceOf(ManaDebited.class, result.manaSummary());
        ManaReceiptSnapshot snapshot = debit.debitReceipt();
        assertEquals(ManaOperationKind.DEBIT, snapshot.identity().operation());
        assertEquals(ManaReason.SKILL_COST, snapshot.identity().reason());
        assertEquals(ActionTransactionTestFixtures.ACCOUNT_ID, snapshot.identity().accountId());
        assertEquals(30L, snapshot.identity().amount());
        assertEquals(100L, snapshot.identity().beforeBalance());
        assertEquals(70L, snapshot.identity().afterBalance());
        assertEquals(ManaRefundState.OPEN, snapshot.refundState());
        assertEquals(1, result.manaMutationCount());
        assertEquals(1, result.manaSummary().mutationCount());
        assertEquals(70L, account.currentBalance());
        assertEquals(1, account.balanceWrites());
        assertEquals(List.of(0), port.committedIndexes());
    }

    @Test
    void maximumDebitAmountSucceedsAtMaximumBalance() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(
                P6ManaBounds.MAX_MANA_VALUE);
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = execute(
                P6ManaBounds.MAX_MANA_OPERATION_AMOUNT, account, port);

        assertEquals(EffectTerminalStatus.SUCCEEDED, result.effectResult().status());
        ManaDebited debit = assertInstanceOf(ManaDebited.class, result.manaSummary());
        assertEquals(
                P6ManaBounds.MAX_MANA_OPERATION_AMOUNT,
                debit.debitReceipt().identity().amount());
        assertEquals(0L, account.currentBalance());
        assertEquals(1, account.balanceWrites());
        assertEquals(1, result.manaMutationCount());
        assertEquals(List.of(0), port.committedIndexes());
    }

    @Test
    void insufficientManaRejectsWithoutStepOrBalanceWrite() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(9L);
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = execute(10L, account, port);

        assertEquals(EffectTerminalStatus.REJECTED, result.effectResult().status());
        assertEquals(
                EffectRejectReason.INSUFFICIENT_MANA,
                result.effectResult().rejectReason().orElseThrow());
        ManaDebitRejected rejected =
                assertInstanceOf(ManaDebitRejected.class, result.manaSummary());
        assertEquals(ManaRejectReason.INSUFFICIENT_MANA, rejected.rejectReason());
        assertEquals(0, result.manaMutationCount());
        assertEquals(9L, account.currentBalance());
        assertEquals(1, account.balanceReads());
        assertEquals(0, account.balanceWrites());
        assertEquals(1, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void unavailableManaRejectsWithoutBalanceReadOrStep() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(
                true,
                ActionTransactionTestFixtures.ACCOUNT_ID,
                ManaAvailability.UNAVAILABLE,
                100L,
                new ArrayList<>());
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        ActionDamageTransactionResult result = execute(10L, account, port);

        assertEquals(EffectTerminalStatus.REJECTED, result.effectResult().status());
        assertEquals(
                EffectRejectReason.MANA_STATE_UNAVAILABLE,
                result.effectResult().rejectReason().orElseThrow());
        ManaDebitRejected rejected =
                assertInstanceOf(ManaDebitRejected.class, result.manaSummary());
        assertEquals(ManaRejectReason.MANA_STATE_UNAVAILABLE, rejected.rejectReason());
        assertEquals(0, result.manaMutationCount());
        assertEquals(1, account.availabilityReads());
        assertEquals(0, account.balanceReads());
        assertEquals(0, account.balanceWrites());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void wrongThreadDebitIsInvariantWithZeroObservedMutation() {
        var account = new ActionTransactionTestFixtures.RecordingManaAccount(
                false,
                ActionTransactionTestFixtures.ACCOUNT_ID,
                ManaAvailability.AVAILABLE,
                100L,
                new ArrayList<>());
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> execute(10L, account, port));

        assertEquals(P6ExecutionInvariantCode.IMPOSSIBLE_MANA_REJECTION, failure.code());
        assertEquals(1, account.threadChecks());
        assertEquals(0, account.accountIdReads());
        assertEquals(0, account.availabilityReads());
        assertEquals(0, account.balanceReads());
        assertEquals(0, account.balanceWrites());
        assertEquals(100L, account.currentBalance());
        assertEquals(List.of(), port.committedIndexes());
    }

    @Test
    void impossibleDebitRejectionIsInvariantWithoutStepOrMutation() {
        var port = ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll();

        P6ExecutionInvariantException failure = assertThrows(
                P6ExecutionInvariantException.class,
                () -> ActionTransactionTestFixtures.engine(new DamageEffectResolver())
                        .execute(
                                ActionTransactionTestFixtures.invocation(10L),
                                null,
                                0,
                                ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                                port));

        assertEquals(P6ExecutionInvariantCode.IMPOSSIBLE_MANA_REJECTION, failure.code());
        assertEquals(1, port.availabilityChecks());
        assertEquals(List.of(), port.committedIndexes());
        assertTrue(port.order().contains("port-availability"));
    }

    private static ActionDamageTransactionResult execute(
            long manaCost,
            ActionTransactionTestFixtures.RecordingManaAccount account,
            ActionTransactionTestFixtures.TransactionRecordingPort port) {
        return ActionTransactionTestFixtures.engine(new DamageEffectResolver())
                .execute(
                        ActionTransactionTestFixtures.invocation(manaCost),
                        account,
                        0,
                        ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                        port);
    }
}
