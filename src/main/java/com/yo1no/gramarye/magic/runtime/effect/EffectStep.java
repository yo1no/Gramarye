package com.yo1no.gramarye.magic.runtime.effect;

import java.util.Objects;

sealed interface EffectStep permits DamageEffectStep {
    int index();

    EffectStepKind kind();

    int declaredPrimaryMutationUpperBound();

    int declaredChildIntentUpperBound();
}

enum EffectStepKind {
    DAMAGE
}

record DamageEffectStep(
        int index,
        DamageTargetReference target,
        long magnitude,
        int declaredPrimaryMutationUpperBound,
        int declaredChildIntentUpperBound) implements EffectStep {
    DamageEffectStep {
        Objects.requireNonNull(target, "target");
        if (index < 0 || index >= P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
            throw new IllegalArgumentException("step index is outside the P6 V0 bound");
        }
        if (magnitude <= 0 || magnitude > P6EffectBounds.MAX_EFFECT_MAGNITUDE) {
            throw new IllegalArgumentException("step magnitude is outside the P6 V0 bound");
        }
        if (declaredPrimaryMutationUpperBound <= 0
                || declaredPrimaryMutationUpperBound
                        > P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION) {
            throw new IllegalArgumentException("declared primary mutation bound is invalid");
        }
        if (declaredChildIntentUpperBound < 0
                || declaredChildIntentUpperBound
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION) {
            throw new IllegalArgumentException("declared child-intent bound is invalid");
        }
    }

    @Override
    public EffectStepKind kind() {
        return EffectStepKind.DAMAGE;
    }
}
