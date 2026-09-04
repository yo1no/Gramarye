package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;

final class P7QueuedCastIntent {
    private final UUID authenticatedPlayerId;
    private final long connectionEpoch;
    private final CastIntent intent;

    P7QueuedCastIntent(
            UUID authenticatedPlayerId, long connectionEpoch, CastIntent intent) {
        this.authenticatedPlayerId = Objects.requireNonNull(
                authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        this.connectionEpoch = connectionEpoch;
        this.intent = Objects.requireNonNull(intent, "intent");
    }

    UUID authenticatedPlayerId() {
        return authenticatedPlayerId;
    }

    long connectionEpoch() {
        return connectionEpoch;
    }

    CastIntent intent() {
        return intent;
    }
}
