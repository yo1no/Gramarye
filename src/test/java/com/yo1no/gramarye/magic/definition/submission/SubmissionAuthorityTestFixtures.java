package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class SubmissionAuthorityTestFixtures {
    static final SkillId SKILL_ID = id("123e4567-e89b-12d3-a456-426614174000");
    static final SkillId OTHER_SKILL_ID = id("a1a3f187-2552-49a5-b9e5-f22d4040c56b");
    static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("405c65d6-36b1-496f-8b22-3e34383a0435"));

    private SubmissionAuthorityTestFixtures() {
    }

    static SkillDraft draft(Optional<SkillRevision> baseRevision) {
        return draft(SKILL_ID, baseRevision);
    }

    static SkillDraft draft(SkillId skillId, Optional<SkillRevision> baseRevision) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                baseRevision,
                List.of(),
                AppearanceDocument.defaultAppearance());
    }

    static DraftSubmissionPrecheck.Ready precheck(
            SkillDraft draft,
            ValidationResult report) {
        return new DraftSubmissionPrecheck.Ready(
                SkillSubmissionInput.direct(draft), report);
    }

    static SkillSubmissionAuthorizationResult.Authorized authorizedNew(SkillId skillId) {
        return new SkillSubmissionAuthorizationResult.Authorized(
                OWNER, new AuthorizedSkillState.New(skillId));
    }

    static SkillSubmissionAuthorizationResult.Authorized authorizedExisting(
            SkillId skillId,
            int latestRevision) {
        return new SkillSubmissionAuthorizationResult.Authorized(
                OWNER,
                new AuthorizedSkillState.Existing(new SkillReference(
                        skillId, new SkillRevision(latestRevision))));
    }

    static ValidationResult warningReport(boolean truncated) {
        return new ValidationResult(List.of(issue(ValidationSeverity.WARNING)), truncated, false);
    }

    static ValidationResult errorReport() {
        return ValidationResult.of(issue(ValidationSeverity.ERROR));
    }

    static ValidationResult hiddenErrorReport() {
        return new ValidationResult(List.of(issue(ValidationSeverity.WARNING)), true, true);
    }

    private static ValidationIssue issue(ValidationSeverity severity) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath(
                        "gramarye", "submission.authority_fixture"),
                severity,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());
    }

    private static SkillId id(String value) {
        return new SkillId(UUID.fromString(value));
    }
}
