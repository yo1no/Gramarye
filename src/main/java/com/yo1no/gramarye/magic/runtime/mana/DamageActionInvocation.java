package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable, call-scoped input for the P6-S3 damage-action slice. */
record DamageActionInvocation(
        ResourceLocation actionRegistryKey,
        EffectRequestId requestId,
        SourceEventId sourceEventId,
        DamageTargetReference target,
        long magnitude,
        long manaCost,
        CompensationPolicy compensationPolicy) implements ActionInvocation {
    DamageActionInvocation {
        Objects.requireNonNull(actionRegistryKey, "actionRegistryKey");
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
