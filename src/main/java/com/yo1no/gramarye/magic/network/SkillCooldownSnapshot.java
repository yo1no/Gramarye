package com.yo1no.gramarye.magic.network;

import java.util.List;

final class SkillCooldownSnapshot {
    private final long syncSequence;
    private final List<CooldownSnapshotEntry> entries;

    SkillCooldownSnapshot(long syncSequence, List<CooldownSnapshotEntry> entries) {
        if (syncSequence <= 0) {
            throw new P7SemanticInvariantException("cooldown sync sequence is invalid");
        }
        if (entries == null
                || entries.size() > P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET) {
            throw new P7SemanticInvariantException("cooldown entry count is invalid");
        }
        var copy = List.copyOf(entries);
        var previousSlot = -1;
        for (var entry : copy) {
            if (entry == null || entry.slot() <= previousSlot) {
                throw new P7SemanticInvariantException(
                        "cooldown entries are not strictly ordered");
            }
            previousSlot = entry.slot();
        }
        this.syncSequence = syncSequence;
        this.entries = copy;
    }

    long syncSequence() {
        return syncSequence;
    }

    List<CooldownSnapshotEntry> entries() {
        return entries;
    }

    int encodedBodySize() {
        var size = Long.BYTES + 1;
        for (var entry : entries) {
            size += 1 + positiveVarIntSize(entry.remainingTicks());
        }
        return size;
    }

    private static int positiveVarIntSize(int value) {
        if (value <= 0x7f) {
            return 1;
        }
        if (value <= 0x3fff) {
            return 2;
        }
        if (value <= 0x1f_ffff) {
            return 3;
        }
        if (value <= 0x0fff_ffff) {
            return 4;
        }
        return 5;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof SkillCooldownSnapshot that
                        && syncSequence == that.syncSequence
                        && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(syncSequence) + entries.hashCode();
    }
}
