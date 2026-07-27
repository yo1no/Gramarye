package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import java.util.Optional;

/** One explicit latest-pointer route; absence from the list means empty pointer at generation zero. */
record PlayerLatestState(
        SkillId skillId,
        Optional<SkillReference> pointer,
        int mutationGeneration) {
    PlayerLatestState {
        Objects.requireNonNull(skillId, "skillId");
        pointer = Objects.requireNonNull(pointer, "pointer");
        if (mutationGeneration < 0) {
            throw new IllegalArgumentException("mutationGeneration must be non-negative");
        }
        if (pointer.isPresent() && !pointer.orElseThrow().skillId().equals(skillId)) {
            throw new IllegalArgumentException("Latest pointer route must match its entry route");
        }
    }
}
