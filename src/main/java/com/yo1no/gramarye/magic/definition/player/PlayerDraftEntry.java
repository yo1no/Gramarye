package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import java.util.Objects;

/** One admitted Draft route and its matching current physical carrier. */
record PlayerDraftEntry(
        SkillId skillId,
        SkillDraft draft,
        SkillDraftPersistenceFacade.EncodedSkillDraft encodedDraft) {
    PlayerDraftEntry {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(encodedDraft, "encodedDraft");
        if (!skillId.equals(draft.skillId())) {
            throw new IllegalArgumentException("Draft route must match the typed Draft");
        }
    }
}
