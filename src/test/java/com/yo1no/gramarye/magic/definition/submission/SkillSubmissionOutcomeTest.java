package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillSubmissionOutcomeTest {
    @Test
    void preparedAcceptsWarningOnlyReportsAndRejectsErrorsAndNulls() {
        var plan = preparedPlan();
        var warning = warningReport(false);
        var truncatedWarning = warningReport(true);

        var regular = new SkillSubmissionOutcome.Prepared(plan, warning);
        var truncated = new SkillSubmissionOutcome.Prepared(plan, truncatedWarning);

        assertAll(
                () -> assertSame(plan, regular.plan()),
                () -> assertSame(warning, regular.report()),
                () -> assertSame(truncatedWarning, truncated.report()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.Prepared(null, warning)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.Prepared(plan, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Prepared(plan, errorReport())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Prepared(plan, hiddenErrorReport())));
    }

    @Test
    void invalidAcceptsRetainedOrOmittedErrorsAndRejectsWarningOnlyReports() {
        var retainedError = errorReport();
        var hiddenError = hiddenErrorReport();

        var retained = new SkillSubmissionOutcome.Invalid(retainedError);
        var hidden = new SkillSubmissionOutcome.Invalid(hiddenError);

        assertAll(
                () -> assertSame(retainedError, retained.report()),
                () -> assertSame(hiddenError, hidden.report()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.Invalid(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Invalid(ValidationResult.valid())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Invalid(warningReport(false))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Invalid(warningReport(true))));
    }

    @Test
    void conflictAcceptsWarningOnlyReportsAndRejectsErrorsAndNulls() {
        var conflict = new SkillSubmissionConflict.BaseRevisionForNew(
                SubmissionPreparationTestFixtures.SKILL_ID, new SkillRevision(2));
        var warning = warningReport(false);
        var truncatedWarning = warningReport(true);

        var regular = new SkillSubmissionOutcome.Conflict(conflict, warning);
        var truncated = new SkillSubmissionOutcome.Conflict(conflict, truncatedWarning);

        assertAll(
                () -> assertSame(conflict, regular.conflict()),
                () -> assertSame(warning, regular.report()),
                () -> assertSame(truncatedWarning, truncated.report()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.Conflict(null, warning)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.Conflict(conflict, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Conflict(conflict, errorReport())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.Conflict(conflict, hiddenErrorReport())));
    }

    @Test
    void identityRejectedSupportsAuthorizationAndAdmissionReasonsWithoutErrorReports() {
        var notAuthorized = rejection(SkillIdentityRejectionCode.NOT_AUTHORIZED);
        var quotaExceeded = rejection(SkillIdentityRejectionCode.QUOTA_EXCEEDED);
        var warning = warningReport(false);
        var truncatedWarning = warningReport(true);

        var authorization = new SkillSubmissionOutcome.IdentityRejected(notAuthorized, warning);
        var admission = new SkillSubmissionOutcome.IdentityRejected(
                quotaExceeded, truncatedWarning);

        assertAll(
                () -> assertSame(notAuthorized, authorization.rejection()),
                () -> assertSame(warning, authorization.report()),
                () -> assertSame(notAuthorized.skillId(), authorization.skillId()),
                () -> assertEquals(SkillIdentityRejectionCode.NOT_AUTHORIZED,
                        authorization.reason()),
                () -> assertSame(quotaExceeded, admission.rejection()),
                () -> assertSame(truncatedWarning, admission.report()),
                () -> assertEquals(SkillIdentityRejectionCode.QUOTA_EXCEEDED,
                        admission.reason()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.IdentityRejected(null, warning)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.IdentityRejected(notAuthorized, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.IdentityRejected(
                                notAuthorized, errorReport())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.IdentityRejected(
                                notAuthorized, hiddenErrorReport())));
    }

    @Test
    void revisionExhaustedRequiresMaximumLatestAndWarningOnlyReport() {
        var latest = reference(Integer.MAX_VALUE);
        var nonMaximum = reference(Integer.MAX_VALUE - 1);
        var warning = warningReport(false);
        var truncatedWarning = warningReport(true);

        var regular = new SkillSubmissionOutcome.RevisionExhausted(latest, warning);
        var truncated = new SkillSubmissionOutcome.RevisionExhausted(
                latest, truncatedWarning);

        assertAll(
                () -> assertSame(latest, regular.latest()),
                () -> assertSame(warning, regular.report()),
                () -> assertSame(truncatedWarning, truncated.report()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.RevisionExhausted(null, warning)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionOutcome.RevisionExhausted(latest, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.RevisionExhausted(
                                nonMaximum, warning)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.RevisionExhausted(
                                latest, errorReport())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionOutcome.RevisionExhausted(
                                latest, hiddenErrorReport())));
    }

    private static SkillSubmissionPlan preparedPlan() {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));
        var prepared = (SubmissionPreparationCheck.Prepared)
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT);
        return SkillSubmissionPlan.from(prepared);
    }

    private static SkillSubmissionAuthorizationResult.Rejected rejection(
            SkillIdentityRejectionCode reason) {
        return new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionPreparationTestFixtures.SKILL_ID, reason);
    }

    private static SkillReference reference(int revision) {
        return new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID, new SkillRevision(revision));
    }

    private static ValidationResult warningReport(boolean truncated) {
        return SubmissionAuthorityTestFixtures.warningReport(truncated);
    }

    private static ValidationResult errorReport() {
        return SubmissionAuthorityTestFixtures.errorReport();
    }

    private static ValidationResult hiddenErrorReport() {
        return SubmissionAuthorityTestFixtures.hiddenErrorReport();
    }
}
