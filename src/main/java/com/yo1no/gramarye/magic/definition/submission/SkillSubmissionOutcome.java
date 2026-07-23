package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/**
 * Public result of skill submission preparation.
 *
 * <p>{@link Prepared} means only that a transient plan may be passed to the future P3-D commit
 * boundary. It does not mean committed, stored, formally allocated, quota-admitted, or
 * runtime-visible. These variants are data-transfer results, not capability tokens.
 */
public sealed interface SkillSubmissionOutcome
        permits SkillSubmissionOutcome.Prepared,
                SkillSubmissionOutcome.Invalid,
                SkillSubmissionOutcome.Conflict,
                SkillSubmissionOutcome.IdentityRejected,
                SkillSubmissionOutcome.RevisionExhausted {
    ValidationResult report();

    /** A warning-only preparation result; the plan has not been committed. */
    record Prepared(
            SkillSubmissionPlan plan,
            ValidationResult report) implements SkillSubmissionOutcome {
        public Prepared {
            Objects.requireNonNull(plan, "plan");
            report = requireWarningOnly(report, "prepared");
        }
    }

    /** Data-invalid preparation with no partial plan or proposed definition. */
    record Invalid(ValidationResult report) implements SkillSubmissionOutcome {
        public Invalid {
            Objects.requireNonNull(report, "report");
            if (!report.hasErrors()) {
                throw new IllegalArgumentException("invalid outcome requires an error");
            }
        }
    }

    /** Authorized optimistic-concurrency conflict. */
    record Conflict(
            SkillSubmissionConflict conflict,
            ValidationResult report) implements SkillSubmissionOutcome {
        public Conflict {
            Objects.requireNonNull(conflict, "conflict");
            report = requireWarningOnly(report, "conflict");
        }
    }

    /** Opaque authorization or admission rejection with no existence or ownership details. */
    record IdentityRejected(
            SkillSubmissionAuthorizationResult.Rejected rejection,
            ValidationResult report) implements SkillSubmissionOutcome {
        public IdentityRejected {
            Objects.requireNonNull(rejection, "rejection");
            report = requireWarningOnly(report, "identity-rejected");
        }

        public SkillId skillId() {
            return rejection.skillId();
        }

        public SkillIdentityRejectionCode reason() {
            return rejection.reason();
        }
    }

    /** Accepted authority whose latest Store revision cannot have a successor. */
    record RevisionExhausted(
            SkillReference latest,
            ValidationResult report) implements SkillSubmissionOutcome {
        public RevisionExhausted {
            Objects.requireNonNull(latest, "latest");
            if (latest.revision().value() != Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "revision-exhausted outcome requires the maximum revision");
            }
            report = requireWarningOnly(report, "revision-exhausted");
        }
    }

    private static ValidationResult requireWarningOnly(
            ValidationResult report,
            String outcomeName) {
        Objects.requireNonNull(report, "report");
        if (report.hasErrors()) {
            throw new IllegalArgumentException(outcomeName + " outcome cannot contain an error");
        }
        return report;
    }
}
