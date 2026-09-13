package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;

sealed interface EffectStep permits DamageEffectStep, SpawnProjectileStep {
    int index();

    EffectStepKind kind();

    int declaredPrimaryMutationUpperBound();

    int declaredChildIntentUpperBound();
}

enum EffectStepKind {
    DAMAGE,
    SPAWN_PROJECTILE
}

record SpawnProjectileStep(
        int index,
        net.minecraft.resources.ResourceLocation dimension,
        double originX,
        double originY,
        double originZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15,
        int profileCode,
        int declaredPrimaryMutationUpperBound,
        int declaredChildIntentUpperBound) implements EffectStep {
    SpawnProjectileStep {
        Objects.requireNonNull(dimension, "dimension");
        if (index < 0 || index >= P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN) {
            throw new IllegalArgumentException("step index is outside the P6 V0 bound");
        }
        if (!validOrigin(originX, originY, originZ)) {
            throw new IllegalArgumentException("origin is outside the P9 static domain");
        }
        if (!validDirection(directionXQ15, directionYQ15, directionZQ15)) {
            throw new IllegalArgumentException("direction is not a legal Q15 tuple");
        }
        if (profileCode != 0) {
            throw new IllegalArgumentException("profile code must be zero");
        }
        if (declaredPrimaryMutationUpperBound != 1) {
            throw new IllegalArgumentException("spawn primary mutation bound must be one");
        }
        if (declaredChildIntentUpperBound != 0) {
            throw new IllegalArgumentException("spawn child-intent bound must be zero");
        }
    }

    @Override
    public EffectStepKind kind() {
        return EffectStepKind.SPAWN_PROJECTILE;
    }

    private static boolean validOrigin(double x, double y, double z) {
        return Double.isFinite(x)
                && Double.isFinite(y)
                && Double.isFinite(z)
                && x >= -30_000_000.0
                && x < 30_000_000.0
                && y >= -20_000_000.0
                && y < 20_000_000.0
                && z >= -30_000_000.0
                && z < 30_000_000.0;
    }

    private static boolean validDirection(int x, int y, int z) {
        return x >= -32_767
                && x <= 32_767
                && y >= -32_767
                && y <= 32_767
                && z >= -32_767
                && z <= 32_767
                && (x != 0 || y != 0 || z != 0);
    }
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
