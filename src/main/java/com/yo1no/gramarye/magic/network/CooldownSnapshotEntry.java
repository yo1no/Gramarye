package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncEntryState;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary.SyncReason;
import java.util.Objects;

record CooldownSnapshotEntry(int slot, SkillReference reference, SyncEntryState state,
        SyncReason reason, int remainingTicks) {
    CooldownSnapshotEntry {
        validate(slot, reference, state, reason, remainingTicks);
    }

    static void validate(int slot, SkillReference reference, SyncEntryState state,
            SyncReason reason, int remainingTicks) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        if (slot < P7NetworkBounds.SLOT_MIN || slot > P7NetworkBounds.SLOT_MAX
                || reference.revision().value() < 0) {
            throw new P7SemanticInvariantException("cooldown slot or revision is invalid");
        }
        boolean valid = switch (state) {
            case READY -> reason == SyncReason.NONE && remainingTicks == 0;
            case ACTIVE -> (reason == SyncReason.NONE || reason == SyncReason.SAVE_FAILED)
                    && remainingTicks >= 1 && remainingTicks <= 600;
            case PENDING -> (reason == SyncReason.PENDING_RELEASE || reason == SyncReason.SAVE_FAILED)
                    && remainingTicks == 0;
            case RECOVERY_REQUIRED -> (reason == SyncReason.OUTCOME_UNCERTAIN
                    || reason == SyncReason.RECOVERY_REQUIRED || reason == SyncReason.SAVE_FAILED)
                    && remainingTicks == 0;
            case UNAVAILABLE -> reason != SyncReason.NONE && reason != SyncReason.PENDING_RELEASE
                    && reason != SyncReason.EQUIPMENT_UNKNOWN && remainingTicks == 0;
        };
        if (!valid) {
            throw new P7SemanticInvariantException("cooldown entry cross-fields are invalid");
        }
    }
}
