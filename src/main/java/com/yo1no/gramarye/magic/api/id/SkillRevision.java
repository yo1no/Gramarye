package com.yo1no.gramarye.magic.api.id;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;

public record SkillRevision(int value) {
    public static final Codec<SkillRevision> CODEC = Codec.intRange(0, Integer.MAX_VALUE).comapFlatMap(
            SkillRevision::decode,
            SkillRevision::value);

    public SkillRevision {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
    }

    /**
     * Computes the next candidate revision value when one exists.
     *
     * <p>This value operation does not allocate or commit a revision; formal allocation belongs
     * to a successful Store commit.</p>
     */
    public Optional<SkillRevision> successor() {
        return value == Integer.MAX_VALUE
                ? Optional.empty()
                : Optional.of(new SkillRevision(value + 1));
    }

    private static DataResult<SkillRevision> decode(int value) {
        return value < 0
                ? DataResult.error(() -> "Skill revision must be non-negative: " + value)
                : DataResult.success(new SkillRevision(value));
    }
}
