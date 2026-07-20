package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

public record ScheduleId(UUID value) {
    public static final Codec<ScheduleId> CODEC = UUIDUtil.CODEC.xmap(ScheduleId::new, ScheduleId::value);

    public ScheduleId {
        Objects.requireNonNull(value, "value");
    }
}
