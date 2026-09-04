package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.OptionalLong;

/** Pure checked allocator state for process-local P7 connection epochs. */
final class ConnectionEpochState {
    private final long nextEpoch;
    private final boolean exhausted;

    private ConnectionEpochState(long nextEpoch, boolean exhausted) {
        this.nextEpoch = nextEpoch;
        this.exhausted = exhausted;
    }

    static ConnectionEpochState initial() {
        return activeAt(P7NetworkBounds.NETWORK_SEQUENCE_MIN);
    }

    static ConnectionEpochState activeAt(long nextEpoch) {
        if (nextEpoch < P7NetworkBounds.NETWORK_SEQUENCE_MIN
                || nextEpoch > P7NetworkBounds.NETWORK_SEQUENCE_MAX) {
            throw new P7SemanticInvariantException(
                    "connection epoch is outside the positive range");
        }
        return new ConnectionEpochState(nextEpoch, false);
    }

    boolean exhausted() {
        return exhausted;
    }

    OptionalLong nextEpoch() {
        return exhausted ? OptionalLong.empty() : OptionalLong.of(nextEpoch);
    }

    Allocation allocate() {
        if (exhausted) {
            return Allocation.rejected(this);
        }
        if (nextEpoch == P7NetworkBounds.NETWORK_SEQUENCE_MAX) {
            return Allocation.accepted(
                    nextEpoch,
                    new ConnectionEpochState(P7NetworkBounds.NETWORK_SEQUENCE_MAX, true));
        }
        return Allocation.accepted(nextEpoch, new ConnectionEpochState(nextEpoch + 1L, false));
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ConnectionEpochState that
                        && nextEpoch == that.nextEpoch
                        && exhausted == that.exhausted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nextEpoch, exhausted);
    }

    static final class Allocation {
        private final OptionalLong allocatedEpoch;
        private final ConnectionEpochState nextState;

        private Allocation(OptionalLong allocatedEpoch, ConnectionEpochState nextState) {
            this.allocatedEpoch = allocatedEpoch;
            this.nextState = Objects.requireNonNull(nextState, "nextState");
        }

        private static Allocation accepted(long epoch, ConnectionEpochState nextState) {
            return new Allocation(OptionalLong.of(epoch), nextState);
        }

        private static Allocation rejected(ConnectionEpochState state) {
            return new Allocation(OptionalLong.empty(), state);
        }

        boolean accepted() {
            return allocatedEpoch.isPresent();
        }

        OptionalLong allocatedEpoch() {
            return allocatedEpoch;
        }

        ConnectionEpochState nextState() {
            return nextState;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Allocation that
                            && allocatedEpoch.equals(that.allocatedEpoch)
                            && nextState.equals(that.nextState);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allocatedEpoch, nextState);
        }
    }
}
