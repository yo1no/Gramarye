package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Machine-readable, non-persistent failure from the skill-level migration boundary. */
public record SkillMigrationFailure(
        Code code,
        OptionalInt fromVersion,
        OptionalInt toVersion,
        OptionalLong observedVersion,
        OptionalInt stepIndex,
        Optional<String> exceptionClassName) {
    public SkillMigrationFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fromVersion, "fromVersion");
        Objects.requireNonNull(toVersion, "toVersion");
        Objects.requireNonNull(observedVersion, "observedVersion");
        Objects.requireNonNull(stepIndex, "stepIndex");
        exceptionClassName = Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        if (fromVersion.isPresent() && fromVersion.getAsInt() < 0) {
            throw new IllegalArgumentException("fromVersion must be non-negative");
        }
        if (toVersion.isPresent() && toVersion.getAsInt() < 0) {
            throw new IllegalArgumentException("toVersion must be non-negative");
        }
        if (stepIndex.isPresent() && stepIndex.getAsInt() < 0) {
            throw new IllegalArgumentException("stepIndex must be non-negative");
        }
        exceptionClassName.ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("exceptionClassName must not be blank");
            }
            if (name.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("exceptionClassName exceeds the technical string ceiling");
            }
        });
    }

    public static SkillMigrationFailure of(Code code) {
        return new SkillMigrationFailure(
                code, OptionalInt.empty(), OptionalInt.empty(), OptionalLong.empty(), OptionalInt.empty(), Optional.empty());
    }

    static SkillMigrationFailure forObserved(Code code, long observedVersion) {
        return new SkillMigrationFailure(
                code,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalLong.of(observedVersion),
                OptionalInt.empty(),
                Optional.empty());
    }

    static SkillMigrationFailure forStep(Code code, int fromVersion, int toVersion, int stepIndex) {
        return new SkillMigrationFailure(
                code,
                OptionalInt.of(fromVersion),
                OptionalInt.of(toVersion),
                OptionalLong.empty(),
                OptionalInt.of(stepIndex),
                Optional.empty());
    }

    static SkillMigrationFailure forStepVersion(
            Code code,
            int fromVersion,
            int toVersion,
            int stepIndex,
            long observedVersion) {
        return new SkillMigrationFailure(
                code,
                OptionalInt.of(fromVersion),
                OptionalInt.of(toVersion),
                OptionalLong.of(observedVersion),
                OptionalInt.of(stepIndex),
                Optional.empty());
    }

    static SkillMigrationFailure forStepException(
            int fromVersion,
            int toVersion,
            int stepIndex,
            RuntimeException exception) {
        var className = exception.getClass().getName();
        var boundedClassName = className.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? className
                : className.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
        return new SkillMigrationFailure(
                Code.STEP_THREW_EXCEPTION,
                OptionalInt.of(fromVersion),
                OptionalInt.of(toVersion),
                OptionalLong.empty(),
                OptionalInt.of(stepIndex),
                Optional.of(boundedClassName));
    }

    static SkillMigrationFailure forSnapshotException(RuntimeException exception) {
        var className = exception.getClass().getName();
        var boundedClassName = className.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? className
                : className.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
        return new SkillMigrationFailure(
                Code.RAW_SNAPSHOT_EXCEPTION,
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.of(boundedClassName));
    }

    public enum Code {
        UNSUPPORTED_RAW_FAMILY,
        INVALID_ROOT,
        MISSING_SCHEMA_VERSION,
        INVALID_SCHEMA_VERSION,
        FUTURE_SCHEMA_VERSION,
        MISSING_MIGRATION_EDGE,
        STEP_RETURNED_PARTIAL,
        STEP_FAILED,
        STEP_THREW_EXCEPTION,
        STEP_CHANGED_DYNAMIC_OPS,
        STEP_OUTPUT_VERSION_MISMATCH,
        GLOBAL_DEPTH_EXCEEDED,
        GLOBAL_TREE_NODE_LIMIT_EXCEEDED,
        GLOBAL_KEY_LENGTH_EXCEEDED,
        RAW_SNAPSHOT_EXCEPTION
    }
}
