package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SkillMigrationPlansTest {
    @Test
    void productionPlanIsTheSingleImmutableSchemaZeroPlan() {
        var first = SkillMigrationPlans.production();
        var second = SkillMigrationPlans.production();

        assertSame(first, second);
        assertSame(SkillMigrationPlan.empty(), first);
        assertTrue(first.steps().isEmpty());
        assertTrue(first.verifyCoverage(SkillDocument.CURRENT_SCHEMA_VERSION).isSuccess());
    }

    @Test
    void productionConstructionDoesNotExposePlanInjection() {
        var auditPublicConstructors = Arrays.stream(DescriptorMigrationAudit.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toList();
        var resolverPublicConstructors = Arrays.stream(SkillCandidateResolver.class.getDeclaredConstructors())
                .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                .toList();

        assertEquals(1, auditPublicConstructors.size());
        assertEquals(0, auditPublicConstructors.getFirst().getParameterCount());
        assertEquals(1, resolverPublicConstructors.size());
        assertEquals(2, resolverPublicConstructors.getFirst().getParameterCount());
        assertFalse(Modifier.isPublic(assertPlanConstructor(
                DescriptorMigrationAudit.class,
                SkillMigrationPlan.class).getModifiers()));
        assertFalse(Modifier.isPublic(assertPlanConstructor(
                SkillCandidateResolver.class,
                com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup.class,
                com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup.class,
                SkillMigrationPlan.class).getModifiers()));
    }

    private static java.lang.reflect.Constructor<?> assertPlanConstructor(
            Class<?> type,
            Class<?>... parameters) {
        try {
            return type.getDeclaredConstructor(parameters);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
