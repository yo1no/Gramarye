package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.validation.ValidationIssueCode;

/** Stable Gramarye-owned issue identities for Draft submission checks. */
public final class SkillSubmissionIssueCodes {
    public static final ValidationIssueCode DRAFT_UNSUPPORTED_SCHEMA =
            code("draft.unsupported_schema");
    public static final ValidationIssueCode DRAFT_TRIGGER_MISSING =
            code("draft.trigger_missing");
    public static final ValidationIssueCode DRAFT_ACTION_MISSING =
            code("draft.action_missing");

    private SkillSubmissionIssueCodes() {
    }

    private static ValidationIssueCode code(String path) {
        return ValidationIssueCode.fromNamespaceAndPath("gramarye", path);
    }
}
