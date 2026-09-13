package com.yo1no.gramarye.magic.runtime.mana;

interface EffectCommitPort {
    boolean isAvailable();

    EffectStepOutcome commitSpawn(
            SpawnProjectileRequest request,
            SpawnProjectileStep step);

    EffectStepOutcome commitDamage(
            DamageEffectRequest request,
            DamageEffectStep step);
}
