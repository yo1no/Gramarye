package com.yo1no.gramarye.magic.runtime.mana;

interface DamageEffectCommitPort {
    boolean isAvailable();

    EffectStepOutcome commitDamage(DamageEffectStep step);
}
