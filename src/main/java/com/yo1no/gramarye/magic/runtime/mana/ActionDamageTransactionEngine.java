package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageInvocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.Invocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnProjectileInvocation;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Reason;
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
            ActionInvocation input,
            ManaAccountAccess account,
            int suppliedChildIntentCapacity,
            EffectExecutionGuard guard,
            EffectCommitPort commitPort) {
        return ((V0KnownTransaction) executeTransaction(input, account,
                suppliedChildIntentCapacity, guard, commitPort, null, null, null)).result();
    }

    V0TransactionOutcome executeV0(
            ActionInvocation input,
            V0ManaAccountAccess account,
            EffectExecutionGuard guard,
            V0EffectResolver v0Resolver,
            V0EffectCommitPort commitPort,
            V0TransactionGuard transactionGuard) {
        return executeTransaction(input, account, 0, guard, null, v0Resolver, commitPort,
                transactionGuard);
    }

    private V0TransactionOutcome executeTransaction(
            ActionInvocation input,
            ManaAccountAccess account,
            int suppliedChildIntentCapacity,
            EffectExecutionGuard guard,
            EffectCommitPort legacyPort,
            V0EffectResolver v0Resolver,
            V0EffectCommitPort v0Port,
            V0TransactionGuard transactionGuard) {
        if (input == null) {
            return known(withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.INVALID_REQUEST)));
        }

        Optional<ActionExecutor> executor = executors.find(input.actionRegistryKey());
        if (executor.isEmpty()) {
            return known(withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.UNSUPPORTED_ACTION)));
        }

        ActionExecutorOutcome executorOutcome = executor.orElseThrow().execute(input);
        if (executorOutcome == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.ACTION_EXECUTOR_RETURNED_NULL);
        }
        if (executorOutcome instanceof NoActionRequest) {
            return known(withoutMana(effects.rejectBeforePreparation(
                    EffectRejectReason.INVALID_REQUEST)));
        }
        if (!(executorOutcome instanceof ProducedActionRequest produced)
                || !matchesInput(produced.request(), input)) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ACTION_EXECUTOR_OUTCOME);
        }

        EffectRequest request = produced.request();
        PreparedEffectExecution legacy = null;
        PreparedV0EffectExecution v0 = null;
        if (transactionGuard == null) {
            EffectExecutionPreparation preparation = effects.prepare(request,
                    suppliedChildIntentCapacity, resolver, guard, legacyPort);
            if (preparation instanceof TerminalEffectExecution terminal) {
                return known(withoutMana(terminal.result()));
            }
            legacy = (PreparedEffectExecution) preparation;
        } else {
            V0EffectPreparation preparation = effects.prepareV0(request, v0Resolver, guard, v0Port);
            if (preparation instanceof V0EffectPreparationRejected rejected) {
                return known(withoutMana(rejected.result()));
            }
            v0 = (PreparedV0EffectExecution) preparation;
            EffectRejectReason rejection = openV0(
                    input, (V0ManaAccountAccess) account, transactionGuard);
            if (rejection != null) {
                return known(withoutMana(effects.rejectBeforePreparation(rejection)));
            }
        }
        ManaMutationBudget budget = new ManaMutationBudget();

        if (request.manaCost() == 0) {
            V0EffectAttempt outcome = executePrepared(legacy, v0, ManaTraceState.NOT_DEBITED);
            if (outcome instanceof V0UnresolvedAttempt unresolved) {
                return new V0UnresolvedTransaction(unresolved, false);
            }
            return known(withoutMana(effects.finalizeWithoutMana(
                    ((V0KnownAttempt) outcome).attempt())));
        }

        ManaTransactionResult debitResult = manaTransactions.debit(
                account, ManaReason.SKILL_COST, request.manaCost());
        if (debitResult instanceof ManaTransactionResult.Rejected rejected) {
            return known(debitRejected(legacy, rejected, budget));
        }
        ManaTransactionResult.Accepted debit =
                (ManaTransactionResult.Accepted) debitResult;
        validateDebit(debit, request.manaCost());
        consumeMutation(budget);

        V0EffectAttempt outcome = executePrepared(legacy, v0, ManaTraceState.DEBITED);
        if (outcome instanceof V0UnresolvedAttempt unresolved) {
            return new V0UnresolvedTransaction(unresolved, true);
        }
        EffectExecutionAttempt attempt = ((V0KnownAttempt) outcome).attempt();
        if (attempt.primaryMutationCount() > 0) {
            EffectExecutionResult effect = effects.finalizeDebited(attempt);
            return known(new ActionDamageTransactionResult(
                    effect,
                    new ManaDebited(ManaReceiptSnapshot.from(debit.receipt())),
                    budget.consumed(),
                    Optional.empty()));
        }
        if (attempt.status() != EffectAttemptStatus.ZERO_MUTATION_FAILURE
                || request.compensationPolicy()
                        != CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ATTEMPT);
        }

        ProvisionalEffectFailure provisional = new ProvisionalEffectFailure(
                attempt.failureReason().orElseThrow(), attempt.failureStepIndex());
        ManaTransactionResult refundResult;
        if (transactionGuard != null && (!((V0ManaAccountAccess) account).debitMaterialCurrent()
                || !transactionGuard.checkCompensation(((V0ManaAccountAccess) account).exactActor))) {
            refundResult = new ManaTransactionResult.Rejected(
                    ManaOperationKind.REFUND, ManaRejectReason.INVALID_TRANSACTION_STATE);
        } else {
            refundResult = manaTransactions.refund(account, debit.receipt());
        }
        if (refundResult instanceof ManaTransactionResult.Accepted refund) {
            validateRefund(debit, refund);
            consumeMutation(budget);
            EffectExecutionResult effect = effects.finalizeCompensated(attempt);
            return known(new ActionDamageTransactionResult(
                    effect,
                    new ManaRefunded(
                            ManaReceiptSnapshot.from(debit.receipt()),
                            ManaReceiptSnapshot.from(refund.receipt())),
                    budget.consumed(),
                    Optional.of(provisional)));
        }

        ManaTransactionResult.Rejected rejected =
                (ManaTransactionResult.Rejected) refundResult;
        validateRefundRejection(rejected);
        EffectExecutionResult effect = effects.finalizeCompensationFailed(attempt);
        return known(new ActionDamageTransactionResult(
                effect,
                new ManaRefundFailed(
                        ManaReceiptSnapshot.from(debit.receipt()),
                        rejected.rejectReason()),
                budget.consumed(),
                Optional.of(provisional)));
    }

    private V0EffectAttempt executePrepared(
            PreparedEffectExecution legacy, PreparedV0EffectExecution v0, ManaTraceState state) {
        return v0 == null
                ? new V0KnownAttempt(effects.executePrepared(legacy, state))
                : effects.executePreparedV0(v0, state);
    }

    private static V0TransactionOutcome known(ActionDamageTransactionResult result) {
        return new V0KnownTransaction(result);
    }

    private static EffectRejectReason openV0(
            ActionInvocation input, V0ManaAccountAccess account, V0TransactionGuard guard) {
        boolean positiveFee = input.manaCost() > 0;
        if (positiveFee) {
            if (!guard.checkDebit(account.exactActor)) {
                return EffectRejectReason.CANCELLED;
            }
            if (account.availability() != ManaAvailability.AVAILABLE) {
                return EffectRejectReason.MANA_STATE_UNAVAILABLE;
            }
            if (account.balance() < input.manaCost()) {
                return EffectRejectReason.INSUFFICIENT_MANA;
            }
        }

        V0Reason reason = guard.begin(account.exactActor, v0Invocation(input));
        if (reason != V0Reason.NONE) {
            return switch (reason) {
                case ACTOR_OR_SOURCE_INVALID -> EffectRejectReason.CANCELLED;
                case TARGET_OR_PORT_UNAVAILABLE -> EffectRejectReason.COMMIT_PORT_UNAVAILABLE;
                case DEADLINE_EXCEEDED -> EffectRejectReason.DEADLINE_EXCEEDED;
                case INVALID_REQUEST -> EffectRejectReason.INVALID_REQUEST;
                default -> throw new P6ExecutionInvariantException(
                        P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
            };
        }
        return positiveFee && !guard.checkDebit(account.exactActor)
                ? EffectRejectReason.CANCELLED : null;
    }

    private static Invocation v0Invocation(ActionInvocation input) {
        return switch (input) {
            case SpawnProjectileActionInvocation spawn -> new SpawnProjectileInvocation(
                    spawn.actionRegistryKey(), spawn.requestId().value(), spawn.sourceEventId().value(),
                    spawn.dimension(), spawn.originX(), spawn.originY(), spawn.originZ(),
                    spawn.directionXQ15(), spawn.directionYQ15(), spawn.directionZQ15(),
                    spawn.profileCode(), spawn.manaCost());
            case DamageActionInvocation damage -> new DamageInvocation(
                    damage.actionRegistryKey(), damage.requestId().value(), damage.sourceEventId().value(),
                    damage.target().value(), damage.magnitude(), damage.manaCost());
        };
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
        EffectExecutionResult effect = prepared == null
                ? effects.rejectBeforePreparation(effectReason)
                : effects.rejectPrepared(prepared, effectReason);
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
            EffectRequest request, ActionInvocation input) {
        if (request instanceof SpawnProjectileRequest spawnRequest
                && input instanceof SpawnProjectileActionInvocation spawnInput) {
            return spawnRequest.requestId().equals(spawnInput.requestId())
                    && spawnRequest.sourceEventId().equals(spawnInput.sourceEventId())
                    && spawnRequest.dimension().equals(spawnInput.dimension())
                    && Double.doubleToLongBits(spawnRequest.originX())
                            == Double.doubleToLongBits(spawnInput.originX())
                    && Double.doubleToLongBits(spawnRequest.originY())
                            == Double.doubleToLongBits(spawnInput.originY())
                    && Double.doubleToLongBits(spawnRequest.originZ())
                            == Double.doubleToLongBits(spawnInput.originZ())
                    && spawnRequest.directionXQ15() == spawnInput.directionXQ15()
                    && spawnRequest.directionYQ15() == spawnInput.directionYQ15()
                    && spawnRequest.directionZQ15() == spawnInput.directionZQ15()
                    && spawnRequest.profileCode() == spawnInput.profileCode()
                    && spawnRequest.manaCost() == spawnInput.manaCost()
                    && spawnRequest.compensationPolicy() == spawnInput.compensationPolicy();
        }
        if (request instanceof DamageEffectRequest damageRequest
                && input instanceof DamageActionInvocation damageInput) {
            return damageRequest.requestId().equals(damageInput.requestId())
                    && damageRequest.sourceEventId().equals(damageInput.sourceEventId())
                    && damageRequest.target().equals(damageInput.target())
                    && damageRequest.magnitude() == damageInput.magnitude()
                    && damageRequest.manaCost() == damageInput.manaCost()
                    && damageRequest.compensationPolicy() == damageInput.compensationPolicy();
        }
        return false;
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
