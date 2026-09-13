package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.UUID;

sealed interface EffectRequest permits SpawnProjectileRequest, DamageEffectRequest {
    EffectRequestId requestId();

    SourceEventId sourceEventId();

    long manaCost();

    CompensationPolicy compensationPolicy();
}

record EffectRequestId(long value) {
    EffectRequestId {
        if (value <= 0) {
            throw new IllegalArgumentException("request identity must be positive");
        }
    }
}

record SourceEventId(long value) {
    SourceEventId {
        if (value <= 0) {
            throw new IllegalArgumentException("source-event identity must be positive");
        }
    }
}

record DamageTargetReference(UUID value) {
    DamageTargetReference {
        Objects.requireNonNull(value, "value");
        if (value.getMostSignificantBits() == 0L
                && value.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException("target identity must be nonzero");
        }
    }
}

enum CompensationPolicy {
    REFUND_IF_NO_PRIMARY_MUTATION
}

record SpawnProjectileRequest(
        EffectRequestId requestId,
        SourceEventId sourceEventId,
        net.minecraft.resources.ResourceLocation dimension,
        double originX,
        double originY,
        double originZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15,
        int profileCode,
        long manaCost,
        CompensationPolicy compensationPolicy) implements EffectRequest {
    SpawnProjectileRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(compensationPolicy, "compensationPolicy");
        if (!validOrigin(originX, originY, originZ)) {
            throw new IllegalArgumentException("origin is outside the P9 static domain");
        }
        if (!validDirection(directionXQ15, directionYQ15, directionZQ15)) {
            throw new IllegalArgumentException("direction is not a legal Q15 tuple");
        }
        if (profileCode != 0) {
            throw new IllegalArgumentException("profile code must be zero");
        }
        if (manaCost < 0 || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw new IllegalArgumentException("mana cost is outside the P6 V0 bound");
        }
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

record DamageEffectRequest(
        EffectRequestId requestId,
        SourceEventId sourceEventId,
        DamageTargetReference target,
        long magnitude,
        long manaCost,
        CompensationPolicy compensationPolicy) implements EffectRequest {
    DamageEffectRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(compensationPolicy, "compensationPolicy");
        if (magnitude <= 0 || magnitude > P6EffectBounds.MAX_EFFECT_MAGNITUDE) {
            throw new IllegalArgumentException("magnitude is outside the P6 V0 bound");
        }
        if (manaCost < 0 || manaCost > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            throw new IllegalArgumentException("mana cost is outside the P6 V0 bound");
        }
    }
}
