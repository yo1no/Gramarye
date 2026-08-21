package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Historical P4-E1 inventory plus exact post-P3 production exemptions. */
final class P4EPhaseTypes {
    static final Set<String> E1_STORE_TYPE_NAMES = Set.of(
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
            "P4E1CompleteRootHandoff",
            "P4E1FinalFreshness",
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
            "PlayerSkillAttachmentAdmissionSource",
            "SkillRetentionRootAuditResult",
            "SkillRetentionRootAuditService");

    static final Set<String> E1_PUBLIC_STORE_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentAdmissionSource",
            "SkillRetentionRootAuditResult");

    static final Set<String> E1_PLAYER_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentAdmission",
            "PlayerSkillAttachmentSourceObservation");

    /* Legacy post-P3 gates consume this aggregate exact-name exemption. */
    static final Set<String> STORE_TYPE_NAMES = java.util.stream.Stream.concat(
                    E1_STORE_TYPE_NAMES.stream(),
                    P4E2PhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES.stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    static final Set<String> PUBLIC_STORE_TYPE_NAMES = java.util.stream.Stream.concat(
                    E1_PUBLIC_STORE_TYPE_NAMES.stream(),
                    P4E2PhaseTypes.PUBLIC_STORE_TOP_LEVEL_TYPE_NAMES.stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    static final Set<String> PLAYER_TYPE_NAMES = E1_PLAYER_TYPE_NAMES;

    static final Set<String> FORBIDDEN_LATER_PHASE_TOKENS = Set.of(
            "P4E1RootIndex",
            "P4E1RootHandoff",
            "P4E1Reconciliation",
            "OfflineRootIndex",
            "RootInventory",
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
