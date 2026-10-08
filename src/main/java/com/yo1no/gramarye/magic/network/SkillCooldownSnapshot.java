package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncEntryState;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncReason;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncSourceState;
import java.util.List;
import java.util.Objects;

record SkillCooldownSnapshot(long syncSequence, long sourceEpoch, long sourceVersion,
        SyncSourceState sourceState, SyncReason sourceReason, List<CooldownSnapshotEntry> entries) {
    SkillCooldownSnapshot {
        if (syncSequence <= 0) {
            throw new P7SemanticInvariantException("cooldown sync sequence is invalid");
        }
        Objects.requireNonNull(sourceState, "sourceState");
        Objects.requireNonNull(sourceReason, "sourceReason");
        if (entries == null || entries.size() > P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET) {
            throw new P7SemanticInvariantException("cooldown entry count is invalid");
        }
        entries = List.copyOf(entries);
        boolean validSource = switch (sourceState) {
            case AVAILABLE -> sourceReason == SyncReason.NONE;
            case PARTIAL -> sourceReason == SyncReason.MALFORMED
                    || sourceReason == SyncReason.UNSUPPORTED_VERSION || sourceReason == SyncReason.BOUNDS;
            case UNAVAILABLE -> switch (sourceReason) {
                case NOT_READY, MALFORMED, UNSUPPORTED_VERSION, BOUNDS, CLOCK, PROVIDER,
                        EQUIPMENT_UNKNOWN, SOURCE_UNAVAILABLE -> true;
                default -> false;
            };
        };
        if (!validSource || sourceVersion < 0 || sourceEpoch < 0
                || (sourceEpoch == 0 && (sourceVersion != 0 || sourceState != SyncSourceState.UNAVAILABLE
                    || sourceReason != SyncReason.SOURCE_UNAVAILABLE || !entries.isEmpty()))
                || (sourceReason == SyncReason.EQUIPMENT_UNKNOWN && !entries.isEmpty())) {
            throw new P7SemanticInvariantException("cooldown source cross-fields are invalid");
        }
        int previousSlot = -1;
        for (var entry : entries) {
            if (entry.slot() <= previousSlot) {
                throw new P7SemanticInvariantException("cooldown entries are not strictly ordered");
            }
            if (sourceState == SyncSourceState.UNAVAILABLE
                    && (entry.state() != SyncEntryState.UNAVAILABLE || entry.reason() != sourceReason)) {
                throw new P7SemanticInvariantException("unavailable cooldown source has a usable entry");
            }
            previousSlot = entry.slot();
        }
    }

    int encodedBodySize() {
        int size = 27;
        for (var entry : entries) {
            size += 23;
            if (entry.state() == SyncEntryState.ACTIVE) {
                size += entry.remainingTicks() <= 127 ? 1 : 2;
            }
        }
        return size;
    }
}
