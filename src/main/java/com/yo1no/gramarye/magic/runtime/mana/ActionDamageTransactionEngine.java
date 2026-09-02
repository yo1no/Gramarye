package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.Optional;

/** Sole P6-S3 owner of action projection, effect execution, and mana compensation. */
final class ActionDamageTransactionEngine {
    private final ActionExecutorRegistry executors;
    private final EffectResolver resolver;
    private final EffectExecutionEngine effects;
    private final ManaTransactionService manaTransactions;

    ActionDamageTransactionEngine(
            ActionExecutorRegistry executors,
            EffectResolver resolver,
            EffectExecutionEngine effects,
            ManaTransactionService manaTransactions) {
        this.executors = Objects.requireNonNull(executors, "executors");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.manaTransactions = Objects.requireNonNull(
                manaTransactions, "manaTransactions");
    }

    ActionDamageTransactionResult execute(
            DamageActionInvocation input,
            ManaAccountAccess account,
            int suppliedChildIntentCapacity,
            EffectExecutionGuard guard,
            DamageEffectCommitPort commitPort) {
        if (input == null) {
            return withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.INVALID_REQUEST));
        }

        Optional<ActionExecutor> executor = executors.find(input.actionRegistryKey());
        if (executor.isEmpty()) {
            return withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.UNSUPPORTED_ACTION));
        }

        ActionExecutorOutcome executorOutcome = executor.orElseThrow().execute(input);
        if (executorOutcome == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.ACTION_EXECUTOR_RETURNED_NULL);
        }
        if (executorOutcome instanceof NoActionRequest) {
            return withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.INVALID_REQUEST));
        }
        if (!(executorOutcome instanceof ProducedActionRequest produced)
                || !matchesInput(produced.request(), input)) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ACTION_EXECUTOR_OUTCOME);
        }

        DamageEffectRequest request = produced.request();
        EffectExecutionPreparation preparation = effects.prepare(
                request,
                suppliedChildIntentCapacity,
                resolver,
                guard,
                commitPort);
        if (preparation instanceof TerminalEffectExecution terminal) {
            return withoutMana(terminal.result());
        }
        PreparedEffectExecution prepared = (PreparedEffectExecution) preparation;
        ManaMutationBudget budget = new ManaMutationBudget();

        if (request.manaCost() == 0) {
            EffectExecutionAttempt attempt = effects.executePrepared(
                    prepared, ManaTraceState.NOT_DEBITED);
            return withoutMana(effects.finalizeWithoutMana(attempt));
        }

        ManaTransactionResult debitResult = manaTransactions.debit(
                account, ManaReason.SKILL_COST, request.manaCost());
        if (debitResult instanceof ManaTransactionResult.Rejected rejected) {
            return debitRejected(prepared, rejected, budget);
        }
        ManaTransactionResult.Accepted debit =
                (ManaTransactionResult.Accepted) debitResult;
        validateDebit(debit, request.manaCost());
        consumeMutation(budget);

        EffectExecutionAttempt attempt = effects.executePrepared(
                prepared, ManaTraceState.DEBITED);
        if (attempt.primaryMutationCount() > 0) {
            EffectExecutionResult effect = effects.finalizeDebited(attempt);
            return new ActionDamageTransactionResult(
                    effect,
                    new ManaDebited(ManaReceiptSnapshot.from(debit.receipt())),
                    budget.consumed(),
                    Optional.empty());
        }
        if (attempt.status() != EffectAttemptStatus.ZERO_MUTATION_FAILURE
                || request.compensationPolicy()
                        != CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ATTEMPT);
        }

        ProvisionalEffectFailure provisional = new ProvisionalEffectFailure(
                attempt.failureReason().orElseThrow(), attempt.failureStepIndex());
        ManaTransactionResult refundResult = manaTransactions.refund(
                account, debit.receipt());
        if (refundResult instanceof ManaTransactionResult.Accepted refund) {
            validateRefund(debit, refund);
            consumeMutation(budget);
            EffectExecutionResult effect = effects.finalizeCompensated(attempt);
            return new ActionDamageTransactionResult(
                    effect,
                    new ManaRefunded(
                            ManaReceiptSnapshot.from(debit.receipt()),
                            ManaReceiptSnapshot.from(refund.receipt())),
                    budget.consumed(),
                    Optional.of(provisional));
        }

        ManaTransactionResult.Rejected rejected =
                (ManaTransactionResult.Rejected) refundResult;
        validateRefundRejection(rejected);
        EffectExecutionResult effect = effects.finalizeCompensationFailed(attempt);
        return new ActionDamageTransactionResult(
                effect,
                new ManaRefundFailed(
                        ManaReceiptSnapshot.from(debit.receipt()),
                        rejected.rejectReason()),
                budget.consumed(),
                Optional.of(provisional));
    }

    private ActionDamageTransactionResult debitRejected(
            PreparedEffectExecution prepared,
            ManaTransactionResult.Rejected rejected,
            ManaMutationBudget budget) {
        if (rejected.operation() != ManaOperationKind.DEBIT) {
            throw impossibleManaRejection();
        }
        EffectRejectReason effectReason = switch (rejected.rejectReason()) {
            case INSUFFICIENT_MANA -> EffectRejectReason.INSUFFICIENT_MANA;
            case MANA_STATE_UNAVAILABLE -> EffectRejectReason.MANA_STATE_UNAVAILABLE;
            case BALANCE_LIMIT_EXCEEDED,
                    INVALID_AMOUNT,
                    WRONG_THREAD,
                    ALREADY_REFUNDED,
                    RECEIPT_ACCOUNT_MISMATCH,
                    INVALID_TRANSACTION_STATE -> throw impossibleManaRejection();
        };
        if (budget.consumed() != 0) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.MANA_MUTATION_BUDGET_EXCEEDED);
        }
        EffectExecutionResult effect = effects.rejectPrepared(prepared, effectReason);
        return new ActionDamageTransactionResult(
                effect,
                new ManaDebitRejected(rejected.rejectReason()),
                0,
                Optional.empty());
    }

    private static ActionDamageTransactionResult withoutMana(
            EffectExecutionResult effect) {
        return new ActionDamageTransactionResult(
                effect, new ManaNotRequired(), 0, Optional.empty());
    }

    private static boolean matchesInput(
            DamageEffectRequest request, DamageActionInvocation input) {
        return request.requestId().equals(input.requestId())
                && request.sourceEventId().equals(input.sourceEventId())
                && request.target().equals(input.target())
                && request.magnitude() == input.magnitude()
                && request.manaCost() == input.manaCost()
                && request.compensationPolicy() == input.compensationPolicy();
    }

    private static void validateDebit(
            ManaTransactionResult.Accepted debit, long expectedAmount) {
        ManaReceipt receipt = debit.receipt();
        if (debit.operation() != ManaOperationKind.DEBIT
                || debit.reason() != ManaReason.SKILL_COST
                || debit.amount() != expectedAmount
                || receipt.operation() != ManaOperationKind.DEBIT
                || receipt.reason() != ManaReason.SKILL_COST
                || receipt.refundState() != ManaRefundState.OPEN
                || !debit.accountId().equals(receipt.accountId())
                || debit.beforeBalance() != receipt.beforeBalance()
                || debit.afterBalance() != receipt.afterBalance()) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
        }
    }

    private static void validateRefund(
            ManaTransactionResult.Accepted debit,
            ManaTransactionResult.Accepted refund) {
        if (refund.operation() != ManaOperationKind.REFUND
                || refund.reason() != ManaReason.COMPENSATION_REFUND
                || !refund.accountId().equals(debit.accountId())
                || refund.amount() != debit.amount()
                || refund.beforeBalance() != debit.afterBalance()
                || refund.afterBalance() != debit.beforeBalance()
                || refund.receipt().operation() != ManaOperationKind.REFUND
                || debit.receipt().refundState() != ManaRefundState.REFUNDED) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
        }
    }

    private static void validateRefundRejection(
            ManaTransactionResult.Rejected rejected) {
        if (rejected.operation() != ManaOperationKind.REFUND
                || (rejected.rejectReason() != ManaRejectReason.MANA_STATE_UNAVAILABLE
                        && rejected.rejectReason()
                                != ManaRejectReason.BALANCE_LIMIT_EXCEEDED
                        && rejected.rejectReason()
                                != ManaRejectReason.INVALID_TRANSACTION_STATE)) {
            throw impossibleManaRejection();
        }
    }

    private static void consumeMutation(ManaMutationBudget budget) {
        if (!budget.tryConsume()) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.MANA_MUTATION_BUDGET_EXCEEDED);
        }
    }

    private static P6ExecutionInvariantException impossibleManaRejection() {
        return new P6ExecutionInvariantException(
                P6ExecutionInvariantCode.IMPOSSIBLE_MANA_REJECTION);
    }
}
