package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.Gramarye;
import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Sole registration owner for the permanent player-skill Attachment. */
final class PlayerSkillAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Gramarye.MOD_ID);

    private static final DeferredHolder<
                    AttachmentType<?>, AttachmentType<PlayerSkillAttachmentState>>
            PLAYER_SKILLS = ATTACHMENT_TYPES.register(
                    "player_skills",
                    () -> AttachmentType.<PlayerSkillAttachmentState>builder(
                                    PlayerSkillAttachmentPersistenceBridge::freshEmptyReady)
                            .serialize(PlayerSkillAttachmentSerializer.INSTANCE)
                            .copyOnDeath()
                            .build());

    private PlayerSkillAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(Objects.requireNonNull(modBus, "modBus"));
    }

    static AttachmentType<PlayerSkillAttachmentState> type() {
        return PLAYER_SKILLS.get();
    }
}
