package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;
import java.util.Optional;

/** Package-internal total result for the P4-A document persistence boundary. */
sealed interface SkillDocumentPersistenceResult<T>
        permits SkillDocumentPersistenceResult.Success, SkillDocumentPersistenceResult.Failure {
    Optional<T> successValue();

    Optional<SkillDocumentPersistenceFailure> failureValue();

    static <T> SkillDocumentPersistenceResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> SkillDocumentPersistenceResult<T> failure(
            SkillDocumentPersistenceFailure failure) {
        return new Failure<>(failure);
    }

    record Success<T>(T value) implements SkillDocumentPersistenceResult<T> {
        public Success {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public Optional<T> successValue() {
            return Optional.of(value);
        }

        @Override
        public Optional<SkillDocumentPersistenceFailure> failureValue() {
            return Optional.empty();
        }
    }

    record Failure<T>(SkillDocumentPersistenceFailure failure)
            implements SkillDocumentPersistenceResult<T> {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }

        @Override
        public Optional<T> successValue() {
            return Optional.empty();
        }

        @Override
        public Optional<SkillDocumentPersistenceFailure> failureValue() {
            return Optional.of(failure);
        }
    }
}
