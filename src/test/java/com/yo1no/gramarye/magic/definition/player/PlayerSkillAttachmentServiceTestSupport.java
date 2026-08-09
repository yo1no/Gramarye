package com.yo1no.gramarye.magic.definition.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** Test-output-only access to package-private P4-C construction and serializer seams. */
public final class PlayerSkillAttachmentServiceTestSupport {
    private PlayerSkillAttachmentServiceTestSupport() {
    }

    public static PlayerSkillAttachmentService createService() {
        return new PlayerSkillAttachmentService();
    }

    public static SerializerClassification classifyWithRegisteredSerializer(
            Tag input, HolderLookup.Provider provider) {
        return switch (PlayerSkillAttachmentSerializer.INSTANCE.read(null, input, provider)) {
            case PlayerSkillAttachmentReady ignored -> SerializerClassification.ADMITTED;
            case PlayerSkillAttachmentPreservedRaw ignored -> SerializerClassification.REJECTED;
            case PlayerSkillAttachmentOversizeMarker ignored ->
                    SerializerClassification.OVERSIZE;
        };
    }

    public enum SerializerClassification {
        ADMITTED,
        REJECTED,
        OVERSIZE
    }
}
