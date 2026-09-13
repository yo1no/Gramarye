package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable, call-scoped input for the P9 spawn-projectile action. */
record SpawnProjectileActionInvocation(
        ResourceLocation actionRegistryKey,
        EffectRequestId requestId,
        SourceEventId sourceEventId,
        ResourceLocation dimension,
        double originX,
        double originY,
        double originZ,
        int directionXQ15,
        int directionYQ15,
        int directionZQ15,
        int profileCode,
        long manaCost,
        CompensationPolicy compensationPolicy) implements ActionInvocation {
    SpawnProjectileActionInvocation {
        Objects.requireNonNull(actionRegistryKey, "actionRegistryKey");
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
