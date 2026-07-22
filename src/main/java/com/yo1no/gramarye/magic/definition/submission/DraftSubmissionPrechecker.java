package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.Objects;

/** Maps Draft read provenance and admits only the current Draft schema. */
final class DraftSubmissionPrechecker {
    DraftSubmissionPrecheck check(SkillSubmissionInput input) {
        Objects.requireNonNull(input, "input");
        var collector = new ValidationCollector();
        DraftReadFactMapper.append(input, collector);

        var actualSchema = input.draft().draftSchemaVersion();
        var expectedSchema = SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION;
        if (actualSchema != expectedSchema) {
            collector.add(new ValidationIssue(
                    SkillSubmissionIssueCodes.DRAFT_UNSUPPORTED_SCHEMA,
                    ValidationSeverity.ERROR,
                    DraftSubmissionPaths.draftSchemaVersion(),
                    new ValidationIssueMetadata.Schema(actualSchema, expectedSchema)));
        }

        var report = collector.result();
        return report.hasErrors()
                ? new DraftSubmissionPrecheck.Invalid(report)
                : new DraftSubmissionPrecheck.Ready(input, report);
    }
}
