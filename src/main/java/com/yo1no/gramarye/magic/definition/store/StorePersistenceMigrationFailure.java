package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

record StorePersistenceMigrationFailure(
        Code code,
        OptionalInt fromVersion,
        OptionalInt toVersion,
        OptionalInt observedVersion,
        OptionalInt stepIndex,
        Optional<String> exceptionClassName) {
    StorePersistenceMigrationFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(fromVersion, "fromVersion");
        Objects.requireNonNull(toVersion, "toVersion");
        Objects.requireNonNull(observedVersion, "observedVersion");
        Objects.requireNonNull(stepIndex, "stepIndex");
        exceptionClassName = Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        for (var value : new OptionalInt[] {fromVersion, toVersion, observedVersion, stepIndex}) {
            if (value.isPresent() && value.getAsInt() < 0) {
                throw new IllegalArgumentException("migration metadata must be non-negative");
            }
        }
        exceptionClassName.ifPresent(name -> {
            if (name.isBlank() || name.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("exception class name is invalid");
            }
        });
    }

    static StorePersistenceMigrationFailure simple(Code code) {
        return new StorePersistenceMigrationFailure(
                code, OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.empty(), Optional.empty());
    }

    static StorePersistenceMigrationFailure observed(Code code, int version) {
        return new StorePersistenceMigrationFailure(
                code, OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(version),
                OptionalInt.empty(), Optional.empty());
    }

    static StorePersistenceMigrationFailure step(Code code, int from, int index) {
        return new StorePersistenceMigrationFailure(
                code, OptionalInt.of(from), OptionalInt.of(from + 1), OptionalInt.empty(),
                OptionalInt.of(index), Optional.empty());
    }

    static StorePersistenceMigrationFailure stepVersion(
            Code code, int from, int index, int observed) {
        return new StorePersistenceMigrationFailure(
                code, OptionalInt.of(from), OptionalInt.of(from + 1), OptionalInt.of(observed),
                OptionalInt.of(index), Optional.empty());
    }

    static StorePersistenceMigrationFailure stepException(
            int from, int index, RuntimeException exception) {
        var name = exception.getClass().getName();
        var bounded = name.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? name : name.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
        return new StorePersistenceMigrationFailure(
                Code.STEP_THREW_EXCEPTION,
                OptionalInt.of(from), OptionalInt.of(from + 1), OptionalInt.empty(),
                OptionalInt.of(index), Optional.of(bounded));
    }

    enum Code {
        INVALID_ROOT,
        MISSING_SCHEMA_VERSION,
        INVALID_SCHEMA_VERSION,
        FUTURE_SCHEMA_VERSION,
        MISSING_MIGRATION_EDGE,
        STEP_RETURNED_PARTIAL,
        STEP_FAILED,
        STEP_THREW_EXCEPTION,
        STEP_CHANGED_DYNAMIC_OPS,
        STEP_OUTPUT_VERSION_MISMATCH
    }
}
