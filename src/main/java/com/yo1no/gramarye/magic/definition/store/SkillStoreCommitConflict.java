package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Machine-readable optimistic-concurrency conflict from the Store commit boundary. */
public sealed interface SkillStoreCommitConflict
        permits SkillStoreCommitConflict.ExpectedAbsentButPresent,
                SkillStoreCommitConflict.ExpectedLatestButAbsent,
                SkillStoreCommitConflict.LatestMismatch {
    SkillId skillId();

    record ExpectedAbsentButPresent(SkillId skillId) implements SkillStoreCommitConflict {
        public ExpectedAbsentButPresent {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    record ExpectedLatestButAbsent(SkillReference expected)
            implements SkillStoreCommitConflict {
        public ExpectedLatestButAbsent {
            Objects.requireNonNull(expected, "expected");
        }

        @Override
        public SkillId skillId() {
            return expected.skillId();
        }
    }

    record LatestMismatch(
            SkillReference expected,
            SkillReference observed) implements SkillStoreCommitConflict {
        public LatestMismatch {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(observed, "observed");
            if (!expected.skillId().equals(observed.skillId())) {
                throw new IllegalArgumentException(
                        "expected and observed references must use the same SkillId");
            }
            if (expected.revision().equals(observed.revision())) {
                throw new IllegalArgumentException(
                        "expected and observed revisions must differ");
            }
        }

        @Override
        public SkillId skillId() {
            return expected.skillId();
        }
    }
}
