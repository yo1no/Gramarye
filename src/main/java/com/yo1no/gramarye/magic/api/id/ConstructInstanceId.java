package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record ConstructInstanceId(UUID value) {
    public static final Codec<ConstructInstanceId> CODEC = UUIDUtil.CODEC.xmap(ConstructInstanceId::new, ConstructInstanceId::value);

    public ConstructInstanceId {
        Objects.requireNonNull(value, "value");
    }
}
