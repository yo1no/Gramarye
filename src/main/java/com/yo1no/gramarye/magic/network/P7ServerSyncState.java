package com.yo1no.gramarye.magic.network;

import java.util.Objects;

/** One bounded full-sync cycle; a known submitted family is never replayed by reconciliation. */
record P7ServerSyncState(
        P7SyncSequence mana, P7SyncSequence cooldown, long lastResyncTick,
        boolean initialPending, boolean initialManaSubmitted, boolean manaSubmittedInCycle,
        boolean sending) {
    P7ServerSyncState(P7SyncSequence mana, P7SyncSequence cooldown, long lastResyncTick,
            boolean initialPending) {
        this(mana, cooldown, lastResyncTick, initialPending, false, false, false);
    }

    P7ServerSyncState {
        Objects.requireNonNull(mana, "mana");
        Objects.requireNonNull(cooldown, "cooldown");
        if (lastResyncTick < 0 || (initialPending && initialManaSubmitted && !manaSubmittedInCycle)) {
            throw new P7SemanticInvariantException("invalid resync state");
        }
    }

    static P7ServerSyncState initial(long tick) {
        return new P7ServerSyncState(P7SyncSequence.initial(), P7SyncSequence.initial(), tick, true);
    }

    boolean due(long tick) {
        if (tick < lastResyncTick) {
            throw new P7SemanticInvariantException("resync tick regressed");
        }
        return initialPending || manaSubmittedInCycle
                || tick - lastResyncTick >= P7NetworkBounds.MIN_RESYNC_INTERVAL_TICKS;
    }

    P7ServerSyncState sending(boolean next) {
        return new P7ServerSyncState(mana, cooldown, lastResyncTick, initialPending,
                initialManaSubmitted, manaSubmittedInCycle, next);
    }

    P7ServerSyncState manaSubmitted() {
        if (manaSubmittedInCycle) { throw new P7SemanticInvariantException("mana family was already submitted"); }
        return new P7ServerSyncState(mana.submitted(), cooldown, lastResyncTick, initialPending,
                initialPending || initialManaSubmitted, true, sending);
    }

    P7ServerSyncState cooldownSubmitted(long tick) {
        if (!manaSubmittedInCycle) { throw new P7SemanticInvariantException("cooldown preceded mana"); }
        return new P7ServerSyncState(mana, cooldown.submitted(), tick, false, initialManaSubmitted, false, sending);
    }
}
