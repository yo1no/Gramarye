package com.yo1no.gramarye.magic.runtime.effect;

import java.util.Objects;
import java.util.UUID;

interface EffectRequest {
    EffectRequestId requestId();

    SourceEventId sourceEventId();

    DamageTargetReference target();

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
    }
}

enum CompensationPolicy {
    REFUND_IF_NO_PRIMARY_MUTATION
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
