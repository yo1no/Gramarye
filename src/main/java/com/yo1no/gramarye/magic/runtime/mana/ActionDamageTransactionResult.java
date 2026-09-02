package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.Optional;

enum ManaExecutionSummaryKind {
    MANA_NOT_REQUIRED,
    DEBIT_REJECTED,
    DEBITED,
    REFUNDED,
    REFUND_FAILED
}

sealed interface ManaExecutionSummary
        permits ManaNotRequired,
                ManaDebitRejected,
                ManaDebited,
                ManaRefunded,
                ManaRefundFailed {
    ManaExecutionSummaryKind kind();

    int mutationCount();
}

record ManaReceiptSnapshot(ManaReceiptIdentity identity, ManaRefundState refundState) {
    ManaReceiptSnapshot {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(refundState, "refundState");
        if (identity.amount() < P6ManaBounds.MIN_MUTATION_AMOUNT
                || identity.amount() > P6ManaBounds.MAX_MANA_OPERATION_AMOUNT
                || identity.beforeBalance() < 0
                || identity.beforeBalance() > P6ManaBounds.MAX_MANA_VALUE
                || identity.afterBalance() < 0
                || identity.afterBalance() > P6ManaBounds.MAX_MANA_VALUE
                || expectedAfter(identity) != identity.afterBalance()) {
            throw invalidResult();
        }
        if (identity.operation() == ManaOperationKind.DEBIT) {
            if (refundState != ManaRefundState.OPEN
                    && refundState != ManaRefundState.REFUNDED) {
                throw invalidResult();
            }
        } else if (identity.operation() == ManaOperationKind.REFUND) {
            if (identity.reason() != ManaReason.COMPENSATION_REFUND
                    || refundState != ManaRefundState.NON_REFUNDABLE) {
                throw invalidResult();
            }
        } else {
            throw invalidResult();
        }
    }

    static ManaReceiptSnapshot from(ManaReceipt receipt) {
        Objects.requireNonNull(receipt, "receipt");
        ManaReceiptIdentity identity = receipt.identity();
        return new ManaReceiptSnapshot(
                new ManaReceiptIdentity(
                        identity.operation(),
                        identity.reason(),
                        identity.accountId(),
                        identity.amount(),
                        identity.beforeBalance(),
                        identity.afterBalance()),
                receipt.refundState());
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }

    private static long expectedAfter(ManaReceiptIdentity identity) {
        return switch (identity.operation()) {
            case DEBIT -> identity.beforeBalance() - identity.amount();
            case CREDIT, REFUND -> identity.beforeBalance() + identity.amount();
        };
    }
}

record ManaNotRequired() implements ManaExecutionSummary {
    @Override
    public ManaExecutionSummaryKind kind() {
        return ManaExecutionSummaryKind.MANA_NOT_REQUIRED;
    }

    @Override
    public int mutationCount() {
        return 0;
    }
}

record ManaDebitRejected(ManaRejectReason rejectReason) implements ManaExecutionSummary {
    ManaDebitRejected {
        Objects.requireNonNull(rejectReason, "rejectReason");
        if (rejectReason != ManaRejectReason.INSUFFICIENT_MANA
                && rejectReason != ManaRejectReason.MANA_STATE_UNAVAILABLE) {
            throw invalidResult();
        }
    }

    @Override
    public ManaExecutionSummaryKind kind() {
        return ManaExecutionSummaryKind.DEBIT_REJECTED;
    }

    @Override
    public int mutationCount() {
        return 0;
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}

record ManaDebited(ManaReceiptSnapshot debitReceipt) implements ManaExecutionSummary {
    ManaDebited {
        Objects.requireNonNull(debitReceipt, "debitReceipt");
        if (debitReceipt.identity().operation() != ManaOperationKind.DEBIT
                || debitReceipt.identity().reason() != ManaReason.SKILL_COST
                || debitReceipt.refundState() != ManaRefundState.OPEN) {
            throw invalidResult();
        }
    }

    @Override
    public ManaExecutionSummaryKind kind() {
        return ManaExecutionSummaryKind.DEBITED;
    }

    @Override
    public int mutationCount() {
        return 1;
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}

record ManaRefunded(
        ManaReceiptSnapshot debitReceipt,
        ManaReceiptSnapshot refundReceipt) implements ManaExecutionSummary {
    ManaRefunded {
        Objects.requireNonNull(debitReceipt, "debitReceipt");
        Objects.requireNonNull(refundReceipt, "refundReceipt");
        ManaReceiptIdentity debit = debitReceipt.identity();
        ManaReceiptIdentity refund = refundReceipt.identity();
        if (debit.operation() != ManaOperationKind.DEBIT
                || debit.reason() != ManaReason.SKILL_COST
                || debitReceipt.refundState() != ManaRefundState.REFUNDED
                || refund.operation() != ManaOperationKind.REFUND
                || refund.reason() != ManaReason.COMPENSATION_REFUND
                || refundReceipt.refundState() != ManaRefundState.NON_REFUNDABLE
                || !debit.accountId().equals(refund.accountId())
                || debit.amount() != refund.amount()
                || debit.afterBalance() != refund.beforeBalance()
                || debit.beforeBalance() != refund.afterBalance()) {
            throw invalidResult();
        }
    }

    @Override
    public ManaExecutionSummaryKind kind() {
        return ManaExecutionSummaryKind.REFUNDED;
    }

    @Override
    public int mutationCount() {
        return 2;
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}

record ManaRefundFailed(
        ManaReceiptSnapshot debitReceipt,
        ManaRejectReason refundRejectReason) implements ManaExecutionSummary {
    ManaRefundFailed {
        Objects.requireNonNull(debitReceipt, "debitReceipt");
        Objects.requireNonNull(refundRejectReason, "refundRejectReason");
        if (debitReceipt.identity().operation() != ManaOperationKind.DEBIT
                || debitReceipt.identity().reason() != ManaReason.SKILL_COST
                || debitReceipt.refundState() != ManaRefundState.OPEN
                || !isClosedRefundFailure(refundRejectReason)) {
            throw invalidResult();
        }
    }

    @Override
    public ManaExecutionSummaryKind kind() {
        return ManaExecutionSummaryKind.REFUND_FAILED;
    }

    @Override
    public int mutationCount() {
        return 1;
    }

    private static boolean isClosedRefundFailure(ManaRejectReason reason) {
        return reason == ManaRejectReason.MANA_STATE_UNAVAILABLE
                || reason == ManaRejectReason.BALANCE_LIMIT_EXCEEDED
                || reason == ManaRejectReason.INVALID_TRANSACTION_STATE;
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}

record ProvisionalEffectFailure(EffectFailureReason reason, int stepIndex) {
    ProvisionalEffectFailure {
        Objects.requireNonNull(reason, "reason");
        if ((reason != EffectFailureReason.PRIMARY_STEP_NOT_APPLIED
                        && reason != EffectFailureReason.EXECUTION_CANCELLED
                        && reason != EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED)
                || stepIndex != 0) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
        }
    }
}

record ActionDamageTransactionResult(
        EffectExecutionResult effectResult,
        ManaExecutionSummary manaSummary,
        int manaMutationCount,
        Optional<ProvisionalEffectFailure> provisionalFailure) {
    ActionDamageTransactionResult {
        Objects.requireNonNull(effectResult, "effectResult");
        Objects.requireNonNull(manaSummary, "manaSummary");
        Objects.requireNonNull(provisionalFailure, "provisionalFailure");
        if (manaMutationCount != manaSummary.mutationCount()
                || manaMutationCount < 0
                || manaMutationCount > P6EffectBounds.MAX_MANA_MUTATIONS_PER_EXECUTION) {
            throw invalidResult();
        }
        validateVariant(effectResult, manaSummary, provisionalFailure);
    }

    private static void validateVariant(
            EffectExecutionResult effect,
            ManaExecutionSummary summary,
            Optional<ProvisionalEffectFailure> provisionalFailure) {
        int debitStages = countStage(effect, EffectTraceStage.MANA_DEBITED);
        int refundAppliedStages = countStage(effect, EffectTraceStage.REFUND_APPLIED);
        int refundFailedStages = countStage(effect, EffectTraceStage.REFUND_FAILED);
        if (summary instanceof ManaNotRequired) {
            if (effect.status() == EffectTerminalStatus.COMPENSATED
                    || effect.status() == EffectTerminalStatus.COMPENSATION_FAILED
                    || debitStages != 0
                    || refundAppliedStages != 0
                    || refundFailedStages != 0
                    || provisionalFailure.isPresent()) {
                throw invalidResult();
            }
            return;
        }
        if (summary instanceof ManaDebitRejected rejected) {
            if (effect.status() != EffectTerminalStatus.REJECTED
                    || !effect.rejectReason().equals(Optional.of(
                            effectRejectReason(rejected.rejectReason())))
                    || debitStages != 0
                    || refundAppliedStages != 0
                    || refundFailedStages != 0
                    || provisionalFailure.isPresent()) {
                throw invalidResult();
            }
            return;
        }
        if (summary instanceof ManaDebited) {
            if ((effect.status() != EffectTerminalStatus.SUCCEEDED
                            && effect.status() != EffectTerminalStatus.PARTIALLY_SUCCEEDED)
                    || debitStages != 1
                    || refundAppliedStages != 0
                    || refundFailedStages != 0
                    || provisionalFailure.isPresent()) {
                throw invalidResult();
            }
            return;
        }
        ProvisionalEffectFailure provisional = provisionalFailure.orElseThrow(
                ActionDamageTransactionResult::invalidResult);
        if (summary instanceof ManaRefunded) {
            if (effect.status() != EffectTerminalStatus.COMPENSATED
                    || !effect.failureReason().equals(Optional.of(provisional.reason()))
                    || effect.failureStepIndex() != provisional.stepIndex()
                    || !matchesProvisionalExecution(effect, provisional)
                    || debitStages != 1
                    || refundAppliedStages != 1
                    || refundFailedStages != 0) {
                throw invalidResult();
            }
            return;
        }
        if (!(summary instanceof ManaRefundFailed)
                || effect.status() != EffectTerminalStatus.COMPENSATION_FAILED
                || !effect.failureReason().equals(Optional.of(
                        EffectFailureReason.COMPENSATION_REFUND_FAILED))
                || !matchesProvisionalExecution(effect, provisional)
                || debitStages != 1
                || refundAppliedStages != 0
                || refundFailedStages != 1) {
            throw invalidResult();
        }
    }

    private static boolean matchesProvisionalExecution(
            EffectExecutionResult effect, ProvisionalEffectFailure provisional) {
        return switch (provisional.reason()) {
            case PRIMARY_STEP_NOT_APPLIED -> effect.executedStepCount() == 1
                    && effect.trace().entries().stream().anyMatch(entry ->
                            entry.stage() == EffectTraceStage.STEP_NOT_APPLIED
                                    && entry.stepIndex() == provisional.stepIndex());
            case EXECUTION_CANCELLED, EXECUTION_DEADLINE_EXCEEDED ->
                    effect.executedStepCount() == 0
                            && effect.trace().entries().stream()
                                    .noneMatch(entry -> entry.stage().requiresStepIndex());
            case PRIMARY_STEP_APPLIED_WITH_FAILURE, COMPENSATION_REFUND_FAILED -> false;
        };
    }

    private static int countStage(
            EffectExecutionResult result, EffectTraceStage expected) {
        int count = 0;
        for (EffectTraceEntry entry : result.trace().entries()) {
            if (entry.stage() == expected) {
                count++;
            }
        }
        return count;
    }

    private static EffectRejectReason effectRejectReason(ManaRejectReason reason) {
        return switch (reason) {
            case INSUFFICIENT_MANA -> EffectRejectReason.INSUFFICIENT_MANA;
            case MANA_STATE_UNAVAILABLE -> EffectRejectReason.MANA_STATE_UNAVAILABLE;
            case BALANCE_LIMIT_EXCEEDED,
                    INVALID_AMOUNT,
                    WRONG_THREAD,
                    ALREADY_REFUNDED,
                    RECEIPT_ACCOUNT_MISMATCH,
                    INVALID_TRANSACTION_STATE -> throw invalidResult();
        };
    }

    private static P6ExecutionInvariantException invalidResult() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
    }
}
