package com.yo1no.gramarye.magic.runtime.mana;

import java.util.UUID;

/** Sole authority for synchronous mutations of one mana account. */
final class ManaTransactionService {
    ManaTransactionResult debit(
            ManaAccountAccess account, ManaReason reason, long amount) {
        if (!validAmount(amount)) {
            return rejected(ManaOperationKind.DEBIT, ManaRejectReason.INVALID_AMOUNT);
        }
        if (account == null || !validDebitReason(reason)) {
            return rejected(
                    ManaOperationKind.DEBIT,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (!account.isLogicThread()) {
            return rejected(ManaOperationKind.DEBIT, ManaRejectReason.WRONG_THREAD);
        }

        UUID accountId = account.accountId();
        if (accountId == null) {
            return rejected(
                    ManaOperationKind.DEBIT,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (account.availability() != ManaAvailability.AVAILABLE) {
            return rejected(
                    ManaOperationKind.DEBIT,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }

        long beforeBalance = account.balance();
        if (!validBalance(beforeBalance)) {
            return rejected(
                    ManaOperationKind.DEBIT,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }
        long afterBalance = Math.subtractExact(beforeBalance, amount);
        if (afterBalance < 0) {
            return rejected(
                    ManaOperationKind.DEBIT,
                    ManaRejectReason.INSUFFICIENT_MANA);
        }

        account.writeBalance(afterBalance);
        return accepted(
                ManaOperationKind.DEBIT,
                reason,
                accountId,
                amount,
                beforeBalance,
                afterBalance);
    }

    ManaTransactionResult credit(
            ManaAccountAccess account, ManaReason reason, long amount) {
        if (!validAmount(amount)) {
            return rejected(ManaOperationKind.CREDIT, ManaRejectReason.INVALID_AMOUNT);
        }
        if (account == null || !validCreditReason(reason)) {
            return rejected(
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (!account.isLogicThread()) {
            return rejected(ManaOperationKind.CREDIT, ManaRejectReason.WRONG_THREAD);
        }

        UUID accountId = account.accountId();
        if (accountId == null) {
            return rejected(
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (account.availability() != ManaAvailability.AVAILABLE) {
            return rejected(
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }

        long beforeBalance = account.balance();
        if (!validBalance(beforeBalance)) {
            return rejected(
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }
        long afterBalance = Math.addExact(beforeBalance, amount);
        if (afterBalance > P6ManaBounds.MAX_MANA_VALUE) {
            return rejected(
                    ManaOperationKind.CREDIT,
                    ManaRejectReason.BALANCE_LIMIT_EXCEEDED);
        }

        account.writeBalance(afterBalance);
        return accepted(
                ManaOperationKind.CREDIT,
                reason,
                accountId,
                amount,
                beforeBalance,
                afterBalance);
    }

    ManaTransactionResult refund(ManaAccountAccess account, ManaReceipt debitReceipt) {
        if (account == null || debitReceipt == null) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (!account.isLogicThread()) {
            return rejected(ManaOperationKind.REFUND, ManaRejectReason.WRONG_THREAD);
        }

        ManaOperationKind receiptOperation = debitReceipt.operation();
        ManaReason receiptReason = debitReceipt.reason();
        UUID receiptAccountId = debitReceipt.accountId();
        long receiptAmount = debitReceipt.amount();
        long receiptBeforeBalance = debitReceipt.beforeBalance();
        long receiptAfterBalance = debitReceipt.afterBalance();
        ManaRefundState receiptRefundState = debitReceipt.refundState();
        if (receiptOperation != ManaOperationKind.DEBIT
                || !validDebitReason(receiptReason)) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }

        UUID accountId = account.accountId();
        if (accountId == null) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (!receiptAccountId.equals(accountId)) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.RECEIPT_ACCOUNT_MISMATCH);
        }
        if (receiptRefundState == ManaRefundState.REFUNDED) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.ALREADY_REFUNDED);
        }
        if (receiptRefundState != ManaRefundState.OPEN) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        if (account.availability() != ManaAvailability.AVAILABLE) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }

        long currentBalance = account.balance();
        if (!validBalance(currentBalance)) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.MANA_STATE_UNAVAILABLE);
        }
        if (currentBalance != receiptAfterBalance) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }
        long restoredBalance = Math.addExact(currentBalance, receiptAmount);
        if (!validBalance(restoredBalance)
                || restoredBalance != receiptBeforeBalance) {
            return rejected(
                    ManaOperationKind.REFUND,
                    ManaRejectReason.INVALID_TRANSACTION_STATE);
        }

        account.writeBalance(restoredBalance);
        debitReceipt.markRefunded();
        return accepted(
                ManaOperationKind.REFUND,
                ManaReason.COMPENSATION_REFUND,
                accountId,
                receiptAmount,
                currentBalance,
                restoredBalance);
    }

    private static ManaTransactionResult accepted(
            ManaOperationKind operation,
            ManaReason reason,
            UUID accountId,
            long amount,
            long beforeBalance,
            long afterBalance) {
        var receipt = ManaReceipt.create(
                operation, reason, accountId, amount, beforeBalance, afterBalance);
        return new ManaTransactionResult.Accepted(
                operation,
                reason,
                accountId,
                amount,
                beforeBalance,
                afterBalance,
                receipt);
    }

    private static ManaTransactionResult rejected(
            ManaOperationKind operation, ManaRejectReason reason) {
        return new ManaTransactionResult.Rejected(operation, reason);
    }

    private static boolean validAmount(long amount) {
        return amount >= P6ManaBounds.MIN_MUTATION_AMOUNT
                && amount <= P6ManaBounds.MAX_MANA_OPERATION_AMOUNT;
    }

    private static boolean validBalance(long balance) {
        return balance >= 0 && balance <= P6ManaBounds.MAX_MANA_VALUE;
    }

    private static boolean validDebitReason(ManaReason reason) {
        return reason == ManaReason.SKILL_COST || reason == ManaReason.ADMIN_ADJUSTMENT;
    }

    private static boolean validCreditReason(ManaReason reason) {
        return reason == ManaReason.ITEM_RECOVERY || reason == ManaReason.ADMIN_ADJUSTMENT;
    }
}
