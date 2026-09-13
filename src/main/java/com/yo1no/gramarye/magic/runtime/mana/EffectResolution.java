package com.yo1no.gramarye.magic.runtime.mana;

import java.util.List;
import java.util.Objects;

enum EffectResolutionKind {
    ACCEPTED,
    REJECTED
}

sealed interface EffectResolution
        permits AcceptedEffectResolution, RejectedEffectResolution {
    EffectResolutionKind kind();
}

record AcceptedEffectResolution(EffectCommitPlan plan) implements EffectResolution {
    AcceptedEffectResolution {
        if (plan == null) {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_ACCEPTED_PLAN);
        }
    }

    @Override
    public EffectResolutionKind kind() {
        return EffectResolutionKind.ACCEPTED;
    }
}

record RejectedEffectResolution(EffectRejectReason reason) implements EffectResolution {
    RejectedEffectResolution {
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public EffectResolutionKind kind() {
        return EffectResolutionKind.REJECTED;
    }
}

interface EffectResolver {
    EffectResolution resolve(EffectRequest request, int suppliedChildIntentCapacity);
}

final class DamageEffectResolver implements EffectResolver {
    @Override
    public EffectResolution resolve(EffectRequest request, int suppliedChildIntentCapacity) {
        if (request == null) {
            return new RejectedEffectResolution(EffectRejectReason.INVALID_REQUEST);
        }
        if (suppliedChildIntentCapacity < 0
                || suppliedChildIntentCapacity
                        > P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION) {
            return new RejectedEffectResolution(EffectRejectReason.BOUND_EXCEEDED);
        }
        EffectStep step;
        if (request instanceof SpawnProjectileRequest spawnRequest) {
            step = new SpawnProjectileStep(
                    0,
                    spawnRequest.dimension(),
                    spawnRequest.originX(),
                    spawnRequest.originY(),
                    spawnRequest.originZ(),
                    spawnRequest.directionXQ15(),
                    spawnRequest.directionYQ15(),
                    spawnRequest.directionZQ15(),
                    spawnRequest.profileCode(),
                    1,
                    0);
        } else if (request instanceof DamageEffectRequest damageRequest) {
            step = new DamageEffectStep(
                    0,
                    damageRequest.target(),
                    damageRequest.magnitude(),
                    1,
                    0);
        } else {
            throw new P6ExecutionInvariantException(
                    P6ExecutionInvariantCode.INVALID_TRANSACTION_RESULT);
        }
        return new AcceptedEffectResolution(
                new EffectCommitPlan(List.of(step), suppliedChildIntentCapacity));
    }
}
