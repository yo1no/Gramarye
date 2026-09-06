package com.yo1no.gramarye.magic.presentation.api;

/** Immutable validated estimate for one baked Profile instance. */
public record ProfileCost(
        int particleStarts,
        int soundStarts,
        int trailStarts,
        int trailSegments,
        int lifetimeTicks) {
    public ProfileCost {
        requireRange(particleStarts, 0, 256, "particleStarts");
        requireRange(soundStarts, 0, 8, "soundStarts");
        requireRange(trailStarts, 0, 1, "trailStarts");
        requireRange(trailSegments, 0, 48, "trailSegments");
        requireRange(lifetimeTicks, 1, 120, "lifetimeTicks");
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + "," + maximum + "]");
        }
    }
}
