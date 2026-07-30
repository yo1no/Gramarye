package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact phase-local production allowlists for engineering P4-D3-A. */
final class P4D3PhaseTypes {
    static final String RECOVERY_SERVICE_PATH =
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillSubmissionRecoveryService.java";
    static final String RECOVERY_GAME_TEST_PATH =
            "com/yo1no/gramarye/magic/definition/store/"
                    + "SkillSubmissionRecoveryGameTests.java";

    static final Set<String> NEW_PRODUCTION_SOURCE_PATHS = Set.of(
            RECOVERY_SERVICE_PATH,
            RECOVERY_GAME_TEST_PATH);

    static final Set<String> MODIFIED_PRODUCTION_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/Gramarye.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java",
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java",
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java",
            "com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java");

    static final Set<String> PORT_PUBLIC_METHOD_NAMES = Set.of(
            "bootstrapJournal",
            "commitPreparedJournalClear",
            "commitPreparedSubmission",
            "journalRoots",
            "journalStatus",
            "observePendingRecovery",
            "observeSubmissionAuthority",
            "prepareJournalPrefixClear",
            "prepareSubmissionCommit");

    static final Set<String> PORT_RECOVERY_PUBLIC_NESTED_TYPE_NAMES = Set.of(
            "PendingRecoveryProjection",
            "PendingRecoveryStep",
            "PendingRecoveryTargetFailure",
            "PendingRecoveryUnavailableReason",
            "PendingSkillRecoveryChain");

    static final Set<String> RECOVERY_SERVICE_PUBLIC_METHOD_NAMES = Set.of(
            "create",
            "registerOn");

    static final Set<String> RECOVERY_GAME_TEST_METHOD_NAMES = Set.of(
            "persistedBaseReplaysPendingChainOnLogin",
            "persistedIntermediateClearsPrefixBeforeReplayOnLogin",
            "persistedFinalClearsPendingChainWithoutReplayOnLogin");

    private P4D3PhaseTypes() {
    }
}
