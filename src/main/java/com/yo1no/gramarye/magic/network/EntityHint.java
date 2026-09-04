package com.yo1no.gramarye.magic.network;

final class EntityHint {
    private final int networkId;

    EntityHint(int networkId) {
        if (!valueValid(networkId)) {
            throw new P7SemanticInvariantException("unvalidated entity hint");
        }
        this.networkId = networkId;
    }

    static boolean valueValid(int networkId) {
        return networkId >= P7NetworkBounds.ENTITY_HINT_MIN;
    }

    int networkId() {
        return networkId;
    }

    int positiveVarIntEncodedSize() {
        if (networkId <= 0x7f) {
            return 1;
        }
        if (networkId <= 0x3fff) {
            return 2;
        }
        if (networkId <= 0x1f_ffff) {
            return 3;
        }
        if (networkId <= 0x0fff_ffff) {
            return 4;
        }
        return 5;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof EntityHint that && networkId == that.networkId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(networkId);
    }

    @Override
    public String toString() {
        return "EntityHint[networkId=" + networkId + ']';
    }
}
