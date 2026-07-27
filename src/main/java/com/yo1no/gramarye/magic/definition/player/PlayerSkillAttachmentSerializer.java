package com.yo1no.gramarye.magic.definition.player;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

/** Unregistered P4-C1 total serializer for any per-attachment Tag root delivered by NeoForge. */
final class PlayerSkillAttachmentSerializer
        implements IAttachmentSerializer<Tag, PlayerSkillAttachmentState> {
    private final PlayerSkillAttachmentPersistenceBridge persistence;

    PlayerSkillAttachmentSerializer() {
        this(new PlayerSkillAttachmentPersistenceBridge());
    }

    PlayerSkillAttachmentSerializer(PlayerSkillAttachmentPersistenceBridge persistence) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
    }

    @Override
    public PlayerSkillAttachmentState read(
            IAttachmentHolder holder,
            Tag input,
            HolderLookup.Provider provider) {
        Objects.requireNonNull(input, "input");
        final AttachmentTagSizeResult measured;
        try {
            measured = AttachmentTagSize.measure(input);
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory NBT counting failed", exception);
        }
        if (measured instanceof AttachmentTagSizeResult.Exceeded exceeded) {
            if (exceeded.observedAtLeast() != AttachmentTagSize.observedAtLeast()
                    || exceeded.maximum() != AttachmentTagSize.maximum()) {
                throw new IllegalStateException("Attachment size result violated its fixed boundary");
            }
            return new PlayerSkillAttachmentOversizeMarker();
        }
        var exactCount = ((AttachmentTagSizeResult.WithinLimit) measured).exactByteCount();
        if (PlayerSkillAttachmentMarker.isExact(input)) {
            return new PlayerSkillAttachmentOversizeMarker();
        }

        PlayerSkillAttachmentFailure failure;
        try {
            if (input instanceof CompoundTag compound) {
                var result = persistence.load(compound, Optional.ofNullable(provider));
                if (result instanceof PlayerSkillAttachmentPersistenceBridge.Loaded loaded) {
                    return loaded.ready();
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
        return new PlayerSkillAttachmentPreservedRaw(failure, input.copy(), exactCount);
    }

    @Override
    public Tag write(
            PlayerSkillAttachmentState state,
            HolderLookup.Provider provider) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case PlayerSkillAttachmentReady ready -> ready.carrier().copyTag();
            case PlayerSkillAttachmentPreservedRaw preserved -> preserved.copyRaw();
            case PlayerSkillAttachmentOversizeMarker ignored ->
                    PlayerSkillAttachmentMarker.freshTag();
        };
    }
}
