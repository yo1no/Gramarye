package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** One-lookup, privacy-preserving Store authority observation for P4-D submission. */
sealed interface StoreSubmissionAuthorityObservation
        permits StoreSubmissionAuthorityObservation.Absent,
                StoreSubmissionAuthorityObservation.Owned,
                StoreSubmissionAuthorityObservation.ForeignOwned {
    record Absent(SkillId skillId) implements StoreSubmissionAuthorityObservation {
        public Absent {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    record Owned(SkillReference latest) implements StoreSubmissionAuthorityObservation {
        public Owned {
            Objects.requireNonNull(latest, "latest");
        }
    }

    record ForeignOwned(SkillId skillId) implements StoreSubmissionAuthorityObservation {
        public ForeignOwned {
            Objects.requireNonNull(skillId, "skillId");
        }
    }
}
