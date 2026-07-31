package com.yo1no.gramarye.magic.definition.store;

import java.util.List;
import java.util.Set;

/** Exact P4-D3-A production and P4-D3-B test-only phase allowlists. */
final class P4D3PhaseTypes {
    /* Exact paths below are relative to their respective test-only Java source-set roots. */
    static final Set<String> D3_PROBE_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/player/P4D3PlayerProbe.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3FileVerifier.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3FixtureBuilder.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3FixtureManifest.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3Hashing.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3ProbeCase.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3ProbeMain.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3ProbeSupport.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3RunMode.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3StoreJournalFixture.java",
            "com/yo1no/gramarye/magic/definition/submission/P4D3SubmissionProbe.java");

    static final Set<String> D3_GAME_TEST_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/store/P4D3MemoryGameTests.java",
            "com/yo1no/gramarye/magic/definition/store/P4D3ProbeServerLifecycle.java");

    static final Set<String> D3_PUBLIC_PROBE_TYPE_NAMES = Set.of(
            "com.yo1no.gramarye.magic.definition.player.P4D3PlayerProbe",
            "com.yo1no.gramarye.magic.definition.store.P4D3Hashing",
            "com.yo1no.gramarye.magic.definition.store.P4D3ProbeCase",
            "com.yo1no.gramarye.magic.definition.store.P4D3ProbeMain",
            "com.yo1no.gramarye.magic.definition.store.P4D3ProbeSupport",
            "com.yo1no.gramarye.magic.definition.store.P4D3RunMode",
            "com.yo1no.gramarye.magic.definition.store.P4D3StoreJournalFixture",
            "com.yo1no.gramarye.magic.definition.submission.P4D3SubmissionProbe");

    static final List<String> D3_SERVER_RUN_TASK_NAMES = List.of(
            "runP4D3CrashDServer",
            "runP4D3CrashDRestartServer",
            "runP4D3CrashEServer",
            "runP4D3CrashERestartServer",
            "runP4D3CrashFServer",
            "runP4D3CrashFRestartServer",
            "runP4D3CrashGServer",
            "runP4D3CrashGRestartServer",
            "runP4D3CrashHServer",
            "runP4D3CrashHRestartServer",
            "runP4D3CrashIServer",
            "runP4D3CrashIRestartServer",
            "runP4D3CrashJ1Server",
            "runP4D3CrashJ1RestartServer",
            "runP4D3CombinedHeapServer",
            "runP4D3CombinedHeapRestartServer");

    static final List<String> D3_RUN_MODE_TOKENS = List.of(
            "crash-d-first",
            "crash-d-restart",
            "crash-e-first",
            "crash-e-restart",
            "crash-f-first",
            "crash-f-restart",
            "crash-g-first",
            "crash-g-restart",
            "crash-h-first",
            "crash-h-restart",
            "crash-i-first",
            "crash-i-restart",
            "crash-j1-first",
            "crash-j1-restart",
            "combined-first",
            "combined-restart");

    static final List<String> D3_VERIFIER_TASK_NAMES = List.of(
            "verifyP4D3CrashD",
            "verifyP4D3CrashDRestart",
            "verifyP4D3CrashE",
            "verifyP4D3CrashERestart",
            "verifyP4D3CrashF",
            "verifyP4D3CrashFRestart",
            "verifyP4D3CrashG",
            "verifyP4D3CrashGRestart",
            "verifyP4D3CrashH",
            "verifyP4D3CrashHRestart",
            "verifyP4D3CrashI",
            "verifyP4D3CrashIRestart",
            "verifyP4D3CrashJ1",
            "verifyP4D3CrashJ1Restart",
            "verifyP4D3CombinedHeap",
            "verifyP4D3CombinedHeapRestart");

    static final List<String> D3_SERIAL_TASK_NAMES = List.of(
            "runP4D3CrashDServer",
            "verifyP4D3CrashD",
            "runP4D3CrashDRestartServer",
            "verifyP4D3CrashDRestart",
            "runP4D3CrashEServer",
            "verifyP4D3CrashE",
            "runP4D3CrashERestartServer",
            "verifyP4D3CrashERestart",
            "runP4D3CrashFServer",
            "verifyP4D3CrashF",
            "runP4D3CrashFRestartServer",
            "verifyP4D3CrashFRestart",
            "runP4D3CrashGServer",
            "verifyP4D3CrashG",
            "runP4D3CrashGRestartServer",
            "verifyP4D3CrashGRestart",
            "runP4D3CrashHServer",
            "verifyP4D3CrashH",
            "runP4D3CrashHRestartServer",
            "verifyP4D3CrashHRestart",
            "runP4D3CrashIServer",
            "verifyP4D3CrashI",
            "runP4D3CrashIRestartServer",
            "verifyP4D3CrashIRestart",
            "runP4D3CrashJ1Server",
            "verifyP4D3CrashJ1",
            "runP4D3CrashJ1RestartServer",
            "verifyP4D3CrashJ1Restart",
            "runP4D3CombinedHeapServer",
            "verifyP4D3CombinedHeap",
            "runP4D3CombinedHeapRestartServer",
            "verifyP4D3CombinedHeapRestart");

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
