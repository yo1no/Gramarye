package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Immutable compare-and-insert state expected by the future P3-D commit boundary. */
public sealed interface SkillCommitPrecondition
        permits SkillCommitPrecondition.ExpectedAbsent,
                SkillCommitPrecondition.ExpectedLatest {
    SkillId skillId();

    /** Commit requires that Store contain no revision for this skill identity. */
    record ExpectedAbsent(SkillId skillId) implements SkillCommitPrecondition {
        public ExpectedAbsent {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    /** Commit requires that Store's greatest revision equal {@code latest}. */
    record ExpectedLatest(SkillReference latest) implements SkillCommitPrecondition {
        public ExpectedLatest {
            Objects.requireNonNull(latest, "latest");
        }

        @Override
        public SkillId skillId() {
            return latest.skillId();
        }
    }
}
