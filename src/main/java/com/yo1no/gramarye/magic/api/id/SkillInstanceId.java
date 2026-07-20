package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record SkillInstanceId(UUID value) {
    public static final Codec<SkillInstanceId> CODEC = UUIDUtil.CODEC.xmap(SkillInstanceId::new, SkillInstanceId::value);

    public SkillInstanceId {
        Objects.requireNonNull(value, "value");
    }
}
