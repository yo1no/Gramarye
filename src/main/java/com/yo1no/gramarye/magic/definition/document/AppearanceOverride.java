package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;
import java.util.OptionalInt;

/** Typed persistent node-level appearance override. Missing fields inherit. */
public record AppearanceOverride(
        OptionalInt primaryArgb,
        OptionalInt secondaryArgb,
        ProfileSelection soundProfile,
        ProfileSelection particleProfile,
        ProfileSelection trailProfile,
        OptionalInt intensityMilli) {
    public AppearanceOverride {
        Objects.requireNonNull(primaryArgb, "primaryArgb");
        Objects.requireNonNull(secondaryArgb, "secondaryArgb");
        Objects.requireNonNull(soundProfile, "soundProfile");
        Objects.requireNonNull(particleProfile, "particleProfile");
        Objects.requireNonNull(trailProfile, "trailProfile");
        Objects.requireNonNull(intensityMilli, "intensityMilli");
        AppearanceValues.requireIntensityInRange(intensityMilli);
    }

    public static AppearanceOverride empty() {
        return new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.empty(),
                ProfileSelection.inherit(),
                ProfileSelection.inherit(),
                ProfileSelection.inherit(),
                OptionalInt.empty());
    }

    public boolean isEmpty() {
        return primaryArgb.isEmpty()
                && secondaryArgb.isEmpty()
                && soundProfile instanceof ProfileSelection.Inherit
                && particleProfile instanceof ProfileSelection.Inherit
                && trailProfile instanceof ProfileSelection.Inherit
                && intensityMilli.isEmpty();
    }
}
