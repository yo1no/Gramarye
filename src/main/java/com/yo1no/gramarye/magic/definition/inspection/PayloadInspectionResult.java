package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;

/** Complete result of a pure payload inspection; partial projections are not represented. */
public sealed interface PayloadInspectionResult<R>
        permits PayloadInspectionResult.Success, PayloadInspectionResult.Failure {
    record Success<R>(R projection) implements PayloadInspectionResult<R> {
        public Success {
            projection = InspectionContract.requireNonNull(projection, "projection");
        }
    }

    record Failure<R>(PayloadInspectionFailure failure) implements PayloadInspectionResult<R> {
        public Failure {
            failure = InspectionContract.requireNonNull(failure, "failure");
        }
    }
}

final class InspectionContract {
    private InspectionContract() {
    }

    static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new InspectionContractViolationException(name + " must not be null");
        }
        return value;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new InspectionContractViolationException(message);
        }
    }
}

final class InspectionContractViolationException extends IllegalArgumentException {
    private final ValidationIssueMetadata metadata;

    InspectionContractViolationException(String message) {
        super(message);
        this.metadata = ValidationIssueMetadata.none();
    }

    InspectionContractViolationException(ValidationIssueMetadata metadata) {
        this.metadata = InspectionContract.requireNonNull(metadata, "metadata");
    }

    ValidationIssueMetadata metadata() {
        return metadata;
    }
}
