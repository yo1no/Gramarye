package com.yo1no.gramarye.magic.network;

final class CooldownSnapshotEntry {
    private final int slot;
    private final int remainingTicks;

    CooldownSnapshotEntry(int slot, int remainingTicks) {
        if (slot < P7NetworkBounds.SLOT_MIN || slot > P7NetworkBounds.SLOT_MAX) {
            throw new P7SemanticInvariantException("cooldown slot is invalid");
        }
        if (remainingTicks <= 0) {
            throw new P7SemanticInvariantException("cooldown duration is invalid");
        }
        this.slot = slot;
        this.remainingTicks = remainingTicks;
    }

    int slot() {
        return slot;
    }

    int remainingTicks() {
        return remainingTicks;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof CooldownSnapshotEntry that
                        && slot == that.slot
                        && remainingTicks == that.remainingTicks;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(slot) + Integer.hashCode(remainingTicks);
    }
}
