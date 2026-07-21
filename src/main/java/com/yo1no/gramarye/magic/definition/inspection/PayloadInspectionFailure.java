package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;

/** Bounded machine-readable payload inspection failure without context-owned severity or path. */
public record PayloadInspectionFailure(
        ValidationIssueCode code,
        ValidationIssueMetadata metadata) {
    public PayloadInspectionFailure {
        code = InspectionContract.requireNonNull(code, "code");
        metadata = InspectionContract.requireNonNull(metadata, "metadata");
    }
}
