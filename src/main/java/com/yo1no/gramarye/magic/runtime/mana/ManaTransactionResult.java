package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.UUID;

/** Closed result of one attempted mana operation. */
sealed interface ManaTransactionResult
        permits ManaTransactionResult.Accepted, ManaTransactionResult.Rejected {
    ManaOperationKind operation();

    record Accepted(
            ManaOperationKind operation,
            ManaReason reason,
            UUID accountId,
            long amount,
            long beforeBalance,
            long afterBalance,
            ManaReceipt receipt) implements ManaTransactionResult {
        public Accepted {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(receipt, "receipt");
            if (receipt.operation() != operation
                    || receipt.reason() != reason
                    || !receipt.accountId().equals(accountId)
                    || receipt.amount() != amount
                    || receipt.beforeBalance() != beforeBalance
                    || receipt.afterBalance() != afterBalance) {
                throw new IllegalArgumentException("accepted result must match its receipt");
            }
        }
    }

    record Rejected(
            ManaOperationKind operation,
            ManaRejectReason rejectReason) implements ManaTransactionResult {
        public Rejected {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(rejectReason, "rejectReason");
        }
    }
}
