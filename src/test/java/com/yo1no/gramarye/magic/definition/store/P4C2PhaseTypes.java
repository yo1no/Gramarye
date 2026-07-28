package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/**
 * Exact P4-C2-A production allowlist shared by its gate and legacy phase boundaries.
 * P4-C2-B remains deliberately absent because both of its source sets are test-only.
 */
final class P4C2PhaseTypes {
    static final String PLAYER_PACKAGE =
            "com.yo1no.gramarye.magic.definition.player.";

    static final Set<String> PLAYER_SOURCE_FILE_NAMES = Set.of(
            "PlayerSkillAttachmentBuildResult.java",
            "PlayerSkillAttachmentGameTests.java",
            "PlayerSkillAttachmentService.java",
            "PlayerSkillAttachments.java",
            "ObservedPlayerSkillAttachment.java");

    static final Set<String> PLAYER_TOP_LEVEL_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentBuildResult",
            "PlayerSkillAttachmentGameTests",
            "PlayerSkillAttachmentService",
            "PlayerSkillAttachments",
            "ObservedPlayerSkillAttachment");

    static final Set<String> PUBLIC_TOP_LEVEL_TYPE_NAMES = Set.of(
            "PlayerSkillAttachmentGameTests",
            "PlayerSkillAttachmentService");

    static final Set<String> SERVICE_PUBLIC_NESTED_TYPE_NAMES = Set.of(
            "Applied",
            "Available",
            "EditorStateView",
            "LatestStateView",
            "MutationOutcome",
            "MutationRejected",
            "MutationRejectionCode",
            "NoOp",
            "PlayerSkillRootProjection",
            "Prepared",
            "PreparedPlayerSkillTransition",
            "Result",
            "TransitionPreparation",
            "TransitionRejected",
            "TransitionRejectionCode",
            "Unavailable",
            "UnavailableReason");

    static final Set<String> SERVICE_PUBLIC_METHOD_NAMES = Set.of(
            "draftCount",
            "editorState",
            "equippedAt",
            "findDraft",
            "findLatestState",
            "ownerId",
            "prepareLatestTransition",
            "publishPreparedTransition",
            "putDraft",
            "registerOn",
            "removeDraft",
            "rootProjection",
            "setEditorState",
            "setEquipped");

    private P4C2PhaseTypes() {
    }

    static boolean containsTopLevelName(String name) {
        return PLAYER_TOP_LEVEL_TYPE_NAMES.contains(name);
    }

    static boolean containsSourceFileName(String name) {
        return PLAYER_SOURCE_FILE_NAMES.contains(name);
    }
}
