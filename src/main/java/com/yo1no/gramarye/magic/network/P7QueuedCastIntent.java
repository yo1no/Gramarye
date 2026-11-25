package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7QueuedCastIntent {
    private final P7SessionIdentity sessionIdentity;
    private final P7CastOperation operation;

    P7QueuedCastIntent(
            P7SessionIdentity sessionIdentity, CastIntent intent) {
        this(sessionIdentity, new P7CastOperation.Legacy(intent));
    }

    P7QueuedCastIntent(P7SessionIdentity sessionIdentity, P7CastOperation operation) {
        this.sessionIdentity = Objects.requireNonNull(sessionIdentity, "sessionIdentity");
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    P7SessionIdentity sessionIdentity() {
        return sessionIdentity;
    }

    CastIntent intent() {
        if (operation instanceof P7CastOperation.Legacy legacy) { return legacy.intent(); }
        throw new P7SemanticInvariantException("normal operation has no legacy slot intent");
    }

    P7CastOperation operation() { return operation; }

    long sequence() { return operation.sequence(); }
}
