package com.yo1no.gramarye.magic.runtime.effect;

final class P6EffectBounds {
    static final int MAX_EFFECT_REQUESTS_PER_EXECUTION = 1;
    static final int MAX_TARGETS_PER_REQUEST = 1;
    static final int MAX_COMMIT_STEPS_PER_PLAN = 8;
    static final int MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION = 8;
    static final int MAX_MANA_MUTATIONS_PER_EXECUTION = 2;
    static final int MAX_TRACE_ENTRIES = 32;
    static final long MAX_EFFECT_MAGNITUDE = 1_000_000L;
    static final long MAX_MANA_VALUE = 1_000_000_000L;
    static final long MAX_MANA_OPERATION_AMOUNT = 1_000_000_000L;
    static final int MAX_DEADLINE_CHECKS_PER_EXECUTION = 10;
    static final int MAX_CHILD_INTENTS_PER_EXECUTION = 32;

    private P6EffectBounds() {}
}
