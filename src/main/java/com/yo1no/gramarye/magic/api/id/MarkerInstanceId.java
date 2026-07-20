package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record MarkerInstanceId(UUID value) {
    public static final Codec<MarkerInstanceId> CODEC = UUIDUtil.CODEC.xmap(MarkerInstanceId::new, MarkerInstanceId::value);

    public MarkerInstanceId {
        Objects.requireNonNull(value, "value");
    }
}
