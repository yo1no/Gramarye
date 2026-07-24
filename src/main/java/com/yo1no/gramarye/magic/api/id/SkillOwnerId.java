package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/** Pure domain identity for the owner authorized to submit a skill. */
public record SkillOwnerId(UUID value) {
    public static final Codec<SkillOwnerId> CODEC =
            UUIDUtil.CODEC.xmap(SkillOwnerId::new, SkillOwnerId::value);

    public SkillOwnerId {
        Objects.requireNonNull(value, "value");
    }
}
