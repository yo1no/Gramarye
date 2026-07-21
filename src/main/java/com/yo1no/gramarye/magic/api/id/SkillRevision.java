package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public record SkillRevision(int value) {
    public static final Codec<SkillRevision> CODEC = Codec.intRange(0, Integer.MAX_VALUE).comapFlatMap(
            SkillRevision::decode,
            SkillRevision::value);

    public SkillRevision {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }

    private static DataResult<SkillRevision> decode(int value) {
        return value < 0
                ? DataResult.error(() -> "Skill revision must be non-negative: " + value)
                : DataResult.success(new SkillRevision(value));
    }
}
