package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Typed authoritative new/existing state for one submission snapshot. */
public sealed interface AuthorizedSkillState
        permits AuthorizedSkillState.New, AuthorizedSkillState.Existing {
    SkillId skillId();

    /** Creation is currently authorized, but the ID is not reserved. */
    record New(SkillId skillId) implements AuthorizedSkillState {
        public New {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    /** Existing state whose reference is the greatest revision currently present in Store. */
    record Existing(SkillReference latestStoredRevision) implements AuthorizedSkillState {
        public Existing {
            Objects.requireNonNull(latestStoredRevision, "latestStoredRevision");
        }

        @Override
        public SkillId skillId() {
            return latestStoredRevision.skillId();
        }
    }
}
