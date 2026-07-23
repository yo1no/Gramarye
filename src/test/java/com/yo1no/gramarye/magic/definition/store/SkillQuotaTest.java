package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkillQuotaTest {
    @Test
    void unlimitedIsASingletonWithoutAdditionalPolicyState() {
        assertAll(
                () -> assertSame(SkillQuota.Unlimited.INSTANCE, SkillQuota.Unlimited.INSTANCE),
                () -> assertEquals(1, SkillQuota.Unlimited.values().length));
    }

    @Test
    void limitedAcceptsTheInclusivePolicyRangeAndProvidesValueEquality() {
        var zero = new SkillQuota.Limited(0);
        var maximum = new SkillQuota.Limited(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);

        assertAll(
                () -> assertEquals(0, zero.maxCommittedSkills()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                        maximum.maxCommittedSkills()),
                () -> assertEquals(new SkillQuota.Limited(7), new SkillQuota.Limited(7)),
                () -> assertEquals(
                        new SkillQuota.Limited(7).hashCode(),
                        new SkillQuota.Limited(7).hashCode()));
    }

    @Test
    void limitedRejectsValuesOutsideTheOwnerHardCeiling() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillQuota.Limited(-1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillQuota.Limited(
                                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1)));
    }

    @Test
    void quotaTypesExposeNoCodecProviderOrMinecraftSurface() {
        var types = Arrays.asList(
                SkillQuota.class,
                SkillQuota.Unlimited.class,
                SkillQuota.Limited.class);

        assertTrue(types.stream().allMatch(type ->
                Arrays.stream(type.getDeclaredFields()).noneMatch(field -> forbidden(
                        field.getType().getName()))
                        && Arrays.stream(type.getDeclaredMethods()).noneMatch(method ->
                                forbidden(method.getReturnType().getName())
                                        || Arrays.stream(method.getParameterTypes())
                                                .map(Class::getName)
                                                .anyMatch(SkillQuotaTest::forbidden))));
    }

    private static boolean forbidden(String typeName) {
        var lower = typeName.toLowerCase();
        return lower.contains("codec")
                || lower.contains("provider")
                || typeName.startsWith("net.minecraft.");
    }
}
