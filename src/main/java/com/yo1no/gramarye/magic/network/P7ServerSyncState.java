package com.yo1no.gramarye.magic.network;

import java.util.Objects;

record P7ServerSyncState(
        P7SyncSequence mana, P7SyncSequence cooldown, long lastResyncTick,
        boolean initialPending) {
    P7ServerSyncState {
        Objects.requireNonNull(mana, "mana");
        Objects.requireNonNull(cooldown, "cooldown");
        if (lastResyncTick < 0) {
            throw new P7SemanticInvariantException("invalid resync tick");
        }
    }

    static P7ServerSyncState initial(long tick) {
        return new P7ServerSyncState(P7SyncSequence.initial(), P7SyncSequence.initial(), tick, true);
    }

    boolean due(long tick) {
        if (tick < lastResyncTick) {
            throw new P7SemanticInvariantException("resync tick regressed");
        }
        return initialPending || tick - lastResyncTick >= P7NetworkBounds.MIN_RESYNC_INTERVAL_TICKS;
    }

    P7ServerSyncState manaSubmitted() {
        return new P7ServerSyncState(mana.submitted(), cooldown, lastResyncTick, initialPending);
    }

    P7ServerSyncState cooldownSubmitted(long tick) {
        return new P7ServerSyncState(mana, cooldown.submitted(), tick, false);
    }
}
