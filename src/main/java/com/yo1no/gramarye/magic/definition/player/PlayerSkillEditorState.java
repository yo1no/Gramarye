package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Structurally valid editor metadata; stale selections are deliberately retained. */
record PlayerSkillEditorState(Optional<SkillId> selectedDraft, OptionalInt selectedNodeIndex) {
    PlayerSkillEditorState {
        selectedDraft = Objects.requireNonNull(selectedDraft, "selectedDraft");
        selectedNodeIndex = Objects.requireNonNull(selectedNodeIndex, "selectedNodeIndex");
        if (selectedNodeIndex.isPresent()
                && (selectedNodeIndex.getAsInt() < 0
                        || selectedNodeIndex.getAsInt() >= MagicSafetyCeilings.MAX_NODES)) {
            throw new IllegalArgumentException("selectedNodeIndex is outside the hard node boundary");
        }
    }

    static PlayerSkillEditorState empty() {
        return new PlayerSkillEditorState(Optional.empty(), OptionalInt.empty());
    }
}
