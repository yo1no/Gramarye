package com.yo1no.gramarye.magic.definition.migration;

/** The single immutable production provider for skill-document schema migration. */
public final class SkillMigrationPlans {
    private static final SkillMigrationPlan PRODUCTION = SkillMigrationPlan.empty();

    private SkillMigrationPlans() {
    }

    /** Returns the immutable production plan; it is not runtime replaceable. */
    public static SkillMigrationPlan production() {
        return PRODUCTION;
    }
}
