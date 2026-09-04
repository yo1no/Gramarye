package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;

/** Immutable scalar identity for one authenticated P7 connection session. */
final class P7SessionIdentity {
    private final UUID authenticatedPlayerId;
    private final long connectionEpoch;

    P7SessionIdentity(UUID authenticatedPlayerId, long connectionEpoch) {
        if (authenticatedPlayerId == null) {
            throw new P7SemanticInvariantException("authenticated player ID is required");
        }
        if (connectionEpoch < P7NetworkBounds.NETWORK_SEQUENCE_MIN
                || connectionEpoch > P7NetworkBounds.NETWORK_SEQUENCE_MAX) {
            throw new P7SemanticInvariantException(
                    "connection epoch is outside the positive range");
        }
        this.authenticatedPlayerId = authenticatedPlayerId;
        this.connectionEpoch = connectionEpoch;
    }

    UUID authenticatedPlayerId() {
        return authenticatedPlayerId;
    }

    long connectionEpoch() {
        return connectionEpoch;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof P7SessionIdentity that
                        && connectionEpoch == that.connectionEpoch
                        && authenticatedPlayerId.equals(that.authenticatedPlayerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authenticatedPlayerId, connectionEpoch);
    }
}
