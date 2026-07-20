package com.yo1no.gramarye.magic.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record ValidationResult(List<ValidationIssue> issues) {
    private static final ValidationResult VALID = new ValidationResult(List.of());

    public ValidationResult {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
    }

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult of(ValidationIssue issue) {
        return new ValidationResult(List.of(Objects.requireNonNull(issue, "issue")));
    }

    public boolean isValid() {
        return !hasErrors();
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
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
        Objects.requireNonNull(other, "other");
        if (issues.isEmpty()) {
            return other;
        }
        if (other.issues.isEmpty()) {
            return this;
        }

        var mergedIssues = new ArrayList<ValidationIssue>(issues.size() + other.issues.size());
        mergedIssues.addAll(issues);
        mergedIssues.addAll(other.issues);
        return new ValidationResult(mergedIssues);
    }
}
