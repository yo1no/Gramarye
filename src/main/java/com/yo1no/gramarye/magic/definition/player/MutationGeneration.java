package com.yo1no.gramarye.magic.definition.player;

import java.util.OptionalInt;

/** Sole P4-C production owner of mutation-generation successor arithmetic. */
final class MutationGeneration {
    private MutationGeneration() {
    }

    static OptionalInt successor(int current) {
        if (current < 0) {
            throw new IllegalArgumentException("current must be non-negative");
        }
        return current == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(current + 1);
    }
}
