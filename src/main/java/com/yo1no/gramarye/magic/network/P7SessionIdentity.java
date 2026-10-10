package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;

/** Immutable scalar identity for one authenticated P7 connection session. */
final class P7SessionIdentity {
    private final UUID authenticatedPlayerId;
    private final long connectionEpoch;
    private final long serverGeneration;

    P7SessionIdentity(UUID authenticatedPlayerId, long connectionEpoch, long serverGeneration) {
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
        if (serverGeneration <= 0) {
            throw new P7SemanticInvariantException("server generation is outside the positive range");
        }
        this.serverGeneration = serverGeneration;
    }

    UUID authenticatedPlayerId() {
        return authenticatedPlayerId;
    }

    long connectionEpoch() {
        return connectionEpoch;
    }

    long serverGeneration() {
        return serverGeneration;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof P7SessionIdentity that
                        && connectionEpoch == that.connectionEpoch
                        && serverGeneration == that.serverGeneration
                        && authenticatedPlayerId.equals(that.authenticatedPlayerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authenticatedPlayerId, connectionEpoch, serverGeneration);
    }
}
