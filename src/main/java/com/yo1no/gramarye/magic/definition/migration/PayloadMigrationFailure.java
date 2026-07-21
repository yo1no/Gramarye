package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Machine-readable, non-persistent payload migration failure with bounded metadata. */
public record PayloadMigrationFailure(
        Code code,
        OptionalInt fromVersion,
        OptionalInt toVersion,
        OptionalInt observedVersion,
        OptionalInt stepIndex,
        Optional<String> exceptionClassName) {
    public PayloadMigrationFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fromVersion, "fromVersion");
        Objects.requireNonNull(toVersion, "toVersion");
        Objects.requireNonNull(observedVersion, "observedVersion");
        Objects.requireNonNull(stepIndex, "stepIndex");
        exceptionClassName = Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        requireNonNegative(fromVersion, "fromVersion");
        requireNonNegative(toVersion, "toVersion");
        requireNonNegative(observedVersion, "observedVersion");
        requireNonNegative(stepIndex, "stepIndex");
        exceptionClassName.ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("exceptionClassName must not be blank");
            }
            if (name.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException(
                        "exceptionClassName exceeds the technical string ceiling");
            }
        });
    }

    static PayloadMigrationFailure future(int observedVersion) {
        return new PayloadMigrationFailure(
                Code.FUTURE_SCHEMA_VERSION,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(observedVersion),
                OptionalInt.empty(),
                Optional.empty());
    }

    static PayloadMigrationFailure forStep(
            Code code,
            int fromVersion,
            int toVersion,
            int stepIndex) {
        return new PayloadMigrationFailure(
                code,
                OptionalInt.of(fromVersion),
                OptionalInt.of(toVersion),
                OptionalInt.empty(),
                OptionalInt.of(stepIndex),
                Optional.empty());
    }

    static PayloadMigrationFailure forStepException(
            int fromVersion,
            int toVersion,
            int stepIndex,
            RuntimeException exception) {
        var className = exception.getClass().getName();
        var boundedClassName = className.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? className
                : className.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
        return new PayloadMigrationFailure(
                Code.STEP_THREW_EXCEPTION,
                OptionalInt.of(fromVersion),
                OptionalInt.of(toVersion),
                OptionalInt.empty(),
                OptionalInt.of(stepIndex),
                Optional.of(boundedClassName));
    }

    private static void requireNonNegative(OptionalInt value, String name) {
        if (value.isPresent() && value.getAsInt() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    public enum Code {
        FUTURE_SCHEMA_VERSION,
        MISSING_MIGRATION_EDGE,
        STEP_FAILED,
        STEP_RETURNED_PARTIAL,
        STEP_THREW_EXCEPTION,
        STEP_CHANGED_DYNAMIC_OPS,
        PAYLOAD_TREE_DEPTH_EXCEEDED,
        PAYLOAD_TREE_NODE_LIMIT_EXCEEDED,
        PAYLOAD_KEY_LENGTH_EXCEEDED
    }
}
