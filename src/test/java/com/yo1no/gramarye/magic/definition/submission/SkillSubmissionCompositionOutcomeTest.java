package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCapacityScope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class SkillSubmissionCompositionOutcomeTest {
    private static final SkillReference TARGET = new SkillReference(
            SubmissionAuthorityTestFixtures.SKILL_ID,
            new SkillRevision(1));

    @Test
    void sealedOutcomeHasExactlyTheElevenApprovedVariants() {
        assertEquals(Set.of(
                        SkillSubmissionCompositionOutcome.DraftUnavailable.class,
                        SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class,
                        SkillSubmissionCompositionOutcome.PreparationRejected.class,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityRejected.class,
                        SkillSubmissionCompositionOutcome.CommitConflict.class,
                        SkillSubmissionCompositionOutcome.QuotaRejected.class,
                        SkillSubmissionCompositionOutcome.CapacityRejected.class,
                        SkillSubmissionCompositionOutcome.IdentityRejected.class,
                        SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                        SkillSubmissionCompositionOutcome.Committed.class,
                        SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery.class),
                Arrays.stream(SkillSubmissionCompositionOutcome.class.getPermittedSubclasses())
                        .collect(Collectors.toUnmodifiableSet()));
        assertEquals(Set.of(
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .ATTACHMENT_ENCODED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.DOCUMENT_BLOB,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.REVISION_BLOB,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.HISTORY_BLOB,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.STORE_BLOB,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .JOURNAL_ENTRY_COUNT,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .JOURNAL_ENCODED_BYTES),
                Set.of(SkillSubmissionCompositionOutcome.PersistenceCapacityScope.values()));
    }

    @Test
    void preparationRejectedRejectsPreparedAndPreservesWrappedReportIdentity() {
        var error = SubmissionAuthorityTestFixtures.errorReport();
        var invalid = new SkillSubmissionOutcome.Invalid(error);
        var rejected = new SkillSubmissionCompositionOutcome.PreparationRejected(invalid);
        assertSame(invalid, rejected.outcome());
        assertSame(error, rejected.report());
        assertFalse(rejected.toString().contains("ValidationResult"));

        var prepared = preparedOutcome();
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.PreparationRejected(prepared));
        assertThrows(NullPointerException.class, () ->
                new SkillSubmissionCompositionOutcome.PreparationRejected(null));
    }

    @Test
    void everyPostPreparationVariantRetainsExactWarningOnlyReport() {
        var report = SubmissionAuthorityTestFixtures.warningReport(true);
        var persistence = new SkillSubmissionCompositionOutcome.PersistenceCapacityRejected(
                SkillSubmissionCompositionOutcome.PersistenceCapacityScope.DOCUMENT_BLOB,
                report);
        var conflict = new SkillSubmissionCompositionOutcome.CommitConflict(
                new SkillSubmissionCompositionOutcome.CommitConflictDetail
                        .ExpectedAbsentButPresent(TARGET.skillId()),
                report);
        var quota = new SkillSubmissionCompositionOutcome.QuotaRejected(
                TARGET.skillId(), 4, 4, report);
        var capacity = new SkillSubmissionCompositionOutcome.CapacityRejected(
                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                128,
                128,
                report);
        var identity = new SkillSubmissionCompositionOutcome.IdentityRejected(
                TARGET.skillId(), SkillIdentityRejectionCode.NOT_AUTHORIZED, report);
        var unavailable = new SkillSubmissionCompositionOutcome
                .SubsystemUnavailableAfterPreparation(
                        TARGET,
                        SkillSubmissionCompositionOutcome.AfterPreparationPhase.PRE_COMMIT,
                        SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                .ATTACHMENT_STATE_CHANGED,
                        report);
        var committed = new SkillSubmissionCompositionOutcome.Committed(TARGET, report);
        var pending = new SkillSubmissionCompositionOutcome
                .CommittedPendingAttachmentRecovery(
                        TARGET,
                        SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(
                                SkillSubmissionCompositionOutcome
                                        .AttachmentPublicationFailureCode.STATE_CHANGED),
                        report);

        assertSame(report, persistence.report());
        assertSame(report, conflict.report());
        assertSame(report, quota.report());
        assertSame(report, capacity.report());
        assertSame(report, identity.report());
        assertSame(report, unavailable.report());
        assertSame(report, committed.report());
        assertSame(report, pending.report());
        assertFalse(persistence.toString().contains("ValidationResult"));
        assertFalse(committed.toString().contains("ValidationResult"));
    }

    @Test
    void postcommitStoreJournalInvariantHasExactTerminalNonCommittedShape() {
        var report = ValidationResult.valid();
        var outcome = new SkillSubmissionCompositionOutcome
                .SubsystemUnavailableAfterPreparation(
                        TARGET,
                        SkillSubmissionCompositionOutcome.AfterPreparationPhase
                                .POST_COMMIT_STORE_COMMITTED,
                        SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                .STORE_JOURNAL_PUBLICATION_INVARIANT,
                        report);
        assertSame(TARGET, outcome.target());
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationPhase
                        .POST_COMMIT_STORE_COMMITTED,
                outcome.phase());
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .STORE_JOURNAL_PUBLICATION_INVARIANT,
                outcome.failure());
        assertSame(report, outcome.report());
    }

    @Test
    void warningOnlyIdentityAndCapacityMetadataAreStrict() {
        var report = ValidationResult.valid();
        var error = SubmissionAuthorityTestFixtures.errorReport();
        var aboveQuotaMaximum = new SkillSubmissionCompositionOutcome.QuotaRejected(
                TARGET.skillId(), 3, 2, report);
        assertEquals(3, aboveQuotaMaximum.current());
        var aboveCapacityMaximum = new SkillSubmissionCompositionOutcome.CapacityRejected(
                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL + 1,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL,
                report);
        assertEquals(
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL + 1,
                aboveCapacityMaximum.current());
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.IdentityRejected(
                        TARGET.skillId(), SkillIdentityRejectionCode.QUOTA_EXCEEDED, report));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.QuotaRejected(
                        TARGET.skillId(), 2, 3, report));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.QuotaRejected(
                        TARGET.skillId(),
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1,
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1,
                        report));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.CapacityRejected(
                        SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                        -1,
                        0,
                        report));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.CapacityRejected(
                        SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                        5,
                        4,
                        report));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.Committed(TARGET, error));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.PersistenceCapacityRejected(
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.STORE_BLOB,
                        error));
    }

    @Test
    void prePreparationVariantsHaveNoReportAndRejectNullState() {
        var failure = SkillSubmissionCompositionOutcome.BeforePreparationFailure.of(
                SkillSubmissionCompositionOutcome.BeforePreparationFailureCode.STORE_UNAVAILABLE);
        var unavailable = new SkillSubmissionCompositionOutcome
                .SubsystemUnavailableBeforePreparation(TARGET.skillId(), failure);
        assertSame(TARGET.skillId(), unavailable.skillId());
        assertSame(failure, unavailable.failure());
        assertThrows(NullPointerException.class, () ->
                new SkillSubmissionCompositionOutcome.DraftUnavailable(null));
        assertThrows(NullPointerException.class, () ->
                new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                        null, failure));
        assertThrows(NullPointerException.class, () ->
                new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                        TARGET.skillId(), null));
    }

    @Test
    void boundedRuntimeMetadataIsPresentOnlyForRuntimeCodes() {
        var runtime = SkillSubmissionCompositionOutcome.BeforePreparationFailure
                .policyProviderException(new IllegalStateException("not retained"));
        assertEquals(
                SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                        .POLICY_PROVIDER_RUNTIME_EXCEPTION,
                runtime.code());
        assertEquals(
                IllegalStateException.class.getName(),
                runtime.exceptionClassName().orElseThrow());
        assertFalse(runtime.toString().contains("not retained"));

        var publication = SkillSubmissionCompositionOutcome.AttachmentPublicationFailure
                .runtime(new IllegalArgumentException("not retained"));
        assertEquals(
                SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                        .RUNTIME_EXCEPTION,
                publication.code());
        assertEquals(
                IllegalArgumentException.class.getName(),
                publication.exceptionClassName().orElseThrow());
        assertFalse(publication.toString().contains("not retained"));

        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.BeforePreparationFailure(
                        SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                                .STORE_UNAVAILABLE,
                        Optional.of("unexpected.Class")));
        assertThrows(IllegalArgumentException.class, () ->
                SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(
                        SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                                .RUNTIME_EXCEPTION));
    }

    @Test
    void conflictDetailsEnforceRouteAndDifferenceWithoutRawStoreResult() {
        var observed = new SkillReference(TARGET.skillId(), new SkillRevision(2));
        var mismatch = new SkillSubmissionCompositionOutcome.CommitConflictDetail.LatestMismatch(
                TARGET, observed);
        assertSame(TARGET.skillId(), mismatch.skillId());
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.CommitConflictDetail.LatestMismatch(
                        TARGET, TARGET));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillSubmissionCompositionOutcome.CommitConflictDetail.LatestMismatch(
                        TARGET,
                        new SkillReference(
                                SubmissionAuthorityTestFixtures.OTHER_SKILL_ID,
                                new SkillRevision(2))));
        assertTrue(SkillSubmissionCompositionOutcome.CommitConflictDetail.class.isSealed());
    }

    private static SkillSubmissionOutcome.Prepared preparedOutcome() {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));
        var prepared = (SubmissionPreparationCheck.Prepared)
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT);
        return new SkillSubmissionOutcome.Prepared(
                SkillSubmissionPlan.from(prepared), prepared.report());
    }
}
