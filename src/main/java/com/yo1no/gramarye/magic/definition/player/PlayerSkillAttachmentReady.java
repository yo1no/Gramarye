package com.yo1no.gramarye.magic.definition.player;

import java.util.List;
import java.util.Objects;

/** Fully admitted immutable state paired with its prebuilt canonical carrier. */
final class PlayerSkillAttachmentReady implements PlayerSkillAttachmentState {
    private final List<PlayerDraftEntry> drafts;
    private final List<PlayerLatestState> latestStates;
    private final List<EquippedSkillReference> equipped;
    private final PlayerSkillEditorState editor;
    private final EncodedPlayerSkillAttachment carrier;

    PlayerSkillAttachmentReady(
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latestStates,
            List<EquippedSkillReference> equipped,
            PlayerSkillEditorState editor,
            EncodedPlayerSkillAttachment carrier) {
        this.drafts = List.copyOf(Objects.requireNonNull(drafts, "drafts"));
        this.latestStates = List.copyOf(Objects.requireNonNull(latestStates, "latestStates"));
        this.equipped = List.copyOf(Objects.requireNonNull(equipped, "equipped"));
        this.editor = Objects.requireNonNull(editor, "editor");
        this.carrier = Objects.requireNonNull(carrier, "carrier");
    }

    List<PlayerDraftEntry> drafts() {
        return drafts;
    }

    List<PlayerLatestState> latestStates() {
        return latestStates;
    }

    List<EquippedSkillReference> equipped() {
        return equipped;
    }

    PlayerSkillEditorState editor() {
        return editor;
    }

    EncodedPlayerSkillAttachment carrier() {
        return carrier;
    }
}
