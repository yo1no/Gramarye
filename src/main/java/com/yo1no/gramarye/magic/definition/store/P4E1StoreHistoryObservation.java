package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Opaque, clearable observation of one exact Store history route. */
sealed interface P4E1StoreHistoryObservation
        permits P4E1StoreHistoryObservation.Absent,
                P4E1StoreHistoryObservation.Present {
    enum Absent implements P4E1StoreHistoryObservation {
        INSTANCE
    }

    final class Present implements P4E1StoreHistoryObservation {
        private SkillId skillId;
        private StoredSkillHistory history;

        Present(SkillId skillId, StoredSkillHistory history) {
            this.skillId = Objects.requireNonNull(skillId, "skillId");
            this.history = Objects.requireNonNull(history, "history");
        }

        boolean ownerMatches(SkillOwnerId expectedOwner) {
            requireActive();
            return history.owner().equals(Objects.requireNonNull(
                    expectedOwner, "expectedOwner"));
        }

        boolean contains(SkillReference reference) {
            requireActive();
            Objects.requireNonNull(reference, "reference");
            if (!skillId.equals(reference.skillId())) {
                throw new IllegalArgumentException(
                        "history observation and reference route must match");
            }
            return history.revisions().containsKey(reference.revision());
        }

        void discard() {
            skillId = null;
            history = null;
        }

        private void requireActive() {
            if (skillId == null || history == null) {
                throw new IllegalStateException("P4E1_STORE_HISTORY_OBSERVATION_DISCARDED");
            }
        }
    }
}
