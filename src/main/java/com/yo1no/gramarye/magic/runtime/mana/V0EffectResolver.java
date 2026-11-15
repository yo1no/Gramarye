package com.yo1no.gramarye.magic.runtime.mana;

import java.util.List;

final class V0EffectResolver implements EffectResolver {
    private final long permittedManaCost;
    private final long permittedDamageMagnitude;

    V0EffectResolver(long permittedManaCost, long permittedDamageMagnitude) {
        this.permittedManaCost = permittedManaCost;
        this.permittedDamageMagnitude = permittedDamageMagnitude;
    }

    @Override
    public EffectResolution resolve(EffectRequest request, int suppliedChildIntentCapacity) {
        if (suppliedChildIntentCapacity != 0) {
            return new RejectedEffectResolution(EffectRejectReason.BOUND_EXCEEDED);
        }
        if (request instanceof SpawnProjectileRequest spawn
                && spawn.profileCode() == 0
                && spawn.manaCost() == permittedManaCost
                && spawn.manaCost() >= 0 && spawn.manaCost() <= 4
                && permittedDamageMagnitude == 0) {
            return plan(new SpawnProjectileStep(0, spawn.dimension(), spawn.originX(),
                    spawn.originY(), spawn.originZ(), spawn.directionXQ15(),
                    spawn.directionYQ15(), spawn.directionZQ15(), 0, 1, 0));
        }
        if (request instanceof DamageEffectRequest damage
                && damage.magnitude() == permittedDamageMagnitude
                && damage.magnitude() >= 1_000 && damage.magnitude() <= 4_000
                && damage.magnitude() % 1_000 == 0
                && damage.manaCost() == 0 && permittedManaCost == 0) {
            return plan(new DamageEffectStep(0, damage.target(), damage.magnitude(), 1, 0));
        }
        return new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST);
    }

    private static EffectResolution plan(EffectStep step) {
        return new AcceptedEffectResolution(new EffectCommitPlan(List.of(step), 0));
    }
}
