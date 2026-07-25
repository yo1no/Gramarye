package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact phase-local allowlist shared by legacy P3-D gates and the P4-B1 API gate. */
final class P4B1PhaseTypes {
    static final Set<String> TOP_LEVEL_TYPE_NAMES = Set.of(
            "OpaquePendingAttachmentUpdatesBlob",
            "OpaqueSavedDataBlobLocation",
            "OpaqueSavedDataBlobTable",
            "OpaqueSavedDataBlobTokens",
            "OpaqueSavedDataTokenException",
            "ReinsertedSavedDataCarrier",
            "SkillSavedDataCarrierFailure",
            "SkillSavedDataCarrierLoadResult",
            "SkillSavedDataCarrierMigrationFailure",
            "SkillSavedDataCarrierMigrationPlan",
            "SkillSavedDataCarrierMigrationPlans",
            "SkillSavedDataCarrierMigrationResult",
            "SkillSavedDataCarrierMigrationStep",
            "SkillSavedDataCarrierMigrationStepOutput",
            "SkillSavedDataCarrierMigrator",
            "SkillSavedDataCarrierPersistenceBridge",
            "SkillSavedDataInnerCarrier",
            "SkillSavedDataNbtFraming",
            "SkillSavedDataPersistenceSchema",
            "SkillSavedDataReadyCandidate",
            "StrictNbtFramingInput",
            "TokenizedSavedDataCarrierSnapshot");

    private P4B1PhaseTypes() {
    }

    static boolean containsTopLevelName(String name) {
        return TOP_LEVEL_TYPE_NAMES.contains(name);
    }

    static boolean containsSourceFileName(String name) {
        if (!name.endsWith(".java")) {
            return false;
        }
        return containsTopLevelName(name.substring(0, name.length() - ".java".length()));
    }
}
