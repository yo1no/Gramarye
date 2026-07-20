package com.yo1no.gramarye.magic.limits;

/**
 * Server policy limits constrained by {@link MagicSafetyCeilings}.
 *
 * <p>{@link #DEFAULTS} contains provisional policy defaults, not final gameplay balance. P1 does
 * not register these values as NeoForge configuration.</p>
 */
public record MagicPolicyLimits(
        int maxNodes,
        int maxStringLength,
        int maxRawPayloadBytes,
        int maxRuntimeTags,
        int maxVisitedTargets,
        int maxAppearanceIntensity) {
    public static final MagicPolicyLimits DEFAULTS = new MagicPolicyLimits(
            64, // Provisional node policy default; not final gameplay balance.
            256, // Provisional string policy default; not final content sizing.
            64 * 1_024, // Provisional raw payload policy default of 64 KiB.
            16, // Provisional runtime-tag policy default; not final gameplay balance.
            32, // Provisional visited-target policy default; not final gameplay balance.
            1_000); // Provisional intensity policy default; its fixed-point scale is not set in P1.

    public MagicPolicyLimits {
        requireWithinCeiling("maxNodes", maxNodes, MagicSafetyCeilings.MAX_NODES);
        requireWithinCeiling("maxStringLength", maxStringLength, MagicSafetyCeilings.MAX_STRING_LENGTH);
        requireWithinCeiling("maxRawPayloadBytes", maxRawPayloadBytes, MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES);
        requireWithinCeiling("maxRuntimeTags", maxRuntimeTags, MagicSafetyCeilings.MAX_RUNTIME_TAGS);
        requireWithinCeiling("maxVisitedTargets", maxVisitedTargets, MagicSafetyCeilings.MAX_VISITED_TARGETS);
        requireWithinCeiling(
                "maxAppearanceIntensity",
                maxAppearanceIntensity,
                MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY);
    }

    private static void requireWithinCeiling(String name, int value, int ceiling) {
        if (value <= 0 || value > ceiling) {
            throw new IllegalArgumentException(name + " must be in range [1, " + ceiling + "]: " + value);
        }
    }
}
