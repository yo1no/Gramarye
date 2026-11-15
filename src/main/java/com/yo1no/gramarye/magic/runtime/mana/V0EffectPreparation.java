package com.yo1no.gramarye.magic.runtime.mana;

import java.util.List;

sealed interface V0EffectPreparation
        permits PreparedV0EffectExecution, V0EffectPreparationRejected {}

record PreparedV0EffectExecution(
        EffectRequest request,
        EffectCommitPlan plan,
        EffectExecutionGuard guard,
        V0EffectCommitPort commitPort,
        List<EffectTraceEntry> tracePrefix) implements V0EffectPreparation {
    PreparedV0EffectExecution {
        if (plan.steps().size() != 1
                || plan.declaredPrimaryMutationCount() != 1
                || plan.declaredChildIntentCount() != 0) {
            throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.INVALID_PREPARATION);
        }
        tracePrefix = List.copyOf(tracePrefix);
    }
}

record V0EffectPreparationRejected(EffectExecutionResult result) implements V0EffectPreparation {}

sealed interface V0EffectAttempt permits V0KnownAttempt, V0UnresolvedAttempt {}

record V0KnownAttempt(EffectExecutionAttempt attempt) implements V0EffectAttempt {}

record V0UnresolvedAttempt(int stepIndex, int confirmedPrefixCount) implements V0EffectAttempt {}

sealed interface V0TransactionOutcome permits V0KnownTransaction, V0UnresolvedTransaction {}

record V0KnownTransaction(ActionDamageTransactionResult result) implements V0TransactionOutcome {}

record V0UnresolvedTransaction(V0UnresolvedAttempt attempt, boolean debited)
        implements V0TransactionOutcome {}
