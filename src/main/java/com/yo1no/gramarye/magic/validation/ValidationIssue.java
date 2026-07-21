package com.yo1no.gramarye.magic.validation;

import java.util.Objects;

/** Immutable, bounded and machine-readable description of one validation finding. */
public record ValidationIssue(
        ValidationIssueCode code,
        ValidationSeverity severity,
        ValidationPath path,
        ValidationIssueMetadata metadata) {
    public ValidationIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(metadata, "metadata");
    }
}
