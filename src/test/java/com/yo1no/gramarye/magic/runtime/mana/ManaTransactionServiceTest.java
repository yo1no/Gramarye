package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ManaTransactionServiceTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("60000000-0000-4000-8000-000000000002");
    private final ManaTransactionService service = new ManaTransactionService();

    @Test
    void debitSucceedsWithExactOneWriteAndReceipt() {
        var account = availableAccount(100L);
        var accepted = accepted(service.debit(account, ManaReason.SKILL_COST, 30L));

        assertAccepted(
                accepted,
                ManaOperationKind.DEBIT,
                ManaReason.SKILL_COST,
                ACCOUNT_ID,
                30L,
                100L,
                70L);
        assertEquals(70L, account.balance);
        assertEquals(1, account.balanceWrites);
        assertEquals(ManaRefundState.OPEN, accepted.receipt().refundState());
    }

    @Test
    void debitCanReachExactZero() {
        var account = availableAccount(25L);
        var accepted = accepted(service.debit(account, ManaReason.ADMIN_ADJUSTMENT, 25L));
        assertEquals(0L, accepted.afterBalance());
        assertEquals(0L, account.balance);
        assertEquals(1, account.balanceWrites);
    }

    @Test
    void maximumDebitAmountIsAcceptedAtMaximumBalance() {
        var account = availableAccount(P6ManaBounds.MAX_MANA_VALUE);
        var accepted = accepted(service.debit(
                account,
                ManaReason.SKILL_COST,
                P6ManaBounds.MAX_MANA_OPERATION_AMOUNT));

        assertEquals(P6ManaBounds.MAX_MANA_OPERATION_AMOUNT, accepted.amount());
        assertEquals(P6ManaBounds.MAX_MANA_VALUE, accepted.beforeBalance());
        assertEquals(0L, accepted.afterBalance());
        assertEquals(1, account.balanceWrites);
    }

    @Test
    void insufficientDebitRejectsWithoutMutationOrReceipt() {
        var account = availableAccount(9L);
        assertRejected(
                service.debit(account, ManaReason.SKILL_COST, 10L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.INSUFFICIENT_MANA);
        assertNoWrite(account, 9L);
    }

    @Test
    void zeroDebitAmountRejectsBeforeAccountAccess() {
        var account = availableAccount(10L);
        assertRejected(
                service.debit(account, ManaReason.SKILL_COST, 0L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.INVALID_AMOUNT);
        assertZeroAccountAccess(account);
    }

    @Test
    void negativeDebitAmountRejectsBeforeAccountAccess() {
        var account = availableAccount(10L);
        assertRejected(
                service.debit(account, ManaReason.SKILL_COST, -1L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.INVALID_AMOUNT);
        assertZeroAccountAccess(account);
    }

    @Test
    void debitAmountAboveMaximumRejectsBeforeAccountAccess() {
        var account = availableAccount(P6ManaBounds.MAX_MANA_VALUE);
        assertRejected(
                service.debit(
                        account,
                        ManaReason.SKILL_COST,
                        P6ManaBounds.MAX_MANA_OPERATION_AMOUNT + 1L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.INVALID_AMOUNT);
        assertZeroAccountAccess(account);
    }

    @Test
    void unavailableDebitRejectsWithoutBalanceRead() {
        var account = unavailableAccount();
        assertRejected(
                service.debit(account, ManaReason.SKILL_COST, 1L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.MANA_STATE_UNAVAILABLE);
        assertEquals(1, account.availabilityReads);
        assertEquals(0, account.balanceReads);
        assertEquals(0, account.balanceWrites);
    }

    @Test
    void debitReasonMismatchIsTypedRejection() {
        var account = availableAccount(10L);
        assertRejected(
                service.debit(account, ManaReason.ITEM_RECOVERY, 1L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertZeroAccountAccess(account);
    }

    @Test
    void creditSucceedsWithExactOneWriteAndReceipt() {
        var account = availableAccount(40L);
        var accepted = accepted(service.credit(account, ManaReason.ITEM_RECOVERY, 12L));

        assertAccepted(
                accepted,
                ManaOperationKind.CREDIT,
                ManaReason.ITEM_RECOVERY,
                ACCOUNT_ID,
                12L,
                40L,
                52L);
        assertEquals(52L, account.balance);
        assertEquals(1, account.balanceWrites);
        assertEquals(ManaRefundState.NON_REFUNDABLE, accepted.receipt().refundState());
    }

    @Test
    void creditCanReachExactCap() {
        var account = availableAccount(P6ManaBounds.MAX_MANA_VALUE - 1L);
        var accepted = accepted(service.credit(account, ManaReason.ADMIN_ADJUSTMENT, 1L));
        assertEquals(P6ManaBounds.MAX_MANA_VALUE, accepted.afterBalance());
        assertEquals(P6ManaBounds.MAX_MANA_VALUE, account.balance);
    }

    @Test
    void creditBeyondCapRejectsWithoutMutation() {
        var account = availableAccount(P6ManaBounds.MAX_MANA_VALUE);
        assertRejected(
                service.credit(account, ManaReason.ITEM_RECOVERY, 1L),
                ManaOperationKind.CREDIT,
                ManaRejectReason.BALANCE_LIMIT_EXCEEDED);
        assertNoWrite(account, P6ManaBounds.MAX_MANA_VALUE);
    }

    @Test
    void maximumCreditAmountUsesCheckedCapRejection() {
        var account = availableAccount(1L);
        assertRejected(
                service.credit(
                        account,
                        ManaReason.ITEM_RECOVERY,
                        P6ManaBounds.MAX_MANA_OPERATION_AMOUNT),
                ManaOperationKind.CREDIT,
                ManaRejectReason.BALANCE_LIMIT_EXCEEDED);
        assertNoWrite(account, 1L);
    }

    @Test
    void outOfRangeStoredBalanceRejectsBeforeCreditArithmetic() {
        var account = availableAccount(Long.MAX_VALUE);
        assertRejected(
                service.credit(account, ManaReason.ITEM_RECOVERY, 1L),
                ManaOperationKind.CREDIT,
                ManaRejectReason.MANA_STATE_UNAVAILABLE);
        assertEquals(1, account.balanceReads);
        assertNoWrite(account, Long.MAX_VALUE);
    }

    @Test
    void invalidCreditAmountsRejectBeforeAccountAccess() {
        for (var amount : new long[] {0L, -1L, P6ManaBounds.MAX_MANA_OPERATION_AMOUNT + 1L}) {
            var account = availableAccount(10L);
            assertRejected(
                    service.credit(account, ManaReason.ITEM_RECOVERY, amount),
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.INVALID_AMOUNT);
            assertZeroAccountAccess(account);
        }
    }

    @Test
    void unavailableCreditRejectsWithoutBalanceRead() {
        var account = unavailableAccount();
        assertRejected(
                service.credit(account, ManaReason.ITEM_RECOVERY, 1L),
                ManaOperationKind.CREDIT,
                ManaRejectReason.MANA_STATE_UNAVAILABLE);
        assertEquals(1, account.availabilityReads);
        assertEquals(0, account.balanceReads);
        assertEquals(0, account.balanceWrites);
    }

    @Test
    void creditReasonMismatchIsTypedRejection() {
        var account = availableAccount(10L);
        assertRejected(
                service.credit(account, ManaReason.SKILL_COST, 1L),
                ManaOperationKind.CREDIT,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertZeroAccountAccess(account);
    }

    @Test
    void successfulDebitRefundRestoresExactBeforeBalanceOnce() {
        var account = availableAccount(100L);
        var debit = accepted(service.debit(account, ManaReason.SKILL_COST, 35L));
        var debitReceipt = debit.receipt();
        account.resetCounters();

        var refund = accepted(service.refund(account, debitReceipt));
        assertAccepted(
                refund,
                ManaOperationKind.REFUND,
                ManaReason.COMPENSATION_REFUND,
                ACCOUNT_ID,
                35L,
                65L,
                100L);
        assertEquals(100L, account.balance);
        assertEquals(1, account.balanceWrites);
        assertEquals(ManaRefundState.REFUNDED, debitReceipt.refundState());
        assertNotSame(debitReceipt, refund.receipt());
        assertEquals(ManaRefundState.NON_REFUNDABLE, refund.receipt().refundState());
    }

    @Test
    void secondRefundIsRejectedWithoutAnotherWrite() {
        var account = availableAccount(100L);
        var debitReceipt = accepted(
                service.debit(account, ManaReason.SKILL_COST, 10L)).receipt();
        accepted(service.refund(account, debitReceipt));
        account.resetCounters();

        assertRejected(
                service.refund(account, debitReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.ALREADY_REFUNDED);
        assertNoWrite(account, 100L);
        assertEquals(ManaRefundState.REFUNDED, debitReceipt.refundState());
    }

    @Test
    void refundAccountMismatchDoesNotMarkOrMutate() {
        var source = availableAccount(100L);
        var debitReceipt = accepted(
                service.debit(source, ManaReason.SKILL_COST, 10L)).receipt();
        var other = new TestAccount(true, OTHER_ACCOUNT_ID, ManaAvailability.AVAILABLE, 90L);

        assertRejected(
                service.refund(other, debitReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.RECEIPT_ACCOUNT_MISMATCH);
        assertNoWrite(other, 90L);
        assertEquals(ManaRefundState.OPEN, debitReceipt.refundState());
    }

    @Test
    void creditReceiptCannotBeRefunded() {
        var account = availableAccount(10L);
        var creditReceipt = accepted(
                service.credit(account, ManaReason.ITEM_RECOVERY, 1L)).receipt();
        account.resetCounters();

        assertRejected(
                service.refund(account, creditReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertNoWrite(account, 11L);
        assertEquals(ManaRefundState.NON_REFUNDABLE, creditReceipt.refundState());
    }

    @Test
    void refundReceiptCannotBeRefunded() {
        var account = availableAccount(10L);
        var debitReceipt = accepted(
                service.debit(account, ManaReason.SKILL_COST, 1L)).receipt();
        var refundReceipt = accepted(service.refund(account, debitReceipt)).receipt();
        account.resetCounters();

        assertRejected(
                service.refund(account, refundReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertNoWrite(account, 10L);
    }

    @Test
    void unavailableRefundRejectsWithoutBalanceReadOrReceiptMutation() {
        var account = availableAccount(10L);
        var debitReceipt = accepted(
                service.debit(account, ManaReason.SKILL_COST, 1L)).receipt();
        account.availability = ManaAvailability.UNAVAILABLE;
        account.resetCounters();

        assertRejected(
                service.refund(account, debitReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.MANA_STATE_UNAVAILABLE);
        assertEquals(1, account.availabilityReads);
        assertEquals(0, account.balanceReads);
        assertEquals(0, account.balanceWrites);
        assertEquals(ManaRefundState.OPEN, debitReceipt.refundState());
    }

    @Test
    void changedBalanceRejectsRefundWithoutRetry() {
        var account = availableAccount(10L);
        var debitReceipt = accepted(
                service.debit(account, ManaReason.SKILL_COST, 1L)).receipt();
        account.balance = 8L;
        account.resetCounters();

        assertRejected(
                service.refund(account, debitReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertEquals(1, account.balanceReads);
        assertEquals(0, account.balanceWrites);
        assertEquals(8L, account.balance);
        assertEquals(ManaRefundState.OPEN, debitReceipt.refundState());
    }

    @Test
    void nullRefundReceiptIsTypedRejectionBeforeThreadCheck() {
        var account = availableAccount(10L);
        assertRejected(
                service.refund(account, null),
                ManaOperationKind.REFUND,
                ManaRejectReason.INVALID_TRANSACTION_STATE);
        assertZeroAccountAccess(account);
    }

    @Test
    void wrongThreadDebitHasZeroAccountMutationAndReceiptCreation() {
        var account = wrongThreadAccount(10L);
        assertRejected(
                service.debit(account, ManaReason.SKILL_COST, 1L),
                ManaOperationKind.DEBIT,
                ManaRejectReason.WRONG_THREAD);
        assertOnlyThreadChecked(account);
    }

    @Test
    void wrongThreadCreditHasZeroAccountMutationAndReceiptCreation() {
        var account = wrongThreadAccount(10L);
        assertRejected(
                service.credit(account, ManaReason.ITEM_RECOVERY, 1L),
                ManaOperationKind.CREDIT,
                ManaRejectReason.WRONG_THREAD);
        assertOnlyThreadChecked(account);
    }

    @Test
    void wrongThreadRefundReadsNoReceiptAndMutatesNothing() {
        var allowed = availableAccount(10L);
        var debitReceipt = accepted(
                service.debit(allowed, ManaReason.SKILL_COST, 1L)).receipt();
        var wrongThread = wrongThreadAccount(9L);

        assertRejected(
                service.refund(wrongThread, debitReceipt),
                ManaOperationKind.REFUND,
                ManaRejectReason.WRONG_THREAD);
        assertOnlyThreadChecked(wrongThread);
        assertEquals(ManaRefundState.OPEN, debitReceipt.refundState());
    }

    @Test
    void receiptIdentityIsDeterministicAndAccountBound() {
        var first = accepted(service.debit(
                availableAccount(20L), ManaReason.SKILL_COST, 2L)).receipt();
        var second = accepted(service.debit(
                availableAccount(20L), ManaReason.SKILL_COST, 2L)).receipt();

        assertEquals(first.identity(), second.identity());
        assertEquals(ACCOUNT_ID, first.accountId());
        assertNotNull(first.identity());
    }

    @Test
    void originalDebitReceiptStateChangesOnlyOpenToRefunded() {
        var account = availableAccount(20L);
        var receipt = accepted(
                service.debit(account, ManaReason.SKILL_COST, 2L)).receipt();
        assertEquals(ManaRefundState.OPEN, receipt.refundState());
        accepted(service.refund(account, receipt));
        assertEquals(ManaRefundState.REFUNDED, receipt.refundState());
    }

    private static TestAccount availableAccount(long balance) {
        return new TestAccount(true, ACCOUNT_ID, ManaAvailability.AVAILABLE, balance);
    }

    private static TestAccount unavailableAccount() {
        return new TestAccount(true, ACCOUNT_ID, ManaAvailability.UNAVAILABLE, 0L);
    }

    private static TestAccount wrongThreadAccount(long balance) {
        return new TestAccount(false, ACCOUNT_ID, ManaAvailability.AVAILABLE, balance);
    }

    private static ManaTransactionResult.Accepted accepted(ManaTransactionResult result) {
        return assertInstanceOf(ManaTransactionResult.Accepted.class, result);
    }

    private static void assertRejected(
            ManaTransactionResult result,
            ManaOperationKind operation,
            ManaRejectReason reason) {
        var rejected = assertInstanceOf(ManaTransactionResult.Rejected.class, result);
        assertEquals(operation, rejected.operation());
        assertEquals(reason, rejected.rejectReason());
    }

    private static void assertAccepted(
            ManaTransactionResult.Accepted result,
            ManaOperationKind operation,
            ManaReason reason,
            UUID accountId,
            long amount,
            long before,
            long after) {
        assertEquals(operation, result.operation());
        assertEquals(reason, result.reason());
        assertEquals(accountId, result.accountId());
        assertEquals(amount, result.amount());
        assertEquals(before, result.beforeBalance());
        assertEquals(after, result.afterBalance());
        assertSame(result.receipt(), result.receipt());
        assertEquals(result.receipt().identity(), new ManaReceiptIdentity(
                operation, reason, accountId, amount, before, after));
    }

    private static void assertNoWrite(TestAccount account, long expectedBalance) {
        assertEquals(0, account.balanceWrites);
        assertEquals(expectedBalance, account.balance);
    }

    private static void assertZeroAccountAccess(TestAccount account) {
        assertEquals(0, account.threadChecks);
        assertEquals(0, account.accountIdReads);
        assertEquals(0, account.availabilityReads);
        assertEquals(0, account.balanceReads);
        assertEquals(0, account.balanceWrites);
    }

    private static void assertOnlyThreadChecked(TestAccount account) {
        assertEquals(1, account.threadChecks);
        assertEquals(0, account.accountIdReads);
        assertEquals(0, account.availabilityReads);
        assertEquals(0, account.balanceReads);
        assertEquals(0, account.balanceWrites);
    }

    private static final class TestAccount implements ManaAccountAccess {
        private final boolean logicThread;
        private final UUID accountId;
        private ManaAvailability availability;
        private long balance;
        private int threadChecks;
        private int accountIdReads;
        private int availabilityReads;
        private int balanceReads;
        private int balanceWrites;

        private TestAccount(
                boolean logicThread,
                UUID accountId,
                ManaAvailability availability,
                long balance) {
            this.logicThread = logicThread;
            this.accountId = accountId;
            this.availability = availability;
            this.balance = balance;
        }

        @Override
        public boolean isLogicThread() {
            threadChecks++;
            return logicThread;
        }

        @Override
        public UUID accountId() {
            accountIdReads++;
            return accountId;
        }

        @Override
        public ManaAvailability availability() {
            availabilityReads++;
            return availability;
        }

        @Override
        public long balance() {
            balanceReads++;
            return balance;
        }

        @Override
        public void writeBalance(long replacement) {
            balanceWrites++;
            balance = replacement;
        }

        private void resetCounters() {
            threadChecks = 0;
            accountIdReads = 0;
            availabilityReads = 0;
            balanceReads = 0;
            balanceWrites = 0;
        }
    }
}
