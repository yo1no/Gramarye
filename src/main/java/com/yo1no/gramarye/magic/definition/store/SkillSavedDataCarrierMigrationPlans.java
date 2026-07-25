package com.yo1no.gramarye.magic.definition.store;

/** Sole immutable production SavedData outer-carrier migration plan provider. */
final class SkillSavedDataCarrierMigrationPlans {
    private static final SkillSavedDataCarrierMigrationPlan PRODUCTION =
            SkillSavedDataCarrierMigrationPlan.empty();

    private SkillSavedDataCarrierMigrationPlans() {
    }

    static SkillSavedDataCarrierMigrationPlan production() {
        return PRODUCTION;
    }
}
