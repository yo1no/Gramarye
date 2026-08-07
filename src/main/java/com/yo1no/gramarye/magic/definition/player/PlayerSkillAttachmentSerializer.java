package com.yo1no.gramarye.magic.definition.player;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

/** Total serializer for any per-attachment Tag root delivered by NeoForge. */
final class PlayerSkillAttachmentSerializer
        implements IAttachmentSerializer<Tag, PlayerSkillAttachmentState> {
    static final PlayerSkillAttachmentSerializer INSTANCE =
            new PlayerSkillAttachmentSerializer();

    private final PlayerSkillAttachmentAdmission admission;

    PlayerSkillAttachmentSerializer() {
        this(new PlayerSkillAttachmentPersistenceBridge());
    }

    PlayerSkillAttachmentSerializer(PlayerSkillAttachmentPersistenceBridge persistence) {
        this.admission = new PlayerSkillAttachmentAdmission(
                Objects.requireNonNull(persistence, "persistence"));
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
        return switch (admission.admit(input, measured, Optional.ofNullable(provider))) {
            case PlayerSkillAttachmentAdmission.Admitted admitted -> admitted.ready();
            case PlayerSkillAttachmentAdmission.Rejected rejected ->
                    new PlayerSkillAttachmentPreservedRaw(
                            rejected.failure(), input.copy(), rejected.exactEncodedByteCount());
            case PlayerSkillAttachmentAdmission.Oversize ignored ->
                    new PlayerSkillAttachmentOversizeMarker();
        };
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
