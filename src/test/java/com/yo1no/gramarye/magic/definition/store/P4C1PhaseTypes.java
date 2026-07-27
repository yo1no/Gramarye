package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact P4-C1 production allowlist shared by its gate and legacy phase-boundary checks. */
final class P4C1PhaseTypes {
    static final String PLAYER_PACKAGE =
            "com.yo1no.gramarye.magic.definition.player.";
    static final String DOCUMENT_PACKAGE =
            "com.yo1no.gramarye.magic.definition.document.";

    static final Set<String> PLAYER_SOURCE_FILE_NAMES = Set.of(
            "AttachmentTagSize.java",
            "AttachmentTagSizeResult.java",
            "BoundedCountingDataOutput.java",
            "EncodedPlayerSkillAttachment.java",
            "EquippedSkillReference.java",
            "MutationGeneration.java",
            "PlayerDraftEntry.java",
            "PlayerLatestState.java",
            "PlayerSkillAttachmentCodecs.java",
            "PlayerSkillAttachmentFailure.java",
            "PlayerSkillAttachmentMarker.java",
            "PlayerSkillAttachmentMigrationFailure.java",
            "PlayerSkillAttachmentMigrationPlan.java",
            "PlayerSkillAttachmentMigrationPlans.java",
            "PlayerSkillAttachmentMigrationResult.java",
            "PlayerSkillAttachmentMigrationStep.java",
            "PlayerSkillAttachmentMigrator.java",
            "PlayerSkillAttachmentPersistenceBridge.java",
            "PlayerSkillAttachmentQuarantine.java",
            "PlayerSkillAttachmentReady.java",
            "PlayerSkillAttachmentSchema.java",
            "PlayerSkillAttachmentSerializer.java",
            "PlayerSkillAttachmentState.java",
            "PlayerSkillEditorState.java");

    static final Set<String> PLAYER_TOP_LEVEL_TYPE_NAMES = Set.of(
            "AttachmentTagSize",
            "AttachmentTagSizeResult",
            "BoundedCountingDataOutput",
            "EncodedPlayerSkillAttachment",
            "EquippedSkillReference",
            "MutationGeneration",
            "PlayerDraftEntry",
            "PlayerLatestState",
            "PlayerSkillAttachmentCodecs",
            "PlayerSkillAttachmentFailure",
            "PlayerSkillAttachmentMarker",
            "PlayerSkillAttachmentMigrationFailure",
            "PlayerSkillAttachmentMigrationPlan",
            "PlayerSkillAttachmentMigrationPlans",
            "PlayerSkillAttachmentMigrationResult",
            "PlayerSkillAttachmentMigrationStep",
            "PlayerSkillAttachmentMigrator",
            "PlayerSkillAttachmentOversizeMarker",
            "PlayerSkillAttachmentPersistenceBridge",
            "PlayerSkillAttachmentPreservedRaw",
            "PlayerSkillAttachmentQuarantine",
            "PlayerSkillAttachmentReady",
            "PlayerSkillAttachmentSchema",
            "PlayerSkillAttachmentSerializer",
            "PlayerSkillAttachmentState",
            "PlayerSkillEditorState");

    static final Set<String> DOCUMENT_SOURCE_FILE_NAMES = Set.of(
            "DraftPersistenceMigration.java",
            "LogicalSkillDraftConformanceView.java",
            "OpaqueDraftRawTreeTable.java",
            "PhysicalSkillDraft.java",
            "PhysicalSkillDraftNbt.java",
            "SkillDraftLogicalMigration.java",
            "SkillDraftPersistenceBridge.java",
            "SkillDraftPersistenceFacade.java");

    static final Set<String> DOCUMENT_TOP_LEVEL_TYPE_NAMES = Set.of(
            "DraftPersistenceMigration",
            "LogicalSkillDraftConformanceView",
            "OpaqueDraftRawTreeTable",
            "PhysicalDraftActionSlot",
            "PhysicalDraftNode",
            "PhysicalDraftTriggerSlot",
            "PhysicalSkillDraft",
            "PhysicalSkillDraftNbt",
            "SkillDraftLogicalMigration",
            "SkillDraftPersistenceBridge",
            "SkillDraftPersistenceFacade");

    static final Set<String> PUBLIC_TOP_LEVEL_TYPE_NAMES = Set.of(
            "SkillDraftPersistenceFacade");

    static final Set<String> FACADE_PUBLIC_NESTED_TYPE_NAMES = Set.of(
            "CapacityFailure",
            "CaptureRejected",
            "CaptureResult",
            "Captured",
            "CodecFailure",
            "Encoded",
            "EncodedSkillDraft",
            "EncodeRejected",
            "EncodeResult",
            "Failure",
            "FailureCode",
            "Loaded",
            "LoadRejected",
            "LoadResult",
            "SimpleFailure");

    private P4C1PhaseTypes() {
    }

    static boolean containsTopLevelName(String name) {
        return PLAYER_TOP_LEVEL_TYPE_NAMES.contains(name)
                || DOCUMENT_TOP_LEVEL_TYPE_NAMES.contains(name);
    }

    static boolean containsSourceFileName(String name) {
        return PLAYER_SOURCE_FILE_NAMES.contains(name)
                || DOCUMENT_SOURCE_FILE_NAMES.contains(name);
    }
}
