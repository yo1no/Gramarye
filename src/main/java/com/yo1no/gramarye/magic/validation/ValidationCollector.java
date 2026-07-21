package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Mutable single-run collector that emits an immutable bounded {@link ValidationResult}. */
public final class ValidationCollector {
    private final LinkedHashSet<ValidationIssue> retainedIssues = new LinkedHashSet<>();
    private boolean truncated;
    private boolean omittedError;

    public ValidationCollector add(ValidationIssue issue) {
        Objects.requireNonNull(issue, "issue");
        if (retainedIssues.contains(issue)) {
            return this;
        }
        if (retainedIssues.size() < MagicSafetyCeilings.MAX_VALIDATION_ISSUES) {
            retainedIssues.add(issue);
            return this;
        }

        truncated = true;
        if (issue.severity() == ValidationSeverity.ERROR) {
            omittedError = true;
        }
        return this;
    }

    public ValidationCollector add(ValidationResult result) {
        Objects.requireNonNull(result, "result");
        for (var issue : result.issues()) {
            add(issue);
        }
        truncated |= result.truncated();
        omittedError |= result.omittedError();
        return this;
    }

    public ValidationResult result() {
        if (retainedIssues.isEmpty() && !truncated && !omittedError) {
            return ValidationResult.valid();
        }
        return new ValidationResult(
                retainedIssues.stream().toList(),
                truncated,
                omittedError);
    }

    int retainedIdentityCount() {
        return retainedIssues.size();
    }
}
