package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Machine-readable optimistic-concurrency conflict for an authorized submission. */
public sealed interface SkillSubmissionConflict
        permits SkillSubmissionConflict.BaseRevisionForNew,
                SkillSubmissionConflict.MissingBaseForExisting,
                SkillSubmissionConflict.StaleBase,
                SkillSubmissionConflict.FutureBase {
    SkillId skillId();

    record BaseRevisionForNew(
            SkillId skillId,
            SkillRevision suppliedBase) implements SkillSubmissionConflict {
        public BaseRevisionForNew {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(suppliedBase, "suppliedBase");
        }
    }

    record MissingBaseForExisting(SkillReference latest) implements SkillSubmissionConflict {
        public MissingBaseForExisting {
            Objects.requireNonNull(latest, "latest");
        }

        @Override
        public SkillId skillId() {
            return latest.skillId();
        }
    }

    record StaleBase(
            SkillRevision suppliedBase,
            SkillReference latest) implements SkillSubmissionConflict {
        public StaleBase {
            Objects.requireNonNull(suppliedBase, "suppliedBase");
            Objects.requireNonNull(latest, "latest");
            if (suppliedBase.value() >= latest.revision().value()) {
                throw new IllegalArgumentException(
                        "stale base must be lower than the latest revision");
            }
        }

        @Override
        public SkillId skillId() {
            return latest.skillId();
        }
    }

    record FutureBase(
            SkillRevision suppliedBase,
            SkillReference latest) implements SkillSubmissionConflict {
        public FutureBase {
            Objects.requireNonNull(suppliedBase, "suppliedBase");
            Objects.requireNonNull(latest, "latest");
            if (suppliedBase.value() <= latest.revision().value()) {
                throw new IllegalArgumentException(
                        "future base must be greater than the latest revision");
            }
        }

        @Override
        public SkillId skillId() {
            return latest.skillId();
        }
    }
}
