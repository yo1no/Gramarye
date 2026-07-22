package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.Objects;

/** Maps bounded Draft Reader facts into submission warnings without retaining raw data. */
final class DraftReadFactMapper {
    private DraftReadFactMapper() {
    }

    static void append(SkillSubmissionInput input, ValidationCollector collector) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(collector, "collector");
        for (var fact : input.readReport().facts()) {
            collector.add(new ValidationIssue(
                    issueCode(fact.code()),
                    ValidationSeverity.WARNING,
                    path(input, fact),
                    ValidationIssueMetadata.none()));
        }
        if (input.readReport().truncated()) {
            collector.add(new ValidationIssue(
                    SkillValidationIssueCodes.READ_REPORT_TRUNCATED,
                    ValidationSeverity.WARNING,
                    DraftSubmissionPaths.root(),
                    ValidationIssueMetadata.none()));
        }
    }

    private static ValidationIssueCode issueCode(ReadFactCode code) {
        return switch (code) {
            case INTENSITY_CLAMPED_LOW ->
                    SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_LOW;
            case INTENSITY_CLAMPED_HIGH ->
                    SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH;
            case LEGACY_NULL_PROFILE_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_PROFILE_NORMALIZED;
            case LEGACY_NULL_SCALAR_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_SCALAR_NORMALIZED;
            case LEGACY_NULL_APPEARANCE_DEFAULTED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_APPEARANCE_DEFAULTED;
            case LEGACY_NULL_OVERRIDE_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_OVERRIDE_NORMALIZED;
            case UNKNOWN_APPEARANCE_FIELD_IGNORED ->
                    SkillValidationIssueCodes.READ_UNKNOWN_APPEARANCE_FIELD_IGNORED;
        };
    }

    private static ValidationPath path(SkillSubmissionInput input, ReadFact fact) {
        var base = switch (fact.locationKind()) {
            case DRAFT_APPEARANCE -> {
                if (fact.nodeIndex().isPresent()) {
                    throw new IllegalStateException(
                            "Draft appearance read fact must not have a node index");
                }
                yield DraftSubmissionPaths.appearance();
            }
            case DRAFT_NODE_APPEARANCE_OVERRIDE -> {
                if (fact.nodeIndex().isEmpty()) {
                    throw new IllegalStateException(
                            "Draft node appearance read fact requires a node index");
                }
                var nodeIndex = fact.nodeIndex().getAsInt();
                if (nodeIndex >= input.draft().nodes().size()) {
                    throw new IllegalStateException(
                            "Draft node appearance read fact index is outside the Draft");
                }
                yield DraftSubmissionPaths.appearanceOverride(nodeIndex);
            }
            case SKILL_APPEARANCE, SKILL_NODE_APPEARANCE_OVERRIDE ->
                    throw new IllegalStateException(
                            "SkillDocument read fact cannot appear in a SkillDraft report");
        };
        return fact.field()
                .map(field -> DraftSubmissionPaths.appearanceField(base, field))
                .orElse(base);
    }
}
