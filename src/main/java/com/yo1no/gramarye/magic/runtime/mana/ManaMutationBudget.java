package com.yo1no.gramarye.magic.runtime.mana;

/** Call-scoped counter for the exact P6 mana-mutation ceiling. */
final class ManaMutationBudget {
    private int consumed;

    boolean tryConsume() {
        if (consumed >= P6ManaBounds.MAX_MANA_MUTATIONS_PER_EFFECT_EXECUTION) {
            return false;
        }
        consumed = Math.incrementExact(consumed);
        return true;
    }

    int consumed() {
        return consumed;
    }

    int remaining() {
        return P6ManaBounds.MAX_MANA_MUTATIONS_PER_EFFECT_EXECUTION - consumed;
    }
}
