package com.yo1no.gramarye.magic.runtime.effect;

interface DamageEffectCommitPort {
    boolean isAvailable();

    EffectStepOutcome commitDamage(DamageEffectStep step);
}
