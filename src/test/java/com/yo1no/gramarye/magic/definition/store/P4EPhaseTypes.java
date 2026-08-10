package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact P4-E1-B2-A production allowlist; B2-B and later P4-E phases remain absent. */
final class P4EPhaseTypes {
    static final Set<String> STORE_TYPE_NAMES = Set.of(
            "P4E1AuditBudget",
            "P4E1AuditCounter",
            "P4E1AuditStage",
            "P4E1CompressedCapacityRejected",
            "P4E1FileMetadata",
            "P4E1FileSystemAccess",
            "P4E1HeapFloorObservation",
            "P4E1HeapFloorStatus",
            "P4E1IntegratedSnapshotTraversal",
            "P4E1BoundPlayerSkillAttachmentAdmissionSource",
            "P4E1AuditedCapture",
            "P4E1GlobalSourceCapture",
            "P4E1GroupedStoreAudit",
            "P4E1PendingJournalObservation",
            "P4E1PlayerDataDirectorySnapshot",
            "P4E1PlayerDataFileReader",
            "P4E1PlayerDataNbtScanner",
            "P4E1PlayerDataSourceSelector",
            "P4E1RawClaimBuffer",
            "P4E1RootSourceFamily",
            "P4E1SourceAdmissionPreflight",
            "P4E1SourceFailure",
            "P4E1SourceInventory",
            "P4E1StoreHistoryObservation",
            "PlayerSkillAttachmentAdmissionSource");

    static final Set<String> PUBLIC_STORE_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentAdmissionSource");

    static final Set<String> PLAYER_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentAdmission",
            "PlayerSkillAttachmentSourceObservation");

    static final Set<String> MODIFIED_PRODUCTION_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSerializer.java",
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java",
            "com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java",
            "com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java",
            "com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInput.java",
            "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java");

    static final Set<String> FORBIDDEN_LATER_PHASE_TOKENS = Set.of(
            "SkillRetentionRootAuditService",
            "SkillRetentionRootAuditResult",
            "P4E1RootIndex",
            "P4E1RootHandoff",
            "P4E1Complete",
            "OfflineRootIndex",
            "RootInventory",
            "Reconciliation",
            "SkillRetentionRootSnapshot",
            ".reclaim(",
            "ServerStartingEvent",
            "ServerStartedEvent",
            "PlayerLoggedInEvent",
            "PlayerLoggedOutEvent",
            "PlayerEvent.Clone",
            "CompletableFuture",
            "parallelStream(",
            "CustomPacketPayload",
            "PayloadRegistrar");

    private P4EPhaseTypes() {
    }
}
