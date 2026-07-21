package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.OptionalInt;

/** Typed persistent top-level appearance fields. Missing scalar fields inherit. */
public record AppearanceDefinition(
        OptionalInt primaryArgb,
        OptionalInt secondaryArgb,
        ProfileSelection soundProfile,
        ProfileSelection particleProfile,
        ProfileSelection trailProfile,
        OptionalInt intensityMilli) {
    public AppearanceDefinition {
        Objects.requireNonNull(primaryArgb, "primaryArgb");
        Objects.requireNonNull(secondaryArgb, "secondaryArgb");
        Objects.requireNonNull(soundProfile, "soundProfile");
        Objects.requireNonNull(particleProfile, "particleProfile");
        Objects.requireNonNull(trailProfile, "trailProfile");
        Objects.requireNonNull(intensityMilli, "intensityMilli");
        AppearanceValues.requireIntensityInRange(intensityMilli);
    }

    public static AppearanceDefinition empty() {
        return new AppearanceDefinition(
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

final class AppearanceValues {
    private AppearanceValues() {
    }

    static void requireIntensityInRange(OptionalInt intensityMilli) {
        if (intensityMilli.isPresent()
                && (intensityMilli.getAsInt() < 0
                        || intensityMilli.getAsInt() > MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY)) {
            throw new IllegalArgumentException(
                    "intensityMilli must be in range [0, "
                            + MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY + "]");
        }
    }
}
