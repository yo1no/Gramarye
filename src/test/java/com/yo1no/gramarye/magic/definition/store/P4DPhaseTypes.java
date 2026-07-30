package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact phase-local production allowlists for engineering P4-D1 through P4-D2-B. */
final class P4DPhaseTypes {
    static final Set<String> NEW_STORE_SOURCE_FILE_NAMES = Set.of(
            "JournalTargetAuditProof.java",
            "JournalTargetAuditResult.java",
            "PendingAttachmentJournal.java",
            "PendingAttachmentJournalFailure.java",
            "PendingAttachmentJournalFraming.java",
            "PendingAttachmentJournalLifecycle.java",
            "PendingAttachmentJournalMigration.java",
            "PendingAttachmentJournalSchema.java",
            "PendingAttachmentJournalState.java",
            "PendingAttachmentJournalWireScan.java",
            "SkillDefinitionStoreSubmissionPort.java",
            "StoreSubmissionAuthorityObservation.java");

    static final Set<String> NEW_STORE_TOP_LEVEL_TYPE_NAMES = Set.of(
            "EncodedPendingAttachmentJournal",
            "JournalTargetAuditProof",
            "JournalTargetAuditResult",
            "MalformedWireException",
            "PendingAttachmentJournal",
            "PendingAttachmentJournalCursor",
            "PendingAttachmentJournalEntry",
            "PendingAttachmentJournalEntryPhysicalV0",
            "PendingAttachmentJournalFailure",
            "PendingAttachmentJournalFraming",
            "PendingAttachmentJournalLifecycle",
            "PendingAttachmentJournalLoadCandidate",
            "PendingAttachmentJournalLoadResult",
            "PendingAttachmentJournalMigrationPlan",
            "PendingAttachmentJournalMigrationPlans",
            "PendingAttachmentJournalMigrationResult",
            "PendingAttachmentJournalMigrationStep",
            "PendingAttachmentJournalMigrationStepOutput",
            "PendingAttachmentJournalMigrator",
            "PendingAttachmentJournalOperationalFailure",
            "PendingAttachmentJournalPhysicalV0",
            "PendingAttachmentJournalSchema",
            "PendingAttachmentJournalState",
            "PendingAttachmentJournalWireScan",
            "SkillDefinitionStoreSubmissionPort",
            "StoreSubmissionAuthorityObservation");

    static final Set<String> MODIFIED_STORE_SOURCE_FILE_NAMES = Set.of(
            "GramaryeSkillSavedData.java",
            "OpaquePendingAttachmentUpdatesBlob.java",
            "SkillDefinitionStore.java",
            "SkillDefinitionStoreService.java",
            "SkillSavedDataLifecycleGameTests.java");

    static final Set<String> MODIFIED_NON_STORE_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java");

    static final String ONLY_NEW_PUBLIC_TOP_LEVEL =
            "SkillDefinitionStoreSubmissionPort";

    static final Set<String> D2A_SUBMISSION_SOURCE_FILE_NAMES = Set.of(
            "DefaultSkillSubmissionPolicyProvider.java",
            "RandomUuidSkillIdSource.java",
            "SkillDraftCreationService.java",
            "SkillSubmissionCompositionOutcome.java",
            "SkillSubmissionPolicyProvider.java",
            "SkillSubmissionPolicySnapshot.java",
            "SkillSubmissionPreparationPipeline.java");

    static final Set<String> D2A_SUBMISSION_TOP_LEVEL_TYPE_NAMES = Set.of(
            "DefaultSkillSubmissionPolicyProvider",
            "RandomUuidSkillIdSource",
            "SkillDraftCreationService",
            "SkillSubmissionCompositionOutcome",
            "SkillSubmissionPolicyProvider",
            "SkillSubmissionPolicySnapshot",
            "SkillSubmissionPreparationPipeline");

    static final Set<String> D2A_PUBLIC_TOP_LEVEL_TYPE_NAMES = Set.of(
            "SkillDraftCreationService",
            "SkillSubmissionCompositionOutcome",
            "SkillSubmissionPolicyProvider",
            "SkillSubmissionPolicySnapshot");

    static final Set<String> D2A_MODIFIED_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java");

    static final Set<String> D2B_SUBMISSION_SOURCE_FILE_NAMES = Set.of(
            "SkillDefinitionSubmissionGameTests.java",
            "SkillDefinitionSubmissionService.java");

    static final Set<String> D2B_SUBMISSION_TOP_LEVEL_TYPE_NAMES = Set.of(
            "SkillDefinitionSubmissionGameTests",
            "SkillDefinitionSubmissionService");

    static final Set<String> D2B_PUBLIC_TOP_LEVEL_TYPE_NAMES = Set.of(
            "SkillDefinitionSubmissionGameTests",
            "SkillDefinitionSubmissionService");

    static final Set<String> D2B_MODIFIED_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/Gramarye.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java");

    private P4DPhaseTypes() {
    }

    static boolean containsNewStoreSourceFileName(String name) {
        return NEW_STORE_SOURCE_FILE_NAMES.contains(name);
    }

    static boolean containsNewStoreTopLevelName(String name) {
        return NEW_STORE_TOP_LEVEL_TYPE_NAMES.contains(name);
    }
}
