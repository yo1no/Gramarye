package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable bounded aggregate of validation issues and truncation state. */
public record ValidationResult(
        List<ValidationIssue> issues,
        boolean truncated,
        boolean omittedError) {
    private static final ValidationResult VALID = new ValidationResult(List.of(), false, false);

    public ValidationResult {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        if (issues.size() > MagicSafetyCeilings.MAX_VALIDATION_ISSUES) {
            throw new IllegalArgumentException("issues exceeds the validation issue ceiling");
        }
        if (new HashSet<>(issues).size() != issues.size()) {
            throw new IllegalArgumentException("issues must not contain exact duplicates");
        }
        if (omittedError && !truncated) {
            throw new IllegalArgumentException("omittedError requires truncated");
        }
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult of(ValidationIssue issue) {
        return new ValidationResult(List.of(Objects.requireNonNull(issue, "issue")), false, false);
    }

    public boolean isValid() {
        return !hasErrors();
    }

    public boolean hasErrors() {
        return omittedError
                || issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    public List<ValidationIssue> errors() {
        return issues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.ERROR)
                .toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream()
                .filter(issue -> issue.severity() == ValidationSeverity.WARNING)
                .toList();
    }

    public ValidationResult merge(ValidationResult other) {
        return new ValidationCollector()
                .add(this)
                .add(Objects.requireNonNull(other, "other"))
                .result();
    }
}
