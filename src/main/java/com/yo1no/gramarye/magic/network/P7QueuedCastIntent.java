package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7QueuedCastIntent {
    private final P7SessionIdentity sessionIdentity;
    private final CastIntent intent;

    P7QueuedCastIntent(
            P7SessionIdentity sessionIdentity, CastIntent intent) {
        this.sessionIdentity = Objects.requireNonNull(sessionIdentity, "sessionIdentity");
        this.intent = Objects.requireNonNull(intent, "intent");
    }

    P7SessionIdentity sessionIdentity() {
        return sessionIdentity;
    }

    CastIntent intent() {
        return intent;
    }
}
