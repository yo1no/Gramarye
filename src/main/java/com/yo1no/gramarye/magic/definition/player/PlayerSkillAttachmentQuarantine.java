package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;
import net.minecraft.nbt.Tag;

/** Malformed-present Attachment states; neither variant is a missing/default state. */
sealed interface PlayerSkillAttachmentQuarantine extends PlayerSkillAttachmentState
        permits PlayerSkillAttachmentPreservedRaw, PlayerSkillAttachmentOversizeMarker {
    PlayerSkillAttachmentFailure failure();
}

final class PlayerSkillAttachmentPreservedRaw implements PlayerSkillAttachmentQuarantine {
    private final PlayerSkillAttachmentFailure failure;
    private final Tag rawSnapshot;
    private final long exactEncodedByteCount;

    PlayerSkillAttachmentPreservedRaw(
            PlayerSkillAttachmentFailure failure,
            Tag freshRawSnapshot,
            long exactEncodedByteCount) {
        this.failure = Objects.requireNonNull(failure, "failure");
        this.rawSnapshot = Objects.requireNonNull(freshRawSnapshot, "freshRawSnapshot");
        if (exactEncodedByteCount < 0 || exactEncodedByteCount > AttachmentTagSize.maximum()) {
            throw new IllegalArgumentException("exactEncodedByteCount is outside the Attachment bound");
        }
        this.exactEncodedByteCount = exactEncodedByteCount;
    }

    @Override
    public PlayerSkillAttachmentFailure failure() {
        return failure;
    }

    long exactEncodedByteCount() {
        return exactEncodedByteCount;
    }

    Tag copyRaw() {
        return rawSnapshot.copy();
    }

    @Override
    public String toString() {
        return "PlayerSkillAttachmentPreservedRaw[code=" + failure.code()
                + ", exactEncodedByteCount=" + exactEncodedByteCount + ']';
    }
}

final class PlayerSkillAttachmentOversizeMarker implements PlayerSkillAttachmentQuarantine {
    private final PlayerSkillAttachmentFailure failure;

    PlayerSkillAttachmentOversizeMarker() {
        this.failure = PlayerSkillAttachmentFailure.capacity(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENCODED_CAPACITY_EXCEEDED,
                PlayerSkillAttachmentFailure.Stage.TOTAL_COUNT,
                AttachmentTagSize.observedAtLeast(),
                AttachmentTagSize.maximum());
    }

    @Override
    public PlayerSkillAttachmentFailure failure() {
        return failure;
    }

    long observedAtLeast() {
        return failure.observed();
    }

    long maximum() {
        return failure.maximum();
    }

    @Override
    public String toString() {
        return "PlayerSkillAttachmentOversizeMarker[observedAtLeast=" + observedAtLeast()
                + ", maximum=" + maximum() + ']';
    }
}
