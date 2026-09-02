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
        CompensationPolicy compensationPolicy) {
    DamageActionInvocation {
        Objects.requireNonNull(actionRegistryKey, "actionRegistryKey");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sourceEventId, "sourceEventId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(compensationPolicy, "compensationPolicy");
    }
}
