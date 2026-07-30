package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCapacityScope;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitConflict;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitResult;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** First-order runtime counter Gate for the complete P4-D2-B orchestration order. */
final class SkillDefinitionSubmissionServiceTest {
    private static final Object PLAYER = new Object();
    private static final Object SERVER = new Object();
    private static final SkillId SKILL_ID = SubmissionPreparationTestFixtures.SKILL_ID;
    private static final SkillOwnerId OWNER = SubmissionPreparationTestFixtures.OWNER;

    @Test
    void successInvokesEveryFirstOrderStageExactlyOnceInTheApprovedOrder() {
        var dependencies = new CountingDependencies(
                SubmissionPreparationTestFixtures.completeDraft(SKILL_ID, Optional.empty()));
        var service = new SkillDefinitionSubmissionService(dependencies);

        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.Committed.class,
                service.submitCore(PLAYER, SERVER, OWNER, SKILL_ID));

        assertEquals(List.of(
                        "draft_lookup",
                        "c1_precheck",
                        "store_authority",
                        "c2_authority",
                        "policy_snapshot",
                        "c3_prepare",
                        "c4_map",
                        "transition_prepare",
                        "d1_prepare",
                        "currentness",
                        "d1_commit",
                        "transition_publish"),
                dependencies.order);
        for (var stage : dependencies.order) {
            assertEquals(1, dependencies.invocations(stage), stage);
        }
        assertSame(dependencies.preparedReport, outcome.report());
        assertEquals(dependencies.target, outcome.reference());
    }

    @Test
    void currentnessFailureStopsBeforeCommitAndPublicationWithoutRetry() {
        var dependencies = new CountingDependencies(
                SubmissionPreparationTestFixtures.completeDraft(SKILL_ID, Optional.empty()));
        dependencies.currentness =
                PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED;
        var service = new SkillDefinitionSubmissionService(dependencies);

        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                service.submitCore(PLAYER, SERVER, OWNER, SKILL_ID));

        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .ATTACHMENT_STATE_CHANGED,
                outcome.failure());
        assertEquals(1, dependencies.invocations("d1_prepare"));
        assertEquals(1, dependencies.invocations("currentness"));
        assertEquals(0, dependencies.invocations("d1_commit"));
        assertEquals(0, dependencies.invocations("transition_publish"));
        assertSame(dependencies.preparedReport, outcome.report());
    }

    @Test
    void commitFailureStopsBeforeAttachmentPublicationWithoutRetry() {
        var dependencies = new CountingDependencies(
                SubmissionPreparationTestFixtures.completeDraft(SKILL_ID, Optional.empty()));
        dependencies.commitResultFactory = target ->
                new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                        .PreparedBaseMismatch(
                                SkillDefinitionStoreSubmissionPort.PreparedBaseMismatchCode
                                        .STATE_IDENTITY_CHANGED);
        var service = new SkillDefinitionSubmissionService(dependencies);

        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                service.submitCore(PLAYER, SERVER, OWNER, SKILL_ID));

        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .STORE_JOURNAL_STATE_CHANGED,
                outcome.failure());
        assertEquals(1, dependencies.invocations("d1_commit"));
        assertEquals(0, dependencies.invocations("transition_publish"));
        assertSame(dependencies.preparedReport, outcome.report());
    }

    @Test
    void c1InvalidStopsBeforeAuthorityPolicyAndEveryMutationStage() {
        var current = SubmissionPreparationTestFixtures.completeDraft(
                SKILL_ID, Optional.empty());
        var dependencies = new CountingDependencies(new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION + 1,
                current.skillId(),
                current.baseRevision(),
                current.nodes(),
                current.appearance()));
        dependencies.authoritySnapshot =
                new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Unavailable(
                        SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);
        dependencies.policyRuntimeException = new PolicyFailure();
        var service = new SkillDefinitionSubmissionService(dependencies);

        assertInstanceOf(
                SkillSubmissionCompositionOutcome.PreparationRejected.class,
                service.submitCore(PLAYER, SERVER, OWNER, SKILL_ID));

        assertEquals(List.of("draft_lookup", "c1_precheck", "c4_map_invalid"),
                dependencies.order);
        for (var forbidden : List.of(
                "store_authority",
                "c2_authority",
                "policy_snapshot",
                "c3_prepare",
                "transition_prepare",
                "d1_prepare",
                "currentness",
                "d1_commit",
                "transition_publish")) {
            assertEquals(0, dependencies.invocations(forbidden), forbidden);
        }
    }

    @Test
    void attachmentQuarantineAndMissingDraftRemainDistinctBeforePreparationOutcomes() {
        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.draftResult = new PlayerSkillAttachmentService.Unavailable<>(reason);

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class,
                    submit(dependencies));

            assertEquals(switch (reason) {
                case PRESERVED_RAW_QUARANTINE ->
                        SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                                .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
                case OVERSIZE_QUARANTINE ->
                        SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                                .ATTACHMENT_OVERSIZE_QUARANTINE;
            }, outcome.failure().code());
            assertEquals(List.of("draft_lookup"), dependencies.order);
        }

        var missing = validDependencies();
        missing.draftResult = new PlayerSkillAttachmentService.Available<>(Optional.empty());
        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.DraftUnavailable.class,
                submit(missing));
        assertSame(SKILL_ID, outcome.skillId());
        assertEquals(List.of("draft_lookup"), missing.order);
    }

    @Test
    void authorityUnavailableShortCircuitsBeforeC2AndPolicyWithExactReason() {
        for (var reason : SkillDefinitionStoreSubmissionPort.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.authoritySnapshot =
                    new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Unavailable(reason);

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class,
                    submit(dependencies));

            assertEquals(beforePreparationCode(reason), outcome.failure().code());
            assertEquals(
                    List.of("draft_lookup", "c1_precheck", "store_authority"),
                    dependencies.order);
            assertEquals(0, dependencies.invocations("c2_authority"));
            assertEquals(0, dependencies.invocations("policy_snapshot"));
            assertEquals(0, dependencies.invocations("c3_prepare"));
            assertEquals(0, dependencies.invocations("c4_map"));
            assertAllMutationStagesSkipped(dependencies);
        }
    }

    @Test
    void c2IdentityAndConflictRejectionsPrecedePolicyFailure() {
        var foreign = validDependencies();
        foreign.authoritySnapshot =
                new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.ForeignOwned(SKILL_ID);
        foreign.policyRuntimeException = new PolicyFailure();
        var identity = assertInstanceOf(
                SkillSubmissionCompositionOutcome.PreparationRejected.class,
                submit(foreign));
        assertInstanceOf(SkillSubmissionOutcome.IdentityRejected.class, identity.outcome());
        assertEquals(
                List.of(
                        "draft_lookup",
                        "c1_precheck",
                        "store_authority",
                        "c2_authority",
                        "c4_map_identity"),
                foreign.order);
        assertEquals(0, foreign.invocations("policy_snapshot"));
        assertEquals(0, foreign.invocations("c3_prepare"));
        assertEquals(0, foreign.invocations("c4_map"));
        assertAllMutationStagesSkipped(foreign);

        var conflict = validDependencies();
        conflict.authoritySnapshot =
                new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Owned(
                        reference(4));
        conflict.policyRuntimeException = new PolicyFailure();
        var rejected = assertInstanceOf(
                SkillSubmissionCompositionOutcome.PreparationRejected.class,
                submit(conflict));
        assertInstanceOf(SkillSubmissionOutcome.Conflict.class, rejected.outcome());
        assertEquals(
                List.of(
                        "draft_lookup",
                        "c1_precheck",
                        "store_authority",
                        "c2_authority",
                        "c4_map_conflict"),
                conflict.order);
        assertEquals(0, conflict.invocations("policy_snapshot"));
        assertEquals(0, conflict.invocations("c3_prepare"));
        assertEquals(0, conflict.invocations("c4_map"));
        assertAllMutationStagesSkipped(conflict);
    }

    @Test
    void ownedAuthorityUsesTheObservedLatestForAnExactlyOnceExistingSubmission() {
        var dependencies = new CountingDependencies(
                SubmissionPreparationTestFixtures.completeDraft(
                        SKILL_ID, Optional.of(new SkillRevision(4))));
        dependencies.authoritySnapshot =
                new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Owned(reference(4));

        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.Committed.class,
                submit(dependencies));

        assertEquals(reference(5), outcome.reference());
        assertSame(dependencies.preparedReport, outcome.report());
        assertEquals(1, dependencies.invocations("store_authority"));
        assertEquals(1, dependencies.invocations("c2_authority"));
        assertEquals(1, dependencies.invocations("policy_snapshot"));
        assertSuccessfulPublicationCounts(dependencies);
    }

    @Test
    void policyNullRuntimeAndErrorAreCalledOnceAndAlwaysPrecedeC3() {
        var nullPolicy = validDependencies();
        nullPolicy.nullPolicy = true;
        var nullOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class,
                submit(nullPolicy));
        assertEquals(
                SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                        .POLICY_SNAPSHOT_NULL,
                nullOutcome.failure().code());
        assertPolicyFailureStopsAtSnapshot(nullPolicy);
        assertEquals(1, nullPolicy.invocations("policy_snapshot"));

        var runtime = validDependencies();
        runtime.policyRuntimeException = new PolicyFailure();
        var runtimeOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class,
                submit(runtime));
        assertEquals(
                SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                        .POLICY_PROVIDER_RUNTIME_EXCEPTION,
                runtimeOutcome.failure().code());
        assertEquals(
                Optional.of(PolicyFailure.class.getName()),
                runtimeOutcome.failure().exceptionClassName());
        assertPolicyFailureStopsAtSnapshot(runtime);
        assertEquals(1, runtime.invocations("policy_snapshot"));

        var error = validDependencies();
        error.policyError = new PolicyError();
        assertSame(error.policyError, assertThrows(PolicyError.class, () -> submit(error)));
        assertPolicyFailureStopsAtSnapshot(error);
        assertEquals(1, error.invocations("policy_snapshot"));
    }

    @Test
    void everyInjectableNonPreparedC3OutcomeIsPreservedAndStopsBeforeTransition() {
        var nonPrepared = List.<SkillSubmissionOutcome>of(
                new SkillSubmissionOutcome.Invalid(
                        SubmissionAuthorityTestFixtures.errorReport()),
                new SkillSubmissionOutcome.IdentityRejected(
                        new SkillSubmissionAuthorizationResult.Rejected(
                                SKILL_ID, SkillIdentityRejectionCode.NOT_AUTHORIZED),
                        ValidationResult.valid()),
                new SkillSubmissionOutcome.Conflict(
                        new SkillSubmissionConflict.BaseRevisionForNew(
                                SKILL_ID, new SkillRevision(1)),
                        ValidationResult.valid()),
                new SkillSubmissionOutcome.RevisionExhausted(
                        new SkillReference(SKILL_ID, new SkillRevision(Integer.MAX_VALUE)),
                        ValidationResult.valid()));

        for (var injected : nonPrepared) {
            var dependencies = validDependencies();
            dependencies.preparationOverride = injected;

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.PreparationRejected.class,
                    submit(dependencies));

            assertSame(injected, outcome.outcome());
            assertSame(injected.report(), outcome.report());
            assertEquals(
                    List.of(
                            "draft_lookup",
                            "c1_precheck",
                            "store_authority",
                            "c2_authority",
                            "policy_snapshot",
                            "c3_prepare",
                            "c4_map"),
                    dependencies.order);
            assertEquals(1, dependencies.invocations("policy_snapshot"));
            assertEquals(1, dependencies.invocations("c3_prepare"));
            assertEquals(1, dependencies.invocations("c4_map"));
            assertEquals(0, dependencies.invocations("transition_prepare"));
            assertAllMutationStagesSkipped(dependencies);
        }
    }

    @Test
    void everyTransitionPreparationFailureHasExactMappingAndShortCircuitsD1() {
        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.transitionStep =
                    new SkillDefinitionSubmissionService.UnavailableTransitionStep(reason);

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                    submit(dependencies));

            assertEquals(attachmentFailure(reason), outcome.failure());
            assertPreCommitReportAndTarget(dependencies, outcome);
            assertD1AndPublicationSkipped(dependencies);
        }

        for (var code : PlayerSkillAttachmentService.TransitionRejectionCode.values()) {
            var dependencies = validDependencies();
            dependencies.transitionStep =
                    new SkillDefinitionSubmissionService.RejectedTransitionStep(code);
            var outcome = submit(dependencies);

            if (code == PlayerSkillAttachmentService.TransitionRejectionCode
                    .ATTACHMENT_CAPACITY_REJECTED) {
                var rejected = assertInstanceOf(
                        SkillSubmissionCompositionOutcome.PersistenceCapacityRejected.class,
                        outcome);
                assertEquals(
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .ATTACHMENT_ENCODED,
                        rejected.scope());
                assertSame(dependencies.preparedReport, rejected.report());
            } else {
                var unavailable = assertInstanceOf(
                        SkillSubmissionCompositionOutcome
                                .SubsystemUnavailableAfterPreparation.class,
                        outcome);
                assertEquals(switch (code) {
                    case GENERATION_EXHAUSTED ->
                            SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                    .GENERATION_EXHAUSTED;
                    case GENERATION_MISMATCH, POINTER_MISMATCH, TARGET_ROUTE_MISMATCH ->
                            SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                    .PLAN_TRANSITION_PAIRING_FAILURE;
                    case ATTACHMENT_CAPACITY_REJECTED ->
                            throw new AssertionError("capacity handled above");
                }, unavailable.failure());
                assertPreCommitReportAndTarget(dependencies, unavailable);
            }
            assertD1AndPublicationSkipped(dependencies);
        }

        var noOp = validDependencies();
        noOp.transitionStep = new SkillDefinitionSubmissionService.PreparedTransitionStep(
                noOp.transitionHandle, true);
        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                submit(noOp));
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .NORMAL_SUBMISSION_NO_OP,
                outcome.failure());
        assertPreCommitReportAndTarget(noOp, outcome);
        assertD1AndPublicationSkipped(noOp);
    }

    @Test
    void allSevenPersistenceCapacityScopesMapExactlyAndNeverReachCurrentness() {
        var attachment = validDependencies();
        attachment.transitionStep =
                new SkillDefinitionSubmissionService.RejectedTransitionStep(
                        PlayerSkillAttachmentService.TransitionRejectionCode
                                .ATTACHMENT_CAPACITY_REJECTED);
        var attachmentOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.PersistenceCapacityRejected.class,
                submit(attachment));
        assertEquals(
                SkillSubmissionCompositionOutcome.PersistenceCapacityScope.ATTACHMENT_ENCODED,
                attachmentOutcome.scope());
        assertSame(attachment.preparedReport, attachmentOutcome.report());

        var capacityMappings = List.of(
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .DOCUMENT_BLOB_CAPACITY_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.DOCUMENT_BLOB),
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .REVISION_BLOB_CAPACITY_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.REVISION_BLOB),
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .HISTORY_BLOB_CAPACITY_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.HISTORY_BLOB),
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .STORE_BLOB_CAPACITY_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope.STORE_BLOB),
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .JOURNAL_ENTRY_COUNT_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .JOURNAL_ENTRY_COUNT),
                new PreparationCapacityCase(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .JOURNAL_ENCODED_CAPACITY_REJECTED,
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .JOURNAL_ENCODED_BYTES));
        for (var mapping : capacityMappings) {
            var dependencies = validDependencies();
            dependencies.storePreparationStep =
                    new SkillDefinitionSubmissionService.RejectedStorePreparationStep(
                            mapping.failure());

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityRejected.class,
                    submit(dependencies));

            assertEquals(mapping.scope(), outcome.scope());
            assertSame(dependencies.preparedReport, outcome.report());
            assertEquals(1, dependencies.invocations("d1_prepare"));
            assertEquals(0, dependencies.invocations("currentness"));
            assertEquals(0, dependencies.invocations("d1_commit"));
            assertEquals(0, dependencies.invocations("transition_publish"));
        }
    }

    @Test
    void everyNonCapacityD1PreparationFailureMapsExactlyAndNeverCommits() {
        for (var failure : SkillDefinitionStoreSubmissionPort.PreparationFailure.values()) {
            if (isPersistenceCapacity(failure)) {
                continue;
            }
            var dependencies = validDependencies();
            dependencies.storePreparationStep =
                    new SkillDefinitionSubmissionService.RejectedStorePreparationStep(failure);

            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                    submit(dependencies));

            assertEquals(preparationFailure(failure), outcome.failure());
            assertPreCommitReportAndTarget(dependencies, outcome);
            assertEquals(0, dependencies.invocations("currentness"));
            assertEquals(0, dependencies.invocations("d1_commit"));
            assertEquals(0, dependencies.invocations("transition_publish"));
        }

        for (var reason : SkillDefinitionStoreSubmissionPort.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.storePreparationStep =
                    new SkillDefinitionSubmissionService.UnavailableStorePreparationStep(reason);
            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                    submit(dependencies));
            assertEquals(storeUnavailable(reason), outcome.failure());
            assertPreCommitReportAndTarget(dependencies, outcome);
            assertEquals(0, dependencies.invocations("currentness"));
            assertEquals(0, dependencies.invocations("d1_commit"));
            assertEquals(0, dependencies.invocations("transition_publish"));
        }
    }

    @Test
    void currentnessStateChangeAndQuarantineStopAfterD1Preparation() {
        var changed = validDependencies();
        changed.currentness = PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED;
        var changedOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                submit(changed));
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .ATTACHMENT_STATE_CHANGED,
                changedOutcome.failure());
        assertPreCommitReportAndTarget(changed, changedOutcome);
        assertEquals(1, changed.invocations("d1_prepare"));
        assertEquals(1, changed.invocations("currentness"));
        assertEquals(0, changed.invocations("d1_commit"));
        assertEquals(0, changed.invocations("transition_publish"));

        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.currentnessResult =
                    new PlayerSkillAttachmentService.Unavailable<>(reason);
            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                    submit(dependencies));
            assertEquals(attachmentFailure(reason), outcome.failure());
            assertPreCommitReportAndTarget(dependencies, outcome);
            assertEquals(1, dependencies.invocations("d1_prepare"));
            assertEquals(1, dependencies.invocations("currentness"));
            assertEquals(0, dependencies.invocations("d1_commit"));
            assertEquals(0, dependencies.invocations("transition_publish"));
        }
    }

    @Test
    void everyDomainCommitRejectionMapsExactlyWithoutPublication() {
        assertConflictMapping(
                target -> new SkillStoreCommitConflict.ExpectedAbsentButPresent(SKILL_ID));
        assertConflictMapping(
                SkillStoreCommitConflict.ExpectedLatestButAbsent::new);
        assertConflictMapping(
                target -> new SkillStoreCommitConflict.LatestMismatch(
                        target, new SkillReference(SKILL_ID,
                                new SkillRevision(target.revision().value() + 1))));

        var quota = validDependencies();
        quota.commitResultFactory = target -> domainRejected(
                new SkillStoreCommitResult.QuotaRejected(SKILL_ID, 1, 1));
        var quotaOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.QuotaRejected.class,
                submit(quota));
        assertEquals(SKILL_ID, quotaOutcome.skillId());
        assertEquals(1, quotaOutcome.current());
        assertEquals(1, quotaOutcome.maximum());
        assertTerminalCommitMapping(quota, quotaOutcome);

        for (var scope : SkillStoreCapacityScope.values()) {
            var dependencies = validDependencies();
            int maximum = canonicalMaximum(scope);
            dependencies.commitResultFactory = target -> domainRejected(
                    new SkillStoreCommitResult.CapacityRejected(
                            scope, maximum, maximum));
            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.CapacityRejected.class,
                    submit(dependencies));
            assertEquals(scope, outcome.scope());
            assertEquals(maximum, outcome.current());
            assertEquals(maximum, outcome.maximum());
            assertTerminalCommitMapping(dependencies, outcome);
        }

        var owner = validDependencies();
        owner.commitResultFactory = target -> domainRejected(
                new SkillStoreCommitResult.OwnerRejected(SKILL_ID));
        var ownerOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.IdentityRejected.class,
                submit(owner));
        assertEquals(SKILL_ID, ownerOutcome.skillId());
        assertEquals(SkillIdentityRejectionCode.NOT_AUTHORIZED, ownerOutcome.reason());
        assertTerminalCommitMapping(owner, ownerOutcome);
    }

    @Test
    void everyPreparedBaseMismatchUnavailableAndPostCommitInvariantMapsExactly() {
        for (var code : SkillDefinitionStoreSubmissionPort.PreparedBaseMismatchCode.values()) {
            var dependencies = validDependencies();
            dependencies.commitResultFactory = target ->
                    new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                            .PreparedBaseMismatch(code);
            var outcome = submit(dependencies);
            if (code == SkillDefinitionStoreSubmissionPort.PreparedBaseMismatchCode
                    .AUTHORITY_CHANGED) {
                var conflict = assertInstanceOf(
                        SkillSubmissionCompositionOutcome.CommitConflict.class, outcome);
                var detail = assertInstanceOf(
                        SkillSubmissionCompositionOutcome.CommitConflictDetail
                                .AuthorityChanged.class,
                        conflict.detail());
                assertEquals(SKILL_ID, detail.skillId());
                assertTerminalCommitMapping(dependencies, conflict);
            } else {
                var unavailable = assertInstanceOf(
                        SkillSubmissionCompositionOutcome
                                .SubsystemUnavailableAfterPreparation.class,
                        outcome);
                assertEquals(
                        code == SkillDefinitionStoreSubmissionPort.PreparedBaseMismatchCode
                                .STATE_IDENTITY_CHANGED
                                ? SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                        .STORE_JOURNAL_STATE_CHANGED
                                : SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                        .PLAN_TRANSITION_PAIRING_FAILURE,
                        unavailable.failure());
                assertPreCommitReportAndTarget(dependencies, unavailable);
                assertEquals(0, dependencies.invocations("transition_publish"));
            }
        }

        for (var reason : SkillDefinitionStoreSubmissionPort.UnavailableReason.values()) {
            var dependencies = validDependencies();
            dependencies.commitResultFactory = target ->
                    new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                            .Unavailable(reason);
            var outcome = assertInstanceOf(
                    SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                    submit(dependencies));
            assertEquals(storeUnavailable(reason), outcome.failure());
            assertPreCommitReportAndTarget(dependencies, outcome);
            assertEquals(0, dependencies.invocations("transition_publish"));
        }

        var postCommit = validDependencies();
        postCommit.commitResultFactory = target ->
                new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                        .PostCommitInvariantFailure(
                                SkillDefinitionStoreSubmissionPort.PostCommitFailureCode
                                        .POST_COMMIT_INVARIANT_FAILURE);
        var postCommitOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                submit(postCommit));
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationPhase
                        .POST_COMMIT_STORE_COMMITTED,
                postCommitOutcome.phase());
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .STORE_JOURNAL_PUBLICATION_INVARIANT,
                postCommitOutcome.failure());
        assertSame(postCommit.preparedReport, postCommitOutcome.report());
        assertEquals(0, postCommit.invocations("transition_publish"));

        var mismatchedReference = validDependencies();
        mismatchedReference.commitResultFactory = target ->
                new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed(
                        new SkillReference(
                                target.skillId(),
                                new SkillRevision(target.revision().value() + 1)));
        var mismatchOutcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation.class,
                submit(mismatchedReference));
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationPhase
                        .POST_COMMIT_STORE_COMMITTED,
                mismatchOutcome.phase());
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .STORE_JOURNAL_PUBLICATION_INVARIANT,
                mismatchOutcome.failure());
        assertSame(mismatchedReference.preparedReport, mismatchOutcome.report());
        assertEquals(0, mismatchedReference.invocations("transition_publish"));
    }

    @Test
    void everyAttachmentPublicationResultPreservesCommitAndPreparedReportIdentity() {
        var applied = validDependencies();
        var committed = assertInstanceOf(
                SkillSubmissionCompositionOutcome.Committed.class, submit(applied));
        assertEquals(applied.target, committed.reference());
        assertSame(applied.preparedReport, committed.report());
        assertSuccessfulPublicationCounts(applied);

        var noOp = validDependencies();
        noOp.publicationResult = new PlayerSkillAttachmentService.Available<>(
                PlayerSkillAttachmentService.NoOp.INSTANCE);
        assertPublicationFailure(
                noOp,
                SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                        .UNEXPECTED_NO_OP);

        var rejected = validDependencies();
        rejected.publicationResult = new PlayerSkillAttachmentService.Available<>(
                new PlayerSkillAttachmentService.MutationRejected(
                        PlayerSkillAttachmentService.MutationRejectionCode.STATE_CHANGED));
        assertPublicationFailure(
                rejected,
                SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                        .STATE_CHANGED);

        for (var reason : PlayerSkillAttachmentService.UnavailableReason.values()) {
            var quarantined = validDependencies();
            quarantined.publicationResult =
                    new PlayerSkillAttachmentService.Unavailable<>(reason);
            assertPublicationFailure(
                    quarantined,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                            .ATTACHMENT_QUARANTINED);
        }

        var runtime = validDependencies();
        runtime.publicationRuntimeException = new PublicationFailure();
        var pending = assertPublicationFailure(
                runtime,
                SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode
                        .RUNTIME_EXCEPTION);
        assertEquals(
                Optional.of(PublicationFailure.class.getName()),
                pending.failure().exceptionClassName());

        var error = validDependencies();
        error.publicationError = new PublicationError();
        assertSame(error.publicationError,
                assertThrows(PublicationError.class, () -> submit(error)));
        assertSuccessfulPublicationCounts(error);
        assertSame(error.draft, error.originalDraft);
    }

    private static CountingDependencies validDependencies() {
        return new CountingDependencies(
                SubmissionPreparationTestFixtures.completeDraft(SKILL_ID, Optional.empty()));
    }

    private static SkillSubmissionCompositionOutcome submit(CountingDependencies dependencies) {
        return new SkillDefinitionSubmissionService(dependencies)
                .submitCore(PLAYER, SERVER, OWNER, SKILL_ID);
    }

    private static SkillReference reference(int revision) {
        return new SkillReference(SKILL_ID, new SkillRevision(revision));
    }

    private static SkillDefinitionStoreSubmissionPort.SubmissionCommitResult domainRejected(
            SkillStoreCommitResult result) {
        return new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.DomainRejected(
                result);
    }

    private static void assertConflictMapping(
            Function<SkillReference, SkillStoreCommitConflict> conflictFactory) {
        var dependencies = validDependencies();
        var source = new SkillStoreCommitConflict[1];
        dependencies.commitResultFactory = target -> {
            source[0] = conflictFactory.apply(target);
            return domainRejected(new SkillStoreCommitResult.Conflict(source[0]));
        };

        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.CommitConflict.class,
                submit(dependencies));

        var expected = switch (source[0]) {
            case SkillStoreCommitConflict.ExpectedAbsentButPresent conflict ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail
                            .ExpectedAbsentButPresent(conflict.skillId());
            case SkillStoreCommitConflict.ExpectedLatestButAbsent conflict ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail
                            .ExpectedLatestButAbsent(conflict.expected());
            case SkillStoreCommitConflict.LatestMismatch conflict ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail.LatestMismatch(
                            conflict.expected(), conflict.observed());
        };
        assertEquals(expected, outcome.detail());
        assertTerminalCommitMapping(dependencies, outcome);
    }

    private static void assertTerminalCommitMapping(
            CountingDependencies dependencies,
            SkillSubmissionCompositionOutcome outcome) {
        assertSame(dependencies.preparedReport, reportOf(outcome));
        assertEquals(1, dependencies.invocations("d1_commit"));
        assertEquals(0, dependencies.invocations("transition_publish"));
        assertSame(dependencies.draft, dependencies.originalDraft);
    }

    private static SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery
            assertPublicationFailure(
                    CountingDependencies dependencies,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailureCode code) {
        var outcome = assertInstanceOf(
                SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery.class,
                submit(dependencies));
        assertEquals(code, outcome.failure().code());
        assertEquals(dependencies.target, outcome.reference());
        assertSame(dependencies.preparedReport, outcome.report());
        assertSuccessfulPublicationCounts(dependencies);
        assertSame(dependencies.draft, dependencies.originalDraft);
        return outcome;
    }

    private static void assertSuccessfulPublicationCounts(CountingDependencies dependencies) {
        assertEquals(1, dependencies.invocations("d1_prepare"));
        assertEquals(1, dependencies.invocations("currentness"));
        assertEquals(1, dependencies.invocations("d1_commit"));
        assertEquals(1, dependencies.invocations("transition_publish"));
    }

    private static void assertPreCommitReportAndTarget(
            CountingDependencies dependencies,
            SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation outcome) {
        assertEquals(
                SkillSubmissionCompositionOutcome.AfterPreparationPhase.PRE_COMMIT,
                outcome.phase());
        assertEquals(dependencies.target, outcome.target());
        assertSame(dependencies.preparedReport, outcome.report());
    }

    private static ValidationResult reportOf(SkillSubmissionCompositionOutcome outcome) {
        return switch (outcome) {
            case SkillSubmissionCompositionOutcome.PreparationRejected rejected ->
                    rejected.report();
            case SkillSubmissionCompositionOutcome.PersistenceCapacityRejected rejected ->
                    rejected.report();
            case SkillSubmissionCompositionOutcome.CommitConflict conflict -> conflict.report();
            case SkillSubmissionCompositionOutcome.QuotaRejected rejected -> rejected.report();
            case SkillSubmissionCompositionOutcome.CapacityRejected rejected -> rejected.report();
            case SkillSubmissionCompositionOutcome.IdentityRejected rejected -> rejected.report();
            case SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation unavailable ->
                    unavailable.report();
            case SkillSubmissionCompositionOutcome.Committed committed -> committed.report();
            case SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery pending ->
                    pending.report();
            case SkillSubmissionCompositionOutcome.DraftUnavailable ignored ->
                    throw new AssertionError("outcome does not carry a report");
            case SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation ignored ->
                    throw new AssertionError("outcome does not carry a report");
        };
    }

    private static void assertD1AndPublicationSkipped(CountingDependencies dependencies) {
        assertEquals(0, dependencies.invocations("d1_prepare"));
        assertEquals(0, dependencies.invocations("currentness"));
        assertEquals(0, dependencies.invocations("d1_commit"));
        assertEquals(0, dependencies.invocations("transition_publish"));
    }

    private static void assertAllMutationStagesSkipped(CountingDependencies dependencies) {
        assertEquals(0, dependencies.invocations("transition_prepare"));
        assertD1AndPublicationSkipped(dependencies);
    }

    private static void assertPolicyFailureStopsAtSnapshot(
            CountingDependencies dependencies) {
        assertEquals(
                List.of(
                        "draft_lookup",
                        "c1_precheck",
                        "store_authority",
                        "c2_authority",
                        "policy_snapshot"),
                dependencies.order);
        assertEquals(0, dependencies.invocations("c3_prepare"));
        assertEquals(0, dependencies.invocations("c4_map"));
        assertAllMutationStagesSkipped(dependencies);
    }

    private static boolean isPersistenceCapacity(
            SkillDefinitionStoreSubmissionPort.PreparationFailure failure) {
        return switch (failure) {
            case DOCUMENT_BLOB_CAPACITY_REJECTED,
                    REVISION_BLOB_CAPACITY_REJECTED,
                    HISTORY_BLOB_CAPACITY_REJECTED,
                    STORE_BLOB_CAPACITY_REJECTED,
                    JOURNAL_ENTRY_COUNT_REJECTED,
                    JOURNAL_ENCODED_CAPACITY_REJECTED -> true;
            case TRANSITION_SERVER_MISMATCH,
                    NORMAL_SUBMISSION_NO_OP,
                    PLAN_TRANSITION_PAIRING_FAILURE,
                    AUTHORITY_PRECONDITION_MISMATCH,
                    STORE_CARRIER_INVARIANT_FAILURE,
                    JOURNAL_CHAIN_INVARIANT_FAILURE,
                    SAVED_DATA_CARRIER_INVARIANT_FAILURE -> false;
        };
    }

    private static SkillSubmissionCompositionOutcome.AfterPreparationFailure preparationFailure(
            SkillDefinitionStoreSubmissionPort.PreparationFailure failure) {
        return switch (failure) {
            case TRANSITION_SERVER_MISMATCH, PLAN_TRANSITION_PAIRING_FAILURE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .PLAN_TRANSITION_PAIRING_FAILURE;
            case NORMAL_SUBMISSION_NO_OP ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .NORMAL_SUBMISSION_NO_OP;
            case AUTHORITY_PRECONDITION_MISMATCH ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .AUTHORITY_PRECONDITION_MISMATCH;
            case STORE_CARRIER_INVARIANT_FAILURE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .STORE_CARRIER_INVARIANT_FAILURE;
            case JOURNAL_CHAIN_INVARIANT_FAILURE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .JOURNAL_CHAIN_INVARIANT_FAILURE;
            case SAVED_DATA_CARRIER_INVARIANT_FAILURE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .SAVED_DATA_CARRIER_INVARIANT_FAILURE;
            case DOCUMENT_BLOB_CAPACITY_REJECTED,
                    REVISION_BLOB_CAPACITY_REJECTED,
                    HISTORY_BLOB_CAPACITY_REJECTED,
                    STORE_BLOB_CAPACITY_REJECTED,
                    JOURNAL_ENTRY_COUNT_REJECTED,
                    JOURNAL_ENCODED_CAPACITY_REJECTED ->
                    throw new AssertionError("capacity is not an invariant mapping");
        };
    }

    private static SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
            beforePreparationCode(
                    SkillDefinitionStoreSubmissionPort.UnavailableReason reason) {
        return switch (reason) {
            case JOURNAL_NOT_BOOTSTRAPPED ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .JOURNAL_NOT_BOOTSTRAPPED;
            case JOURNAL_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .AUTHORITY_UNAVAILABLE;
        };
    }

    private static SkillSubmissionCompositionOutcome.AfterPreparationFailure attachmentFailure(
            PlayerSkillAttachmentService.UnavailableReason reason) {
        return switch (reason) {
            case PRESERVED_RAW_QUARANTINE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
            case OVERSIZE_QUARANTINE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .ATTACHMENT_OVERSIZE_QUARANTINE;
        };
    }

    private static SkillSubmissionCompositionOutcome.AfterPreparationFailure storeUnavailable(
            SkillDefinitionStoreSubmissionPort.UnavailableReason reason) {
        return switch (reason) {
            case JOURNAL_NOT_BOOTSTRAPPED ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .JOURNAL_NOT_BOOTSTRAPPED;
            case JOURNAL_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure.STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .AUTHORITY_UNAVAILABLE;
        };
    }

    private static int canonicalMaximum(SkillStoreCapacityScope scope) {
        return switch (scope) {
            case OWNER_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER;
            case GLOBAL_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL;
            case SKILL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
            case GLOBAL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL;
        };
    }

    private record PreparationCapacityCase(
            SkillDefinitionStoreSubmissionPort.PreparationFailure failure,
            SkillSubmissionCompositionOutcome.PersistenceCapacityScope scope) {
    }

    private static final class PolicyFailure extends RuntimeException {
    }

    private static final class PolicyError extends Error {
    }

    private static final class PublicationFailure extends RuntimeException {
    }

    private static final class PublicationError extends Error {
    }

    private static final class CountingDependencies
            implements SkillDefinitionSubmissionService.Dependencies {
        private final List<String> order = new ArrayList<>();
        private final SkillDraft draft;
        private final SkillDraft originalDraft;
        private final SkillSubmissionPreparationPipeline pipeline;
        private final Object transitionHandle = new Object();
        private final Object storeHandle = new Object();
        private PlayerSkillAttachmentService.Result<Optional<SkillDraft>> draftResult;
        private SkillDefinitionStoreSubmissionPort.AuthoritySnapshot authoritySnapshot;
        private boolean nullPolicy;
        private RuntimeException policyRuntimeException;
        private Error policyError;
        private SkillSubmissionOutcome preparationOverride;
        private SkillDefinitionSubmissionService.TransitionStep transitionStep;
        private SkillDefinitionSubmissionService.StorePreparationStep storePreparationStep;
        private PlayerSkillAttachmentService.TransitionCurrentness currentness =
                PlayerSkillAttachmentService.TransitionCurrentness.CURRENT;
        private PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.TransitionCurrentness>
                currentnessResult;
        private Function<SkillReference,
                        SkillDefinitionStoreSubmissionPort.SubmissionCommitResult>
                commitResultFactory;
        private PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.MutationOutcome>
                publicationResult;
        private RuntimeException publicationRuntimeException;
        private Error publicationError;
        private SkillReference target;
        private ValidationResult preparedReport;

        private CountingDependencies(SkillDraft draft) {
            this.draft = draft;
            this.originalDraft = draft;
            this.draftResult = new PlayerSkillAttachmentService.Available<>(
                    Optional.of(draft));
            this.authoritySnapshot =
                    new SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Absent(SKILL_ID);
            this.commitResultFactory = committedTarget ->
                    new SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                            .Committed(committedTarget);
            this.publicationResult = new PlayerSkillAttachmentService.Available<>(
                    PlayerSkillAttachmentService.Applied.INSTANCE);
            var components = SubmissionPreparationTestFixtures.validPipeline();
            this.pipeline = new SkillSubmissionPreparationPipeline(
                    new CountingStages(components, order));
        }

        @Override
        public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object playerIdentity, SkillId skillId) {
            record("draft_lookup");
            assertSame(PLAYER, playerIdentity);
            assertSame(SKILL_ID, skillId);
            return draftResult;
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillDraft candidate) {
            assertSame(draft, candidate);
            return pipeline.precheck(candidate);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.AuthoritySnapshot
                observeSubmissionAuthority(
                        Object serverIdentity, SkillId skillId, SkillOwnerId owner) {
            record("store_authority");
            assertSame(SERVER, serverIdentity);
            assertSame(SKILL_ID, skillId);
            assertSame(OWNER, owner);
            return authoritySnapshot;
        }

        @Override
        public SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization) {
            return pipeline.checkAuthority(ready, authorization);
        }

        @Override
        public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
            return pipeline.map(invalid);
        }

        @Override
        public SkillSubmissionOutcome map(
                SubmissionAuthorityCheck.IdentityRejected rejected) {
            return pipeline.map(rejected);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
            return pipeline.map(conflict);
        }

        @Override
        public SkillSubmissionPolicySnapshot snapshotPolicy(Object serverIdentity) {
            record("policy_snapshot");
            assertSame(SERVER, serverIdentity);
            if (policyError != null) {
                throw policyError;
            }
            if (policyRuntimeException != null) {
                throw policyRuntimeException;
            }
            if (nullPolicy) {
                return null;
            }
            return new SkillSubmissionPolicySnapshot(
                    SkillQuota.Unlimited.INSTANCE,
                    SubmissionPreparationTestFixtures.CONTEXT);
        }

        @Override
        public SkillSubmissionOutcome prepareAndMap(
                SubmissionAuthorityCheck.Passed passed, ValidationContext context) {
            assertSame(SubmissionPreparationTestFixtures.CONTEXT, context);
            var outcome = pipeline.prepareAndMap(passed, context);
            if (preparationOverride != null) {
                return preparationOverride;
            }
            if (outcome instanceof SkillSubmissionOutcome.Prepared prepared) {
                target = new SkillReference(
                        prepared.plan().proposedDocument().skillId(),
                        prepared.plan().proposedDocument().revision());
                preparedReport = prepared.report();
            }
            return outcome;
        }

        @Override
        public SkillDefinitionSubmissionService.TransitionStep
                prepareLatestTransitionToCurrent(
                        Object playerIdentity, SkillId skillId, SkillReference candidateTarget) {
            record("transition_prepare");
            assertSame(PLAYER, playerIdentity);
            assertSame(SKILL_ID, skillId);
            assertEquals(target, candidateTarget);
            if (transitionStep != null) {
                return transitionStep;
            }
            return new SkillDefinitionSubmissionService.PreparedTransitionStep(
                    transitionHandle, false);
        }

        @Override
        public SkillDefinitionSubmissionService.StorePreparationStep prepareSubmissionCommit(
                Object serverIdentity,
                SkillSubmissionPlan plan,
                SkillQuota quota,
                Object candidateTransitionHandle) {
            record("d1_prepare");
            assertSame(SERVER, serverIdentity);
            assertSame(SkillQuota.Unlimited.INSTANCE, quota);
            assertSame(transitionHandle, candidateTransitionHandle);
            assertEquals(target.skillId(), plan.proposedDocument().skillId());
            if (storePreparationStep != null) {
                return storePreparationStep;
            }
            return new SkillDefinitionSubmissionService.PreparedStorePreparationStep(storeHandle);
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.TransitionCurrentness>
                checkPreparedTransitionCurrent(
                        Object playerIdentity, Object candidateTransitionHandle) {
            record("currentness");
            assertSame(PLAYER, playerIdentity);
            assertSame(transitionHandle, candidateTransitionHandle);
            if (currentnessResult != null) {
                return currentnessResult;
            }
            return new PlayerSkillAttachmentService.Available<>(currentness);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                commitPreparedSubmission(Object serverIdentity, Object candidateStoreHandle) {
            record("d1_commit");
            assertSame(SERVER, serverIdentity);
            assertSame(storeHandle, candidateStoreHandle);
            return commitResultFactory.apply(target);
        }

        @Override
        public PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publishPreparedTransition(
                        Object playerIdentity, Object candidateTransitionHandle) {
            record("transition_publish");
            assertSame(PLAYER, playerIdentity);
            assertSame(transitionHandle, candidateTransitionHandle);
            if (publicationError != null) {
                throw publicationError;
            }
            if (publicationRuntimeException != null) {
                throw publicationRuntimeException;
            }
            return publicationResult;
        }

        private void record(String stage) {
            order.add(stage);
        }

        private int invocations(String stage) {
            return Math.toIntExact(order.stream().filter(stage::equals).count());
        }
    }

    private static final class CountingStages
            implements SkillSubmissionPreparationPipeline.Stages {
        private final SubmissionPreparationTestFixtures.PipelineComponents components;
        private final List<String> order;
        private final DraftSubmissionPrechecker prechecker = new DraftSubmissionPrechecker();
        private final SubmissionAuthorityChecker authorityChecker =
                new SubmissionAuthorityChecker();

        private CountingStages(
                SubmissionPreparationTestFixtures.PipelineComponents components,
                List<String> order) {
            this.components = components;
            this.order = order;
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillSubmissionInput input) {
            order.add("c1_precheck");
            return prechecker.check(input);
        }

        @Override
        public SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization) {
            order.add("c2_authority");
            return authorityChecker.check(ready, authorization);
        }

        @Override
        public SubmissionPreparationCheck prepare(
                SubmissionAuthorityCheck.Passed passed, ValidationContext context) {
            order.add("c3_prepare");
            return components.productionPreparer().prepare(passed, context);
        }

        @Override
        public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
            order.add("c4_map_invalid");
            return SkillSubmissionOutcomeMapper.from(invalid);
        }

        @Override
        public SkillSubmissionOutcome map(
                SubmissionAuthorityCheck.IdentityRejected rejected) {
            order.add("c4_map_identity");
            return SkillSubmissionOutcomeMapper.from(rejected);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
            order.add("c4_map_conflict");
            return SkillSubmissionOutcomeMapper.from(conflict);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionPreparationCheck preparation) {
            order.add("c4_map");
            return SkillSubmissionOutcomeMapper.from(preparation);
        }
    }
}
