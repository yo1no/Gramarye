package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Shared, no-copy semantic admission for one already measured Attachment Tag. */
final class PlayerSkillAttachmentAdmission {
    private final PlayerSkillAttachmentPersistenceBridge persistence;

    PlayerSkillAttachmentAdmission() {
        this(new PlayerSkillAttachmentPersistenceBridge());
    }

    PlayerSkillAttachmentAdmission(PlayerSkillAttachmentPersistenceBridge persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    Result admit(
            Tag input,
            AttachmentTagSizeResult measured,
            Optional<HolderLookup.Provider> provider) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(measured, "measured");
        Objects.requireNonNull(provider, "provider");

        if (measured instanceof AttachmentTagSizeResult.Exceeded exceeded) {
            requireFixedExceededBoundary(exceeded);
            return Oversize.INSTANCE;
        }

        var exactCount = ((AttachmentTagSizeResult.WithinLimit) measured).exactByteCount();
        if (exactCount > AttachmentTagSize.maximum()) {
            throw new IllegalStateException("Within-limit Attachment size exceeded its fixed boundary");
        }
        if (PlayerSkillAttachmentMarker.isExact(input)) {
            return Oversize.INSTANCE;
        }

        PlayerSkillAttachmentFailure failure;
        try {
            if (input instanceof CompoundTag compound) {
                var result = persistence.load(compound, provider);
                if (result instanceof PlayerSkillAttachmentPersistenceBridge.Loaded loaded) {
                    return new Admitted(loaded.ready(), exactCount);
                }
                failure = ((PlayerSkillAttachmentPersistenceBridge.Rejected) result).failure();
            } else {
                failure = PlayerSkillAttachmentFailure.simple(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                        PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA);
            }
        } catch (RuntimeException exception) {
            failure = PlayerSkillAttachmentFailure.exception(
                    PlayerSkillAttachmentFailure.Code.INTERNAL_CODEC_EXCEPTION,
                    PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA,
                    exception);
        }
        return new Rejected(failure, exactCount);
    }

    private static void requireFixedExceededBoundary(AttachmentTagSizeResult.Exceeded exceeded) {
        if (exceeded.observedAtLeast() != AttachmentTagSize.observedAtLeast()
                || exceeded.maximum() != AttachmentTagSize.maximum()) {
            throw new IllegalStateException("Attachment size result violated its fixed boundary");
        }
    }

    sealed interface Result permits Admitted, Rejected, Oversize {
    }

    record Admitted(PlayerSkillAttachmentReady ready, long exactEncodedByteCount)
            implements Result {
        Admitted {
            Objects.requireNonNull(ready, "ready");
            requireWithinLimit(exactEncodedByteCount);
        }
    }

    record Rejected(PlayerSkillAttachmentFailure failure, long exactEncodedByteCount)
            implements Result {
        Rejected {
            Objects.requireNonNull(failure, "failure");
            requireWithinLimit(exactEncodedByteCount);
        }
    }

    enum Oversize implements Result {
        INSTANCE
    }

    private static void requireWithinLimit(long exactEncodedByteCount) {
        if (exactEncodedByteCount < 0 || exactEncodedByteCount > AttachmentTagSize.maximum()) {
            throw new IllegalArgumentException(
                    "exactEncodedByteCount is outside the Attachment boundary");
        }
    }
}
