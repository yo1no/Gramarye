package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** One bounded equipped slot. Stale references remain valid persistence state. */
record EquippedSkillReference(int slot, SkillReference reference) {
    EquippedSkillReference {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
        Objects.requireNonNull(reference, "reference");
    }
}
