package com.yo1no.gramarye;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Definition-only route into the existing sole permanent-player Attachment register. */
public final class P11CastCooldownDefinitionBridge {
    private P11CastCooldownDefinitionBridge() { }
    public static ResourceLocation attachmentId() { return P11CastCooldownAttachments.ID; }
    public static AttachmentType<?> attachmentType() { return P11CastCooldownAttachments.TYPE; }
}
