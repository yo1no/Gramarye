package com.yo1no.gramarye;

import java.util.Optional;

/** The ordinary distribution has no engineering assembly. */
final class V0Distribution {
    private V0Distribution() { }

    static Optional<V0SlotBinding> open(V0SlotStartup startup) {
        return Optional.empty();
    }
}
