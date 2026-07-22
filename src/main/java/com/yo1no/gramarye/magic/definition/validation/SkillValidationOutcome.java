package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** Admission result at the boundary between validation analysis and validated projection. */
public sealed interface SkillValidationOutcome
        permits SkillValidationOutcome.Accepted, SkillValidationOutcome.Rejected {
    ValidationResult report();

    record Accepted(
            ValidatedSkillDefinition definition,
            ValidationResult report) implements SkillValidationOutcome {
        public Accepted {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(report, "report");
            if (report.hasErrors()) {
                throw new IllegalArgumentException("accepted outcome cannot contain errors");
            }
        }
    }

    record Rejected(ValidationResult report) implements SkillValidationOutcome {
        public Rejected {
            Objects.requireNonNull(report, "report");
            if (!report.hasErrors()) {
                throw new IllegalArgumentException("rejected outcome must contain an error");
            }
        }
    }
}
