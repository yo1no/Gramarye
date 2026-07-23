package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmissionPreparationCheckTest {
    @Test
    void preparedAcceptsValidNewAndExistingProposals() {
        var newPrepared = preparedNew(1, AppearanceDocument.defaultAppearance());
        var existingPrepared = preparedExisting(4, 1);

        assertAll(
                () -> assertEquals(new SkillRevision(0),
                        newPrepared.proposedDocument().revision()),
                () -> assertEquals(new SkillRevision(5),
                        existingPrepared.proposedDocument().revision()),
                () -> assertEquals(
                        newPrepared.validatedDefinition().reference(),
                        new com.yo1no.gramarye.magic.definition.document.SkillReference(
                                newPrepared.proposedDocument().skillId(),
                                newPrepared.proposedDocument().revision())),
                () -> assertEquals(
                        existingPrepared.proposedDocument().nodes().size(),
                        existingPrepared.validatedDefinition().nodes().size()),
                () -> assertEquals(
                        List.of(0),
                        existingPrepared.validatedDefinition().nodes().stream()
                                .map(node -> node.nodeIndex())
                                .toList()));
    }

    @Test
    void preparedAllowsWarningOnlyTruncation() {
        var report = warningReport(true);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var precheck = new DraftSubmissionPrecheck.Ready(
                SkillSubmissionInput.direct(draft), report);
        var authority = new SubmissionAuthorityCheck.Passed(
                precheck,
                new SkillSubmissionAuthorizationResult.Authorized(
                        SubmissionPreparationTestFixtures.OWNER,
                        new AuthorizedSkillState.New(draft.skillId())));
        var prepared = (SubmissionPreparationCheck.Prepared)
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT);

        assertAll(
                () -> assertTrue(prepared.report().truncated()),
                () -> assertFalse(prepared.report().omittedError()),
                () -> assertFalse(prepared.report().hasErrors()));
    }

    @Test
    void preparedRejectsIdentityReferenceAndNodeCountMismatches() {
        var oneNode = preparedNew(1, AppearanceDocument.defaultAppearance());
        var twoNodes = preparedNew(2, AppearanceDocument.defaultAppearance());
        var otherSkill = preparedFor(
                SubmissionPreparationTestFixtures.OTHER_SKILL_ID,
                Optional.empty(),
                1);
        var wrongIdDocument = new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                SubmissionPreparationTestFixtures.OTHER_SKILL_ID,
                oneNode.proposedDocument().revision(),
                oneNode.proposedDocument().nodes(),
                oneNode.proposedDocument().appearance());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                oneNode.authority(),
                                wrongIdDocument,
                                oneNode.validatedDefinition(),
                                oneNode.report())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                oneNode.authority(),
                                oneNode.proposedDocument(),
                                otherSkill.validatedDefinition(),
                                oneNode.report())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                oneNode.authority(),
                                oneNode.proposedDocument(),
                                twoNodes.validatedDefinition(),
                                oneNode.report())));
    }

    @Test
    void preparedRejectsWrongRevisionSchemaEmptyDocumentAndErrorReport() {
        var newPrepared = preparedNew(1, AppearanceDocument.defaultAppearance());
        var revisionOne = preparedExisting(0, 1);
        var maximumDraft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(Integer.MAX_VALUE)));
        var maximumAuthority = SubmissionPreparationTestFixtures.passedExisting(
                SkillSubmissionInput.direct(maximumDraft), Integer.MAX_VALUE);
        var wrongSchema = copyDocument(newPrepared.proposedDocument(), 1,
                newPrepared.proposedDocument().nodes());
        var emptyDocument = copyDocument(
                newPrepared.proposedDocument(), SkillDocument.CURRENT_SCHEMA_VERSION, List.of());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                newPrepared.authority(),
                                revisionOne.proposedDocument(),
                                revisionOne.validatedDefinition(),
                                revisionOne.report())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                newPrepared.authority(),
                                wrongSchema,
                                newPrepared.validatedDefinition(),
                                newPrepared.report())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                newPrepared.authority(),
                                emptyDocument,
                                newPrepared.validatedDefinition(),
                                newPrepared.report())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                newPrepared.authority(),
                                newPrepared.proposedDocument(),
                                newPrepared.validatedDefinition(),
                                errorReport())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                maximumAuthority,
                                newPrepared.proposedDocument(),
                                newPrepared.validatedDefinition(),
                                newPrepared.report())));
    }

    @Test
    void preparedRejectsNullBoundaries() {
        var prepared = preparedNew(1, AppearanceDocument.defaultAppearance());

        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                null,
                                prepared.proposedDocument(),
                                prepared.validatedDefinition(),
                                prepared.report())),
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                prepared.authority(),
                                null,
                                prepared.validatedDefinition(),
                                prepared.report())),
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                prepared.authority(),
                                prepared.proposedDocument(),
                                null,
                                prepared.report())),
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.Prepared(
                                prepared.authority(),
                                prepared.proposedDocument(),
                                prepared.validatedDefinition(),
                                null)));
    }

    @Test
    void invalidStoresOnlyAnErrorBearingReport() {
        var retained = errorReport();
        var hidden = new ValidationResult(List.of(warning()), true, true);

        var retainedInvalid = new SubmissionPreparationCheck.Invalid(retained);
        var hiddenInvalid = new SubmissionPreparationCheck.Invalid(hidden);

        assertAll(
                () -> assertSame(retained, retainedInvalid.report()),
                () -> assertSame(hidden, hiddenInvalid.report()),
                () -> assertEquals(List.of("report"),
                        Arrays.stream(SubmissionPreparationCheck.Invalid.class.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Invalid(ValidationResult.valid())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.Invalid(warningReport(true))),
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.Invalid(null)));
    }

    @Test
    void revisionExhaustedDerivesLatestAndReportFromAuthority() {
        var warningReport = warningReport(true);
        var draft = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(Integer.MAX_VALUE)));
        var precheck = new DraftSubmissionPrecheck.Ready(
                SkillSubmissionInput.direct(draft), warningReport);
        var authority = new SubmissionAuthorityCheck.Passed(
                precheck,
                new SkillSubmissionAuthorizationResult.Authorized(
                        SubmissionPreparationTestFixtures.OWNER,
                        new AuthorizedSkillState.Existing(
                                new com.yo1no.gramarye.magic.definition.document.SkillReference(
                                        draft.skillId(),
                                        new SkillRevision(Integer.MAX_VALUE)))));

        var exhausted = new SubmissionPreparationCheck.RevisionExhausted(authority);

        assertAll(
                () -> assertEquals(new SkillRevision(Integer.MAX_VALUE),
                        exhausted.latest().revision()),
                () -> assertSame(warningReport, exhausted.report()),
                () -> assertSame(authority, exhausted.authority()));
    }

    @Test
    void revisionExhaustedRejectsNewAndNonMaximumExistingStates() {
        var newAuthority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(SubmissionPreparationTestFixtures.emptyDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty())));
        var existingAuthority = SubmissionPreparationTestFixtures.passedExisting(
                SkillSubmissionInput.direct(SubmissionPreparationTestFixtures.emptyDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID,
                        Optional.of(new SkillRevision(3)))),
                3);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.RevisionExhausted(newAuthority)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionPreparationCheck.RevisionExhausted(existingAuthority)),
                () -> assertThrows(NullPointerException.class, () ->
                        new SubmissionPreparationCheck.RevisionExhausted(null)));
    }

    private static SubmissionPreparationCheck.Prepared preparedNew(
            int nodes,
            AppearanceDocument appearance) {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                nodes,
                appearance);
        return prepare(SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft)));
    }

    private static SubmissionPreparationCheck.Prepared preparedExisting(
            int latestRevision,
            int nodes) {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(latestRevision)),
                nodes,
                AppearanceDocument.defaultAppearance());
        return prepare(SubmissionPreparationTestFixtures.passedExisting(
                SkillSubmissionInput.direct(draft), latestRevision));
    }

    private static SubmissionPreparationCheck.Prepared preparedFor(
            com.yo1no.gramarye.magic.api.id.SkillId skillId,
            Optional<SkillRevision> baseRevision,
            int nodes) {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                skillId, baseRevision, nodes, AppearanceDocument.defaultAppearance());
        return prepare(SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft)));
    }

    private static SubmissionPreparationCheck.Prepared prepare(
            SubmissionAuthorityCheck.Passed authority) {
        return (SubmissionPreparationCheck.Prepared)
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT);
    }

    private static SkillDocument copyDocument(
            SkillDocument source,
            int schemaVersion,
            List<com.yo1no.gramarye.magic.definition.document.NodeDocument> nodes) {
        return new SkillDocument(
                schemaVersion,
                source.skillId(),
                source.revision(),
                nodes,
                source.appearance());
    }

    private static ValidationResult warningReport(boolean truncated) {
        return new ValidationResult(List.of(warning()), truncated, false);
    }

    private static ValidationResult errorReport() {
        return ValidationResult.of(new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "submission.test_error"),
                ValidationSeverity.ERROR,
                ValidationPath.empty(),
                ValidationIssueMetadata.none()));
    }

    private static ValidationIssue warning() {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "submission.test_warning"),
                ValidationSeverity.WARNING,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());
    }
}
