package com.yo1no.gramarye.magic.capability;

import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Integer-only bounds for future presentation parameters; no appearance content is defined here. */
public record AppearanceParameterPolicy(Map<ResourceLocation, IntRange> integerRanges) {
    private static final AppearanceParameterPolicy NONE = new AppearanceParameterPolicy(Map.of());

    public AppearanceParameterPolicy {
        integerRanges = Map.copyOf(Objects.requireNonNull(integerRanges, "integerRanges"));
    }

    public static AppearanceParameterPolicy none() {
        return NONE;
    }

    public record IntRange(int minInclusive, int maxInclusive) {
        public IntRange {
            if (minInclusive > maxInclusive) {
                throw new IllegalArgumentException("minInclusive must not exceed maxInclusive");
            }
        }
    }
}
