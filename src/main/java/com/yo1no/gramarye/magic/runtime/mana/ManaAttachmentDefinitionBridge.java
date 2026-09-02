package com.yo1no.gramarye.magic.runtime.mana;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Definition-only bridge to the sole player Attachment registration owner. */
public final class ManaAttachmentDefinitionBridge {
    private ManaAttachmentDefinitionBridge() {}

    public static ResourceLocation attachmentId() {
        return ManaAttachments.id();
    }

    public static AttachmentType<?> attachmentType() {
        return ManaAttachments.type();
    }
}
