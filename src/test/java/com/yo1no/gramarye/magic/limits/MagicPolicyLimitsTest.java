package com.yo1no.gramarye.magic.limits;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MagicPolicyLimitsTest {
    @Test
    void acceptsPolicyValuesWithinEveryHardCeiling() {
        var limits = new MagicPolicyLimits(1, 2, 3, 4, 5, 6);

        assertAll(
                () -> assertEquals(1, limits.maxNodes()),
                () -> assertEquals(2, limits.maxStringLength()),
                () -> assertEquals(3, limits.maxRawPayloadBytes()),
                () -> assertEquals(4, limits.maxRuntimeTags()),
                () -> assertEquals(5, limits.maxVisitedTargets()),
                () -> assertEquals(6, limits.maxAppearanceIntensity()));
    }

    @Test
    void rejectsZeroPolicyValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(0, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 0, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 0, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, 1, 0, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, 1, 1, 0)));
    }

    @Test
    void rejectsNegativePolicyValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(-1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, -1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, -1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, -1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, 1, -1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(1, 1, 1, 1, 1, -1)));
    }

    @Test
    void rejectsPolicyValuesAboveTheirHardCeilings() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        MagicSafetyCeilings.MAX_NODES + 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        1, MagicSafetyCeilings.MAX_STRING_LENGTH + 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        1, 1, MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES + 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        1, 1, 1, MagicSafetyCeilings.MAX_RUNTIME_TAGS + 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        1, 1, 1, 1, MagicSafetyCeilings.MAX_VISITED_TARGETS + 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MagicPolicyLimits(
                        1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY + 1)));
    }

    @Test
    void provisionalDefaultsStayPositiveAndWithinHardCeilings() {
        var defaults = MagicPolicyLimits.DEFAULTS;

        assertAll(
                () -> assertTrue(defaults.maxNodes() > 0 && defaults.maxNodes() <= MagicSafetyCeilings.MAX_NODES),
                () -> assertTrue(defaults.maxStringLength() > 0
                        && defaults.maxStringLength() <= MagicSafetyCeilings.MAX_STRING_LENGTH),
                () -> assertTrue(defaults.maxRawPayloadBytes() > 0
                        && defaults.maxRawPayloadBytes() <= MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES),
                () -> assertTrue(defaults.maxRuntimeTags() > 0
                        && defaults.maxRuntimeTags() <= MagicSafetyCeilings.MAX_RUNTIME_TAGS),
                () -> assertTrue(defaults.maxVisitedTargets() > 0
                        && defaults.maxVisitedTargets() <= MagicSafetyCeilings.MAX_VISITED_TARGETS),
                () -> assertTrue(defaults.maxAppearanceIntensity() > 0
                        && defaults.maxAppearanceIntensity() <= MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY));
    }
}
