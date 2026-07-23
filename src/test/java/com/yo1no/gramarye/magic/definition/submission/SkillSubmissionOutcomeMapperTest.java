package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SkillSubmissionOutcomeMapperTest {
    @Test
    void c1SchemaInvalidPreservesItsReportAndIssueProvenance() {
        var draft = SubmissionTestFixtures.draft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION + 1,
                List.of(SubmissionTestFixtures.completeNode()));
        var invalid = assertInstanceOf(
                DraftSubmissionPrecheck.Invalid.class,
                new DraftSubmissionPrechecker().check(inputWithReadWarning(draft)));

        var outcome = SkillSubmissionOutcomeMapper.from(invalid);
        var repeated = SkillSubmissionOutcomeMapper.from(invalid);

        assertAll(
                () -> assertSame(invalid.report(), outcome.report()),
                () -> assertSame(invalid.report(), repeated.report()),
                () -> assertEquals(outcome.getClass(), repeated.getClass()),
                () -> assertEquals(List.of(
                                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                                SkillSubmissionIssueCodes.DRAFT_UNSUPPORTED_SCHEMA),
                        outcome.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertEquals(List.of(
                                "appearance.intensity_milli",
                                "draft_schema_version"),
                        outcome.report().issues().stream()
                                .map(issue -> issue.path().render())
                                .toList()));
    }

    @Test
    void authorityRejectionsAndConflictPreserveDomainAndReportIdentity() {
        var checker = new SubmissionAuthorityChecker();
        var rejectionReport = SubmissionAuthorityTestFixtures.warningReport(true);
        var rejectionPrecheck = SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(Optional.empty()), rejectionReport);
        var notAuthorized = rejection(
                SkillIdentityRejectionCode.NOT_AUTHORIZED);
        var quotaExceeded = rejection(
                SkillIdentityRejectionCode.QUOTA_EXCEEDED);
        var rejectedStage = assertInstanceOf(
                SubmissionAuthorityCheck.IdentityRejected.class,
                checker.check(rejectionPrecheck, notAuthorized));
        var quotaStage = assertInstanceOf(
                SubmissionAuthorityCheck.IdentityRejected.class,
                checker.check(rejectionPrecheck, quotaExceeded));

        var base = new SkillRevision(3);
        var conflictReport = SubmissionAuthorityTestFixtures.warningReport(false);
        var conflictPrecheck = SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(Optional.of(base)), conflictReport);
        var conflictStage = assertInstanceOf(
                SubmissionAuthorityCheck.Conflict.class,
                checker.check(
                        conflictPrecheck,
                        SubmissionAuthorityTestFixtures.authorizedNew(
                                SubmissionAuthorityTestFixtures.SKILL_ID)));

        var rejected = SkillSubmissionOutcomeMapper.from(rejectedStage);
        var rejectedAgain = SkillSubmissionOutcomeMapper.from(rejectedStage);
        var quota = SkillSubmissionOutcomeMapper.from(quotaStage);
        var quotaAgain = SkillSubmissionOutcomeMapper.from(quotaStage);
        var conflict = SkillSubmissionOutcomeMapper.from(conflictStage);
        var conflictAgain = SkillSubmissionOutcomeMapper.from(conflictStage);

        assertAll(
                () -> assertSame(notAuthorized, rejected.rejection()),
                () -> assertSame(notAuthorized, rejectedAgain.rejection()),
                () -> assertSame(rejectionReport, rejected.report()),
                () -> assertSame(rejectionReport, rejectedAgain.report()),
                () -> assertSame(notAuthorized.skillId(), rejected.skillId()),
                () -> assertEquals(SkillIdentityRejectionCode.NOT_AUTHORIZED,
                        rejected.reason()),
                () -> assertSame(quotaExceeded, quota.rejection()),
                () -> assertSame(quotaExceeded, quotaAgain.rejection()),
                () -> assertSame(rejectionReport, quota.report()),
                () -> assertSame(rejectionReport, quotaAgain.report()),
                () -> assertEquals(SkillIdentityRejectionCode.QUOTA_EXCEEDED,
                        quota.reason()),
                () -> assertSame(conflictStage.conflict(), conflict.conflict()),
                () -> assertSame(conflictStage.conflict(), conflictAgain.conflict()),
                () -> assertSame(conflictReport, conflict.report()),
                () -> assertSame(conflictReport, conflictAgain.report()));
    }

    @Test
    void preparedMappingIsDeterministicAndDoesNotRerunAnyPreparationStage() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var preparer = new SkillSubmissionPreparer(stages);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                inputWithReadWarning(draft));
        var prepared = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));
        assertCounts(stages, 1, 1, 1, 1);

        var first = assertInstanceOf(
                SkillSubmissionOutcome.Prepared.class,
                SkillSubmissionOutcomeMapper.from(prepared));
        var second = assertInstanceOf(
                SkillSubmissionOutcome.Prepared.class,
                SkillSubmissionOutcomeMapper.from(prepared));

        assertAll(
                () -> assertSame(prepared.report(), first.report()),
                () -> assertSame(prepared.report(), second.report()),
                () -> assertSame(prepared.proposedDocument(), first.plan().proposedDocument()),
                () -> assertSame(prepared.proposedDocument(), second.plan().proposedDocument()),
                () -> assertSame(
                        prepared.validatedDefinition(), first.plan().validatedDefinition()),
                () -> assertSame(
                        prepared.validatedDefinition(), second.plan().validatedDefinition()),
                () -> assertSame(authority.authorization().owner(), first.plan().owner()),
                () -> assertSame(authority.authorization().owner(), second.plan().owner()),
                () -> assertEquals(first.plan().precondition(), second.plan().precondition()),
                () -> assertNotSame(first.plan(), second.plan()),
                () -> assertCounts(stages, 1, 1, 1, 1));
    }

    @Test
    void revisionExhaustedMappingPreservesLatestAndReportWithoutRunningStages() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var draft = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(Integer.MAX_VALUE)));
        var authority = SubmissionPreparationTestFixtures.passedExisting(
                inputWithReadWarning(draft), Integer.MAX_VALUE);
        var exhausted = assertInstanceOf(
                SubmissionPreparationCheck.RevisionExhausted.class,
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
        assertCounts(stages, 0, 0, 0, 0);

        var first = assertInstanceOf(
                SkillSubmissionOutcome.RevisionExhausted.class,
                SkillSubmissionOutcomeMapper.from(exhausted));
        var second = assertInstanceOf(
                SkillSubmissionOutcome.RevisionExhausted.class,
                SkillSubmissionOutcomeMapper.from(exhausted));

        assertAll(
                () -> assertSame(exhausted.latest(), first.latest()),
                () -> assertSame(exhausted.latest(), second.latest()),
                () -> assertSame(exhausted.report(), first.report()),
                () -> assertSame(exhausted.report(), second.report()),
                () -> assertEquals(first, second),
                () -> assertCounts(stages, 0, 0, 0, 0));
    }

    @Test
    void c3FormalizationInvalidPreservesWarningsAndCompletenessErrorWithoutRerun() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var draft = SubmissionPreparationTestFixtures.draft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(SubmissionTestFixtures.missingTrigger()),
                AppearanceDocument.defaultAppearance());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                inputWithReadWarning(draft));
        var invalid = assertInstanceOf(
                SubmissionPreparationCheck.Invalid.class,
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
        assertCounts(stages, 1, 0, 0, 0);

        var outcome = assertInstanceOf(
                SkillSubmissionOutcome.Invalid.class,
                SkillSubmissionOutcomeMapper.from(invalid));
        var repeated = assertInstanceOf(
                SkillSubmissionOutcome.Invalid.class,
                SkillSubmissionOutcomeMapper.from(invalid));

        assertAll(
                () -> assertSame(invalid.report(), outcome.report()),
                () -> assertSame(invalid.report(), repeated.report()),
                () -> assertEquals(List.of(
                                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                                SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING),
                        outcome.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertCounts(stages, 1, 0, 0, 0));
    }

    @Test
    void c3B3InvalidPreservesMergedWarningAndValidationErrorWithoutRerun() {
        var components = SubmissionPreparationTestFixtures.emptyPipeline();
        var stages = components.countingStages();
        var draft = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                inputWithReadWarning(draft));
        var invalid = assertInstanceOf(
                SubmissionPreparationCheck.Invalid.class,
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
        assertCounts(stages, 1, 1, 1, 1);

        var outcome = assertInstanceOf(
                SkillSubmissionOutcome.Invalid.class,
                SkillSubmissionOutcomeMapper.from(invalid));
        var repeated = assertInstanceOf(
                SkillSubmissionOutcome.Invalid.class,
                SkillSubmissionOutcomeMapper.from(invalid));

        assertAll(
                () -> assertSame(invalid.report(), outcome.report()),
                () -> assertSame(invalid.report(), repeated.report()),
                () -> assertEquals(List.of(
                                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                                SkillValidationIssueCodes.SKILL_EMPTY_NODES),
                        outcome.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertCounts(stages, 1, 1, 1, 1));
    }

    @Test
    void mapperRejectsNullAtEveryFixedInputBoundary() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionOutcomeMapper.from(
                                (DraftSubmissionPrecheck.Invalid) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionOutcomeMapper.from(
                                (SubmissionAuthorityCheck.IdentityRejected) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionOutcomeMapper.from(
                                (SubmissionAuthorityCheck.Conflict) null)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionOutcomeMapper.from(
                                (SubmissionPreparationCheck) null)));
    }

    private static SkillSubmissionAuthorizationResult.Rejected rejection(
            SkillIdentityRejectionCode reason) {
        return new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID, reason);
    }

    private static SkillSubmissionInput inputWithReadWarning(SkillDraft draft) {
        var fact = new ReadFact(
                ReadFactCode.INTENSITY_CLAMPED_HIGH,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(),
                Optional.of(AppearanceField.INTENSITY_MILLI));
        var report = new SkillDraftReadReport(List.of(fact), false);
        return SkillSubmissionInput.fromReadResult(new SkillDraftReadResult(draft, report));
    }

    private static void assertCounts(
            SubmissionPreparationTestFixtures.CountingStages stages,
            int formalize,
            int resolve,
            int analyze,
            int project) {
        assertAll(
                () -> assertEquals(formalize, stages.formalizeCalls()),
                () -> assertEquals(resolve, stages.resolveCalls()),
                () -> assertEquals(analyze, stages.analyzeCalls()),
                () -> assertEquals(project, stages.projectCalls()));
    }
}
