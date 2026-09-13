package com.yo1no.gramarye.magic.runtime.mana;

import net.minecraft.resources.ResourceLocation;

sealed interface ActionInvocation
        permits SpawnProjectileActionInvocation, DamageActionInvocation {
    ResourceLocation actionRegistryKey();

    EffectRequestId requestId();

    SourceEventId sourceEventId();

    long manaCost();

    CompensationPolicy compensationPolicy();
}
