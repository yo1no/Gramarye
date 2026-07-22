package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** Stage token separating Draft schema admission from later submission checks. */
sealed interface DraftSubmissionPrecheck
        permits DraftSubmissionPrecheck.Ready, DraftSubmissionPrecheck.Invalid {
    ValidationResult report();

    record Ready(
            SkillSubmissionInput input,
            ValidationResult report) implements DraftSubmissionPrecheck {
        public Ready {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(report, "report");
            if (report.hasErrors()) {
                throw new IllegalArgumentException("Ready precheck cannot contain an error");
            }
            if (input.draft().draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("Ready precheck requires the current Draft schema");
            }
        }
    }

    record Invalid(ValidationResult report) implements DraftSubmissionPrecheck {
        public Invalid {
            Objects.requireNonNull(report, "report");
            if (!report.hasErrors()) {
                throw new IllegalArgumentException("Invalid precheck requires an error");
            }
        }
    }
}
