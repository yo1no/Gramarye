package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** A deterministic revision proposal that has not been allocated or committed. */
sealed interface SubmissionRevisionProposal
        permits SubmissionRevisionProposal.Proposed, SubmissionRevisionProposal.Exhausted {
    record Proposed(SkillRevision revision) implements SubmissionRevisionProposal {
        public Proposed {
            Objects.requireNonNull(revision, "revision");
        }
    }

    record Exhausted(SkillReference latest) implements SubmissionRevisionProposal {
        public Exhausted {
            Objects.requireNonNull(latest, "latest");
            if (latest.revision().value() != Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "exhausted proposal requires the maximum revision");
            }
        }
    }
}
