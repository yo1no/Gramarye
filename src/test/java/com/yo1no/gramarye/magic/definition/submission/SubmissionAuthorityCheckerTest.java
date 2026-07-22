package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SubmissionAuthorityCheckerTest {
    private final SubmissionAuthorityChecker checker = new SubmissionAuthorityChecker();

    @Test
    void newSkillWithEmptyBaseProducesPassedTokenAndSameReport() {
        var report = SubmissionAuthorityTestFixtures.warningReport(false);
        var precheck = SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(Optional.empty()), report);
        var authorization = SubmissionAuthorityTestFixtures.authorizedNew(
                SubmissionAuthorityTestFixtures.SKILL_ID);

        var passed = assertInstanceOf(
                SubmissionAuthorityCheck.Passed.class,
                checker.check(precheck, authorization));

        assertAll(
                () -> assertSame(precheck, passed.precheck()),
                () -> assertSame(authorization, passed.authorization()),
                () -> assertSame(report, passed.report()));
    }

    @Test
    void newSkillWithBaseProducesTypedConflictWithoutRevisionProposal() {
        var base = new SkillRevision(3);
        var report = SubmissionAuthorityTestFixtures.warningReport(true);
        var precheck = SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(Optional.of(base)), report);

        var result = assertInstanceOf(
                SubmissionAuthorityCheck.Conflict.class,
                checker.check(precheck, SubmissionAuthorityTestFixtures.authorizedNew(
                        SubmissionAuthorityTestFixtures.SKILL_ID)));
        var conflict = assertInstanceOf(
                SkillSubmissionConflict.BaseRevisionForNew.class, result.conflict());

        assertAll(
                () -> assertSame(base, conflict.suppliedBase()),
                () -> assertSame(report, result.report()),
                () -> assertTrue(result.report().truncated()));
    }

    @Test
    void existingSkillClassifiesMissingExactStaleAndFutureBase() {
        var authorization = SubmissionAuthorityTestFixtures.authorizedExisting(
                SubmissionAuthorityTestFixtures.SKILL_ID, 5);

        var missing = checker.check(
                precheck(Optional.empty()), authorization);
        var exact = checker.check(
                precheck(Optional.of(new SkillRevision(5))), authorization);
        var stale = checker.check(
                precheck(Optional.of(new SkillRevision(4))), authorization);
        var future = checker.check(
                precheck(Optional.of(new SkillRevision(6))), authorization);

        assertAll(
                () -> assertInstanceOf(
                        SkillSubmissionConflict.MissingBaseForExisting.class,
                        assertInstanceOf(SubmissionAuthorityCheck.Conflict.class, missing)
                                .conflict()),
                () -> assertInstanceOf(SubmissionAuthorityCheck.Passed.class, exact),
                () -> assertInstanceOf(
                        SkillSubmissionConflict.StaleBase.class,
                        assertInstanceOf(SubmissionAuthorityCheck.Conflict.class, stale)
                                .conflict()),
                () -> assertInstanceOf(
                        SkillSubmissionConflict.FutureBase.class,
                        assertInstanceOf(SubmissionAuthorityCheck.Conflict.class, future)
                                .conflict()));
    }

    @Test
    void exactIntegerMaxLatestStillPassesBecauseExhaustionBelongsToP3C3() {
        var revision = new SkillRevision(Integer.MAX_VALUE);
        var result = checker.check(
                precheck(Optional.of(revision)),
                SubmissionAuthorityTestFixtures.authorizedExisting(
                        SubmissionAuthorityTestFixtures.SKILL_ID, Integer.MAX_VALUE));

        assertInstanceOf(SubmissionAuthorityCheck.Passed.class, result);
    }

    @Test
    void rejectedAuthorizationShortCircuitsBaseClassificationAndPreservesReason() {
        var report = SubmissionAuthorityTestFixtures.warningReport(false);
        var precheck = SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(Optional.of(new SkillRevision(9))), report);
        var rejection = new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SkillIdentityRejectionCode.QUOTA_EXCEEDED);

        var rejected = assertInstanceOf(
                SubmissionAuthorityCheck.IdentityRejected.class,
                checker.check(precheck, rejection));

        assertAll(
                () -> assertSame(rejection, rejected.rejection()),
                () -> assertSame(report, rejected.report()),
                () -> assertEquals(
                        SkillIdentityRejectionCode.QUOTA_EXCEEDED,
                        rejected.rejection().reason()));
    }

    @Test
    void everyAuthorizationToDraftIdMismatchIsAProgrammingException() {
        var matchingDraft = SubmissionAuthorityTestFixtures.draft(Optional.empty());
        var matchingPrecheck = SubmissionAuthorityTestFixtures.precheck(
                matchingDraft, ValidationResult.valid());
        var differentDraft = SubmissionAuthorityTestFixtures.draft(
                SubmissionAuthorityTestFixtures.OTHER_SKILL_ID, Optional.empty());
        var differentPrecheck = SubmissionAuthorityTestFixtures.precheck(
                differentDraft, ValidationResult.valid());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> checker.check(
                        matchingPrecheck,
                        new SkillSubmissionAuthorizationResult.Rejected(
                                SubmissionAuthorityTestFixtures.OTHER_SKILL_ID,
                                SkillIdentityRejectionCode.NOT_AUTHORIZED))),
                () -> assertThrows(IllegalArgumentException.class, () -> checker.check(
                        matchingPrecheck,
                        SubmissionAuthorityTestFixtures.authorizedNew(
                                SubmissionAuthorityTestFixtures.OTHER_SKILL_ID))),
                () -> assertThrows(IllegalArgumentException.class, () -> checker.check(
                        differentPrecheck,
                        SubmissionAuthorityTestFixtures.authorizedExisting(
                                SubmissionAuthorityTestFixtures.SKILL_ID, 1))));
    }

    @Test
    void passedConstructorDefensivelyUsesTheSharedPairingAndConcurrencyPolicies() {
        var newEmpty = precheck(Optional.empty());
        var newWithBase = precheck(Optional.of(new SkillRevision(1)));
        var existingExact = precheck(Optional.of(new SkillRevision(4)));
        var existingMissing = precheck(Optional.empty());
        var existingStale = precheck(Optional.of(new SkillRevision(3)));
        var existingFuture = precheck(Optional.of(new SkillRevision(5)));
        var newAuthorization = SubmissionAuthorityTestFixtures.authorizedNew(
                SubmissionAuthorityTestFixtures.SKILL_ID);
        var existingAuthorization = SubmissionAuthorityTestFixtures.authorizedExisting(
                SubmissionAuthorityTestFixtures.SKILL_ID, 4);

        assertAll(
                () -> assertDoesNotThrow(() ->
                        new SubmissionAuthorityCheck.Passed(newEmpty, newAuthorization)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Passed(newWithBase, newAuthorization)),
                () -> assertDoesNotThrow(() ->
                        new SubmissionAuthorityCheck.Passed(existingExact, existingAuthorization)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Passed(existingMissing, existingAuthorization)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Passed(existingStale, existingAuthorization)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Passed(existingFuture, existingAuthorization)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Passed(
                                newEmpty,
                                SubmissionAuthorityTestFixtures.authorizedNew(
                                        SubmissionAuthorityTestFixtures.OTHER_SKILL_ID))));
    }

    @Test
    void failureConstructorsRejectVisibleAndHiddenErrorsButAcceptTruncatedWarnings() {
        var rejection = new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SkillIdentityRejectionCode.NOT_AUTHORIZED);
        var conflict = new SkillSubmissionConflict.BaseRevisionForNew(
                SubmissionAuthorityTestFixtures.SKILL_ID, new SkillRevision(1));
        var warning = SubmissionAuthorityTestFixtures.warningReport(true);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.IdentityRejected(
                                rejection, SubmissionAuthorityTestFixtures.errorReport())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.IdentityRejected(
                                rejection, SubmissionAuthorityTestFixtures.hiddenErrorReport())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Conflict(
                                conflict, SubmissionAuthorityTestFixtures.errorReport())),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        new SubmissionAuthorityCheck.Conflict(
                                conflict, SubmissionAuthorityTestFixtures.hiddenErrorReport())),
                () -> assertSame(warning,
                        new SubmissionAuthorityCheck.IdentityRejected(rejection, warning).report()),
                () -> assertSame(warning,
                        new SubmissionAuthorityCheck.Conflict(conflict, warning).report()));
    }

    @Test
    void repeatedChecksAreDeterministicAndRetainTheSameInputObjects() {
        var precheck = precheck(Optional.of(new SkillRevision(2)));
        var authorization = SubmissionAuthorityTestFixtures.authorizedExisting(
                SubmissionAuthorityTestFixtures.SKILL_ID, 4);

        var first = assertInstanceOf(
                SubmissionAuthorityCheck.Conflict.class,
                checker.check(precheck, authorization));
        var second = assertInstanceOf(
                SubmissionAuthorityCheck.Conflict.class,
                checker.check(precheck, authorization));

        assertAll(
                () -> assertEquals(first.conflict(), second.conflict()),
                () -> assertSame(precheck.report(), first.report()),
                () -> assertSame(precheck.report(), second.report()));
    }

    @Test
    void checkerAndStageConstructorsRejectNullBoundaries() {
        var precheck = precheck(Optional.empty());
        var authorization = SubmissionAuthorityTestFixtures.authorizedNew(
                SubmissionAuthorityTestFixtures.SKILL_ID);
        var rejection = new SkillSubmissionAuthorizationResult.Rejected(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SkillIdentityRejectionCode.NOT_AUTHORIZED);
        var conflict = new SkillSubmissionConflict.BaseRevisionForNew(
                SubmissionAuthorityTestFixtures.SKILL_ID, new SkillRevision(1));

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> checker.check(null, authorization)),
                () -> assertThrows(NullPointerException.class,
                        () -> checker.check(precheck, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.Passed(null, authorization)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.Passed(precheck, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.IdentityRejected(null, ValidationResult.valid())),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.IdentityRejected(rejection, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.Conflict(null, ValidationResult.valid())),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionAuthorityCheck.Conflict(conflict, null)));
    }

    private static DraftSubmissionPrecheck.Ready precheck(Optional<SkillRevision> baseRevision) {
        return SubmissionAuthorityTestFixtures.precheck(
                SubmissionAuthorityTestFixtures.draft(baseRevision), ValidationResult.valid());
    }
}
