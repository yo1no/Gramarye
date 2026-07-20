package com.yo1no.gramarye.magic.validation;

import java.util.Objects;

public record ValidationIssue(
        String code,
        ValidationSeverity severity,
        String path,
        String message) {
    public ValidationIssue {
        requireNonBlank(code, "code");
        Objects.requireNonNull(severity, "severity");
        requireNonBlank(path, "path");
        requireNonBlank(message, "message");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
