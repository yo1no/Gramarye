package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.Optional;

final class CastIntent {
    private static final int FIXED_BODY_BYTES = 11;
    private static final int AIM_BODY_BYTES = 6;

    private final long sequence;
    private final int slot;
    private final CastInputKind inputKind;
    private final int presenceMask;
    private final AimHint aimHint;
    private final EntityHint entityHint;

    CastIntent(
            long sequence,
            int slot,
            CastInputKind inputKind,
            int presenceMask,
            AimHint aimHint,
            EntityHint entityHint) {
        if (slot < P7NetworkBounds.SLOT_MIN || slot > P7NetworkBounds.SLOT_MAX) {
            throw new P7SemanticInvariantException("unvalidated cast slot");
        }
        if (inputKind != CastInputKind.CAST) {
            throw new P7SemanticInvariantException("unvalidated cast kind");
        }
        if (!presenceConsistent(presenceMask, aimHint, entityHint)) {
            throw new P7SemanticInvariantException("unvalidated presence mask");
        }
        this.sequence = sequence;
        this.slot = slot;
        this.inputKind = inputKind;
        this.presenceMask = presenceMask;
        this.aimHint = aimHint;
        this.entityHint = entityHint;
    }

    static boolean presenceConsistent(
            int presenceMask, AimHint aimHint, EntityHint entityHint) {
        if ((presenceMask & ~P7NetworkBounds.ALLOWED_PRESENCE_MASK) != 0) {
            return false;
        }
        var aimFlag = (presenceMask & (1 << P7NetworkBounds.AIM_PRESENT_BIT)) != 0;
        var entityFlag =
                (presenceMask & (1 << P7NetworkBounds.ENTITY_HINT_PRESENT_BIT)) != 0;
        return aimFlag == (aimHint != null) && entityFlag == (entityHint != null);
    }

    long sequence() {
        return sequence;
    }

    boolean hasProductValidSequence() {
        return sequence >= P7NetworkBounds.NETWORK_SEQUENCE_MIN;
    }

    int slot() {
        return slot;
    }

    CastInputKind inputKind() {
        return inputKind;
    }

    int presenceMask() {
        return presenceMask;
    }

    Optional<AimHint> aimHint() {
        return Optional.ofNullable(aimHint);
    }

    Optional<EntityHint> entityHint() {
        return Optional.ofNullable(entityHint);
    }

    int encodedBodySize() {
        var size = FIXED_BODY_BYTES;
        if (aimHint != null) {
            size += AIM_BODY_BYTES;
        }
        if (entityHint != null) {
            size += entityHint.positiveVarIntEncodedSize();
        }
        return size;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CastIntent that)) {
            return false;
        }
        return sequence == that.sequence
                && slot == that.slot
                && presenceMask == that.presenceMask
                && inputKind == that.inputKind
                && Objects.equals(aimHint, that.aimHint)
                && Objects.equals(entityHint, that.entityHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, slot, inputKind, presenceMask, aimHint, entityHint);
    }
}
