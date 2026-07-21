package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.validation.ValidationIssueCode;

/** Gramarye-owned failure codes emitted only by the inspector orchestration seam. */
public final class PayloadInspectionFailureCodes {
    public static final ValidationIssueCode INSPECTOR_EXCEPTION =
            ValidationIssueCode.fromNamespaceAndPath("gramarye", "descriptor.inspector_exception");

    public static final ValidationIssueCode INSPECTOR_CONTRACT_VIOLATION =
            ValidationIssueCode.fromNamespaceAndPath(
                    "gramarye", "descriptor.inspector_contract_violation");

    private PayloadInspectionFailureCodes() {
    }
}
