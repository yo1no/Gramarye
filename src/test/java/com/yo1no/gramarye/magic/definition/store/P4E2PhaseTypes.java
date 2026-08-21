package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact production, integration, and evidence inventory for engineering P4-E2. */
final class P4E2PhaseTypes {
    static final String ROOT_PACKAGE = "com.yo1no.gramarye.";
    static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    static final String PLAYER_PACKAGE =
            "com.yo1no.gramarye.magic.definition.player.";
    static final String SUBMISSION_PACKAGE =
            "com.yo1no.gramarye.magic.definition.submission.";

    static final Set<String> NEW_STORE_SOURCE_FILE_NAMES = Set.of(
            "P4E2BoundPlayerSkillAttachmentReconciliationCapability.java",
            "P4E2GroupedStoreValidation.java",
            "P4E2OnlineReconciliationCoordinator.java",
            "P4E2OnlineReconciliationDependency.java",
            "P4E2ReconciliationResult.java",
            "PlayerSkillAttachmentReconciliationCapability.java");

    static final Set<String> NEW_STORE_TOP_LEVEL_TYPE_NAMES = Set.of(
            "P4E2BoundPlayerSkillAttachmentReconciliationCapability",
            "P4E2GroupedStoreValidation",
            "P4E2OnlineReconciliationCoordinator",
            "P4E2OnlineReconciliationDependency",
            "P4E2ReconciliationResult",
            "PlayerSkillAttachmentReconciliationCapability");

    static final Set<String> PUBLIC_STORE_TOP_LEVEL_TYPE_NAMES = Set.of(
            "P4E2OnlineReconciliationDependency",
            "PlayerSkillAttachmentReconciliationCapability");

    static final Set<String> DEPENDENCY_PUBLIC_NESTED_TYPE_NAMES = Set.of(
            "RecoveryKind");

    static final Set<String> MODIFIED_PRODUCTION_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/Gramarye.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java",
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillDefinitionSubmissionGameTests.java",
            "com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java");

    static final Set<String> DIRECT_OBSERVATION_PRODUCTION_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/Gramarye.java",
            "com/yo1no/gramarye/P4E2QualificationFacade.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E2BoundPlayerSkillAttachmentReconciliationCapability.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E2OnlineReconciliationCoordinator.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "PlayerSkillAttachmentReconciliationCapability.java",
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java",
            "com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java");

    static final Set<String> QUALIFICATION_FACADE_PUBLIC_ENUM_NAMES = Set.of(
            "RecoveryVariant",
            "RecoveryDetail",
            "ReconciliationVariant",
            "ReconciliationDetail");

    static final Set<String> QUALIFICATION_FACADE_PUBLIC_VIEW_NAMES = Set.of(
            "SubmissionView",
            "StoreView",
            "PlayerView");

    static final Set<String> REQUIRED_TEST_SOURCE_FILE_NAMES = Set.of(
            "P4E2ApiGateTest.java",
            "P4E2AtomicReconciliationTest.java",
            "P4E2GroupedStoreValidationTest.java",
            "P4E2LifecycleOrderingTest.java",
            "P4E2PhaseTypes.java",
            "P4E2VisibilityCompileTest.java");

    static final Set<String> RESULT_VARIANT_NAMES = Set.of(
            "Changed",
            "Deferred",
            "Failed",
            "GenerationExhausted",
            "NoChanges",
            "RecoveryChanged");

    static final Set<String> PLAYER_SERVICE_E2_PUBLIC_METHOD_NAMES = Set.of(
            "checkPreparedReconciliationCurrent",
            "discardOnlineReconciliationHandle",
            "discardOnlineReconciliationProjection",
            "discardPreparedReconciliation",
            "drainOnlineReconciliation",
            "isOnlineReconciliationCurrent",
            "observeOnlineForReconciliation",
            "onlineReconciliationState",
            "prepareOnlineReconciliation",
            "publishPreparedReconciliation");

    static final Set<String> PLAYER_SERVICE_E2_PUBLIC_NESTED_TYPE_NAMES = Set.of(
            "OnlineReconciliationHandle",
            "OnlineReconciliationSink",
            "OnlineReconciliationState",
            "OpaqueReconciliationCapability",
            "PreparedOnlineReconciliation",
            "PreparedReconciliation",
            "ReconciliationBuildFailure",
            "ReconciliationBuildRejected",
            "ReconciliationCurrentness",
            "ReconciliationGenerationExhausted",
            "ReconciliationPreparationResult",
            "ReconciliationPublication",
            "ReconciliationStateChanged");

    static final Set<String> FORBIDDEN_E2_SOURCE_TOKENS = Set.of(
            "SkillRetentionRootSnapshot.fromCompleteRoots",
            ".reclaim(",
            "PlayerEvent.Clone",
            "PlayerLoggedOutEvent",
            "ServerStartedEvent",
            "ServerStartingEvent",
            "CompletableFuture",
            "ExecutorService",
            "Executors.",
            "parallelStream(",
            "java.lang.reflect",
            "setAccessible(",
            "sun.misc.Unsafe",
            "@SuppressWarnings",
            "CustomPacketPayload",
            "PayloadRegistrar");

    private P4E2PhaseTypes() {
    }
}
