package com.yo1no.gramarye.magic.runtime.mana;

interface V0EffectCommitPort {
    boolean isAvailable();

    V0PrimaryOutcome commitSpawn(SpawnProjectileRequest request, SpawnProjectileStep step);

    V0PrimaryOutcome commitDamage(DamageEffectRequest request, DamageEffectStep step);
}

sealed interface V0PrimaryOutcome permits V0Confirmed, V0NativeUnknown {}

record V0Confirmed(EffectStepOutcome outcome) implements V0PrimaryOutcome {}

enum V0NativeUnknown implements V0PrimaryOutcome {
    INSTANCE
}
