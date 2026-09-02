package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.runtime.mana.ManaAttachmentDefinitionBridge;
import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Sole registration owner for permanent player Attachments. */
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

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<?>> PLAYER_MANA =
            ATTACHMENT_TYPES.register(
                    ManaAttachmentDefinitionBridge.attachmentId().getPath(),
                    ManaAttachmentDefinitionBridge::attachmentType);

    private PlayerSkillAttachments() {
    }

    static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(Objects.requireNonNull(modBus, "modBus"));
    }

    static AttachmentType<PlayerSkillAttachmentState> type() {
        return PLAYER_SKILLS.get();
    }
}
