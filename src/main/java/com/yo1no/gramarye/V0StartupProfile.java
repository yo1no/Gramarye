package com.yo1no.gramarye;

import java.util.Objects;

/** Selected role is separate from the unchanged engineering raw configuration candidate. */
record V0StartupProfile(V0StartupLoadState state, Kind kind) {
    enum Kind { NORMAL, ENGINEERING }
    private static final V0StartupLimits NORMAL_LIMITS =
            new V0StartupLimits(1, 2, 1, 4, 16, 16, 24, 8, 1);

    V0StartupProfile {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(kind, "kind");
    }

    static V0StartupProfile normal() {
        return new V0StartupProfile(new V0StartupLoadState.Ready(NORMAL_LIMITS), Kind.NORMAL);
    }

    static V0StartupProfile engineering(V0StartupLoadState state) {
        return new V0StartupProfile(state, Kind.ENGINEERING);
    }
}
