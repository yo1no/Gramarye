package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0NativeOutcome;
import java.util.Objects;

final class V0NativeCommitAdapter implements V0EffectCommitPort {
    private final P6RuntimeExecutionCapability.V0Call call;

    V0NativeCommitAdapter(P6RuntimeExecutionCapability.V0Call call) {
        this.call = Objects.requireNonNull(call);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public V0PrimaryOutcome commitSpawn(SpawnProjectileRequest request, SpawnProjectileStep step) {
        return outcome(call.commitSpawn(new SpawnCommit(
                request.requestId().value(), request.sourceEventId().value(), step.dimension(),
                step.originX(), step.originY(), step.originZ(), step.directionXQ15(),
                step.directionYQ15(), step.directionZQ15(), step.profileCode(), request.manaCost())));
    }

    @Override
    public V0PrimaryOutcome commitDamage(DamageEffectRequest request, DamageEffectStep step) {
        return outcome(call.commitDamage(new DamageCommit(
                request.requestId().value(), request.sourceEventId().value(),
                step.target().value(), step.magnitude(), request.manaCost())));
    }

    private static V0PrimaryOutcome outcome(V0NativeOutcome outcome) {
        if (outcome == null) {
            throw new P6ExecutionInvariantException(P6ExecutionInvariantCode.PORT_RETURNED_NULL);
        }
        return switch (outcome) {
            case CONFIRMED_NOT_APPLIED -> new V0Confirmed(EffectStepOutcome.notApplied());
            case CONFIRMED_APPLIED -> new V0Confirmed(EffectStepOutcome.applied(1));
            case APPLIED_WITH_FAILURE -> new V0Confirmed(EffectStepOutcome.appliedWithFailure(1));
            case OUTCOME_UNKNOWN -> V0NativeUnknown.INSTANCE;
        };
    }
}
