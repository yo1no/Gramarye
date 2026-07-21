package com.yo1no.gramarye.magic.limits;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MagicPolicyLimitsTest {
    @Test
    void acceptsPolicyValuesWithinEveryHardCeiling() {
        var limits = limits(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        assertAll(
                () -> assertEquals(1, limits.maxNodes()),
                () -> assertEquals(2, limits.maxStringLength()),
                () -> assertEquals(3, limits.maxRawPayloadBytes()),
                () -> assertEquals(4, limits.maxRuntimeTags()),
                () -> assertEquals(5, limits.maxVisitedTargets()),
                () -> assertEquals(6, limits.maxAppearanceIntensity()),
                () -> assertEquals(7, limits.maxUnparsedAppearanceDepth()),
                () -> assertEquals(8, limits.maxUnparsedAppearanceNodes()),
                () -> assertEquals(9, limits.maxSkillDocumentDepth()),
                () -> assertEquals(10, limits.maxSkillDocumentBytes()));
    }

    @Test
    void rejectsZeroPolicyValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> limits(0, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 0, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 0, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 0, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 0, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 0, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 0, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 0, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, 0, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, 1, 0)));
    }

    @Test
    void rejectsNegativePolicyValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> limits(-1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, -1, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, -1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, -1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, -1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, -1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, -1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, -1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, -1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, 1, -1)));
    }

    @Test
    void rejectsPolicyValuesAboveTheirHardCeilings() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> limits(MagicSafetyCeilings.MAX_NODES + 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, MagicSafetyCeilings.MAX_STRING_LENGTH + 1, 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES + 1, 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, MagicSafetyCeilings.MAX_RUNTIME_TAGS + 1, 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, MagicSafetyCeilings.MAX_VISITED_TARGETS + 1, 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY + 1, 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_DEPTH + 1, 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_NODES + 1, 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH + 1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> limits(1, 1, 1, 1, 1, 1, 1, 1, 1, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1)));
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
                        && defaults.maxAppearanceIntensity() <= MagicSafetyCeilings.MAX_APPEARANCE_INTENSITY),
                () -> assertTrue(defaults.maxUnparsedAppearanceDepth() > 0
                        && defaults.maxUnparsedAppearanceDepth() <= MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_DEPTH),
                () -> assertTrue(defaults.maxUnparsedAppearanceNodes() > 0
                        && defaults.maxUnparsedAppearanceNodes() <= MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_NODES),
                () -> assertTrue(defaults.maxSkillDocumentDepth() > 0
                        && defaults.maxSkillDocumentDepth() <= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH),
                () -> assertTrue(defaults.maxSkillDocumentBytes() > 0
                        && defaults.maxSkillDocumentBytes() <= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
    }

    private static MagicPolicyLimits limits(int... values) {
        return new MagicPolicyLimits(
                values[0], values[1], values[2], values[3], values[4],
                values[5], values[6], values[7], values[8], values[9]);
    }
}
