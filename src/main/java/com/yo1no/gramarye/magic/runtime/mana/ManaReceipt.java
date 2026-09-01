package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.UUID;

enum ManaRefundState {
    OPEN,
    REFUNDED,
    NON_REFUNDABLE
}

record ManaReceiptIdentity(
        ManaOperationKind operation,
        ManaReason reason,
        UUID accountId,
        long amount,
        long beforeBalance,
        long afterBalance) {
    ManaReceiptIdentity {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(accountId, "accountId");
    }
}

/** Call-scoped proof of one successful mana mutation. */
final class ManaReceipt {
    private final ManaReceiptIdentity identity;
    private ManaRefundState refundState;

    private ManaReceipt(ManaReceiptIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
        validateIdentity(identity);
        refundState = identity.operation() == ManaOperationKind.DEBIT
                ? ManaRefundState.OPEN
                : ManaRefundState.NON_REFUNDABLE;
    }

    static ManaReceipt create(
            ManaOperationKind operation,
            ManaReason reason,
            UUID accountId,
            long amount,
            long beforeBalance,
            long afterBalance) {
        return new ManaReceipt(new ManaReceiptIdentity(
                operation, reason, accountId, amount, beforeBalance, afterBalance));
    }

    ManaReceiptIdentity identity() {
        return identity;
    }

    ManaOperationKind operation() {
        return identity.operation();
    }

    ManaReason reason() {
        return identity.reason();
    }

    UUID accountId() {
        return identity.accountId();
    }

    long amount() {
        return identity.amount();
    }

    long beforeBalance() {
        return identity.beforeBalance();
    }

    long afterBalance() {
        return identity.afterBalance();
    }

    ManaRefundState refundState() {
        return refundState;
    }

    void markRefunded() {
        if (refundState != ManaRefundState.OPEN) {
            throw new IllegalStateException("only an open debit receipt can be refunded");
        }
        refundState = ManaRefundState.REFUNDED;
    }

    private static void validateIdentity(ManaReceiptIdentity identity) {
        if (identity.amount() < P6ManaBounds.MIN_MUTATION_AMOUNT
                || identity.amount() > P6ManaBounds.MAX_MANA_OPERATION_AMOUNT
                || !validBalance(identity.beforeBalance())
                || !validBalance(identity.afterBalance())
                || !reasonMatches(identity.operation(), identity.reason())) {
            throw new IllegalArgumentException("invalid mana receipt identity");
        }

        long expectedAfter = switch (identity.operation()) {
            case DEBIT -> Math.subtractExact(identity.beforeBalance(), identity.amount());
            case CREDIT, REFUND ->
                    Math.addExact(identity.beforeBalance(), identity.amount());
        };
        if (expectedAfter != identity.afterBalance()) {
            throw new IllegalArgumentException("inconsistent mana receipt balances");
        }
    }

    private static boolean validBalance(long balance) {
        return balance >= 0 && balance <= P6ManaBounds.MAX_MANA_VALUE;
    }

    private static boolean reasonMatches(ManaOperationKind operation, ManaReason reason) {
        return switch (operation) {
            case DEBIT -> reason == ManaReason.SKILL_COST
                    || reason == ManaReason.ADMIN_ADJUSTMENT;
            case CREDIT -> reason == ManaReason.ITEM_RECOVERY
                    || reason == ManaReason.ADMIN_ADJUSTMENT;
            case REFUND -> reason == ManaReason.COMPENSATION_REFUND;
        };
    }
}
