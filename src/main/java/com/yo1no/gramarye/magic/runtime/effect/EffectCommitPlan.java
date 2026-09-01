package com.yo1no.gramarye.magic.runtime.effect;

import java.util.List;
import java.util.Objects;

record EffectCommitPlan(List<EffectStep> steps, int suppliedChildIntentCapacity) {
    EffectCommitPlan {
        Objects.requireNonNull(steps, "steps");
        if (suppliedChildIntentCapacity < 0
                || suppliedChildIntentCapacity
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION) {
            throw new IllegalArgumentException("supplied child-intent capacity is invalid");
        }

        List<EffectStep> copied = List.copyOf(steps);
        if (copied.isEmpty()
                || copied.size() > P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
            throw new IllegalArgumentException("commit plan must contain one through eight steps");
        }

        int declaredPrimaryMutations = 0;
        int declaredChildIntents = 0;
        for (int index = 0; index < copied.size(); index++) {
            EffectStep step = copied.get(index);
            if (step.index() != index || step.declaredPrimaryMutationUpperBound() <= 0) {
                throw new IllegalArgumentException("commit plan step order or declaration is invalid");
            }
            declaredPrimaryMutations = Math.addExact(
                    declaredPrimaryMutations, step.declaredPrimaryMutationUpperBound());
            declaredChildIntents = Math.addExact(
                    declaredChildIntents, step.declaredChildIntentUpperBound());
        }
        if (declaredPrimaryMutations <= 0
                || declaredPrimaryMutations
                        > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION) {
            throw new IllegalArgumentException("declared primary mutation total is invalid");
        }
        if (declaredChildIntents > suppliedChildIntentCapacity) {
            throw new IllegalArgumentException("declared child-intent total exceeds capacity");
        }
        steps = copied;
    }

    int declaredPrimaryMutationCount() {
        int total = 0;
        for (EffectStep step : steps) {
            total = Math.addExact(total, step.declaredPrimaryMutationUpperBound());
        }
        return total;
    }

    int declaredChildIntentCount() {
        int total = 0;
        for (EffectStep step : steps) {
            total = Math.addExact(total, step.declaredChildIntentUpperBound());
        }
        return total;
    }
}
