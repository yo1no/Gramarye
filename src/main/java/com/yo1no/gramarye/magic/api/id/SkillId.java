package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record SkillId(UUID value) {
    public static final Codec<SkillId> CODEC = UUIDUtil.CODEC.xmap(SkillId::new, SkillId::value);

    public SkillId {
        Objects.requireNonNull(value, "value");
    }
}
