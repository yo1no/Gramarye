package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record EventId(long value) {
    public static final Codec<EventId> CODEC = Codec.LONG.comapFlatMap(
            EventId::decode,
            EventId::value);

    public EventId {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }

    private static DataResult<EventId> decode(long value) {
        return value < 0
                ? DataResult.error(() -> "Event ID must be non-negative: " + value)
                : DataResult.success(new EventId(value));
    }
}
