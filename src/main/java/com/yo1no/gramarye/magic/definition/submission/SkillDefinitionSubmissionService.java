package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.lookup.RegistryActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.RegistryTriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitConflict;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitResult;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated, server-thread composition of one normal player skill submission. */
public final class SkillDefinitionSubmissionService {
    private final Dependencies dependencies;

    SkillDefinitionSubmissionService(
            PlayerSkillAttachmentService attachments,
            SkillDefinitionStoreSubmissionPort storePort,
            SkillSubmissionPolicyProvider policyProvider,
            SkillSubmissionPreparationPipeline pipeline) {
        this(new ProductionDependencies(attachments, storePort, policyProvider, pipeline));
    }

    SkillDefinitionSubmissionService(Dependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    /** Creates the stateless production facade with lazy registry-backed definition lookups. */
    public static SkillDefinitionSubmissionService production(
            PlayerSkillAttachmentService attachments,
            SkillDefinitionStoreSubmissionPort storePort,
            SkillSubmissionPolicyProvider policyProvider) {
        var pipeline = new SkillSubmissionPreparationPipeline(
                new SkillCandidateResolver(
                        new RegistryTriggerTypeLookup(),
                        new RegistryActionTypeLookup()),
                new SkillValidationAnalyzer(
                        new NodeProjectionResolver(),
                        ProfileAvailabilityView.unknown()),
                new SkillDefinitionProjector());
        return new SkillDefinitionSubmissionService(
                attachments, storePort, policyProvider, pipeline);
    }

    /** Submits the player's authoritative Draft for the caller-known skill identity. */
    public SkillSubmissionCompositionOutcome submit(
            ServerPlayer player, SkillId skillId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(skillId, "skillId");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Skill submission requires the server thread");
        }
        return submitCore(
                player,
                server,
                new SkillOwnerId(player.getUUID()),
                skillId);
    }

    SkillSubmissionCompositionOutcome submitCore(
            Object playerIdentity,
            Object serverIdentity,
            SkillOwnerId owner,
            SkillId skillId) {
        Objects.requireNonNull(playerIdentity, "playerIdentity");
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(skillId, "skillId");

        var draftResult = Objects.requireNonNull(
                dependencies.findDraft(playerIdentity, skillId), "findDraft result");
        if (draftResult instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return beforePreparationUnavailable(skillId, unavailable.reason());
        }
        var draft = ((PlayerSkillAttachmentService.Available<Optional<SkillDraft>>) draftResult)
                .value();
        if (draft.isEmpty()) {
            return new SkillSubmissionCompositionOutcome.DraftUnavailable(skillId);
        }

        var precheck = Objects.requireNonNull(
                dependencies.precheck(draft.orElseThrow()), "precheck result");
        if (precheck instanceof DraftSubmissionPrecheck.Invalid invalid) {
            return new SkillSubmissionCompositionOutcome.PreparationRejected(
                    Objects.requireNonNull(dependencies.map(invalid), "mapped invalid outcome"));
        }
        var ready = (DraftSubmissionPrecheck.Ready) precheck;

        var authoritySnapshot = Objects.requireNonNull(
                dependencies.observeSubmissionAuthority(
                        serverIdentity, skillId, owner),
                "authority snapshot");
        if (authoritySnapshot
                instanceof SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Unavailable
                        unavailable) {
            return beforePreparationUnavailable(skillId, unavailable.reason());
        }
        var authorization = mapAuthorization(authoritySnapshot, owner, skillId);
        var authority = Objects.requireNonNull(
                dependencies.checkAuthority(ready, authorization),
                "authority result");
        if (authority instanceof SubmissionAuthorityCheck.IdentityRejected rejected) {
            return new SkillSubmissionCompositionOutcome.PreparationRejected(
                    Objects.requireNonNull(
                            dependencies.map(rejected), "mapped identity outcome"));
        }
        if (authority instanceof SubmissionAuthorityCheck.Conflict conflict) {
            return new SkillSubmissionCompositionOutcome.PreparationRejected(
                    Objects.requireNonNull(
                            dependencies.map(conflict), "mapped conflict outcome"));
        }
        var passed = (SubmissionAuthorityCheck.Passed) authority;

        SkillSubmissionPolicySnapshot policy;
        try {
            policy = dependencies.snapshotPolicy(serverIdentity);
        } catch (RuntimeException exception) {
            return new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                    skillId,
                    SkillSubmissionCompositionOutcome.BeforePreparationFailure
                            .policyProviderException(exception));
        }
        if (policy == null) {
            return new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                    skillId,
                    SkillSubmissionCompositionOutcome.BeforePreparationFailure.of(
                            SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                                    .POLICY_SNAPSHOT_NULL));
        }
        var quota = policy.quota();
        var context = policy.validationContext();

        var preparation = Objects.requireNonNull(
                dependencies.prepareAndMap(passed, context),
                "mapped preparation outcome");
        if (!(preparation instanceof SkillSubmissionOutcome.Prepared prepared)) {
            return new SkillSubmissionCompositionOutcome.PreparationRejected(preparation);
        }
        var plan = prepared.plan();
        ValidationResult report = prepared.report();
        var target = new SkillReference(
                plan.proposedDocument().skillId(),
                plan.proposedDocument().revision());

        var transitionResult = Objects.requireNonNull(
                dependencies.prepareLatestTransitionToCurrent(
                        playerIdentity, skillId, target),
                "transition preparation result");
        if (transitionResult instanceof UnavailableTransitionStep unavailable) {
            return afterPreparationUnavailable(
                    target,
                    attachmentFailure(unavailable.reason()),
                    report);
        }
        if (transitionResult instanceof RejectedTransitionStep rejected) {
            if (rejected.code()
                    == PlayerSkillAttachmentService.TransitionRejectionCode
                            .ATTACHMENT_CAPACITY_REJECTED) {
                return new SkillSubmissionCompositionOutcome.PersistenceCapacityRejected(
                        SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                                .ATTACHMENT_ENCODED,
                        report);
            }
            var failure = switch (rejected.code()) {
                case GENERATION_EXHAUSTED ->
                        SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                .GENERATION_EXHAUSTED;
                case GENERATION_MISMATCH,
                        POINTER_MISMATCH,
                        TARGET_ROUTE_MISMATCH ->
                        SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                .PLAN_TRANSITION_PAIRING_FAILURE;
                case ATTACHMENT_CAPACITY_REJECTED ->
                        throw new IllegalStateException("capacity rejection was already mapped");
            };
            return afterPreparationUnavailable(target, failure, report);
        }
        var transition = (PreparedTransitionStep) transitionResult;
        if (transition.noOp()) {
            return afterPreparationUnavailable(
                    target,
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .NORMAL_SUBMISSION_NO_OP,
                    report);
        }

        var storePreparation = Objects.requireNonNull(
                dependencies.prepareSubmissionCommit(
                        serverIdentity, plan, quota, transition.handle()),
                "Store preparation result");
        if (storePreparation instanceof UnavailableStorePreparationStep unavailable) {
            return afterPreparationUnavailable(
                    target, storeUnavailable(unavailable.reason()), report);
        }
        if (storePreparation instanceof RejectedStorePreparationStep rejected) {
            var capacity = persistenceCapacity(rejected.failure());
            if (capacity.isPresent()) {
                return new SkillSubmissionCompositionOutcome.PersistenceCapacityRejected(
                        capacity.orElseThrow(), report);
            }
            return afterPreparationUnavailable(
                    target, preparationFailure(rejected.failure()), report);
        }
        var storeHandle = ((PreparedStorePreparationStep) storePreparation).handle();

        var currentnessResult = Objects.requireNonNull(
                dependencies.checkPreparedTransitionCurrent(
                        playerIdentity, transition.handle()),
                "transition currentness result");
        if (currentnessResult
                instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return afterPreparationUnavailable(
                    target, attachmentFailure(unavailable.reason()), report);
        }
        var currentness = ((PlayerSkillAttachmentService.Available<
                        PlayerSkillAttachmentService.TransitionCurrentness>) currentnessResult)
                .value();
        if (currentness == PlayerSkillAttachmentService.TransitionCurrentness.STATE_CHANGED) {
            return afterPreparationUnavailable(
                    target,
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure
                            .ATTACHMENT_STATE_CHANGED,
                    report);
        }

        var commit = Objects.requireNonNull(
                dependencies.commitPreparedSubmission(serverIdentity, storeHandle),
                "Store commit result");
        var terminal = mapCommitResult(commit, target, report);
        if (terminal != null) {
            return terminal;
        }

        PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publication;
        try {
            publication = dependencies.publishPreparedTransition(
                    playerIdentity, transition.handle());
        } catch (RuntimeException exception) {
            return pendingAttachmentRecovery(
                    target,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailure
                            .runtime(exception),
                    report);
        }
        Objects.requireNonNull(publication, "Attachment publication result");
        if (publication instanceof PlayerSkillAttachmentService.Unavailable<?>) {
            return pendingAttachmentRecovery(
                    target,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(
                            SkillSubmissionCompositionOutcome
                                    .AttachmentPublicationFailureCode
                                    .ATTACHMENT_QUARANTINED),
                    report);
        }
        return switch (((PlayerSkillAttachmentService.Available<
                        PlayerSkillAttachmentService.MutationOutcome>) publication).value()) {
            case PlayerSkillAttachmentService.Applied ignored ->
                    new SkillSubmissionCompositionOutcome.Committed(target, report);
            case PlayerSkillAttachmentService.NoOp ignored -> pendingAttachmentRecovery(
                    target,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(
                            SkillSubmissionCompositionOutcome
                                    .AttachmentPublicationFailureCode.UNEXPECTED_NO_OP),
                    report);
            case PlayerSkillAttachmentService.MutationRejected ignored ->
                    pendingAttachmentRecovery(
                            target,
                            SkillSubmissionCompositionOutcome.AttachmentPublicationFailure.of(
                                    SkillSubmissionCompositionOutcome
                                            .AttachmentPublicationFailureCode.STATE_CHANGED),
                            report);
        };
    }

    private static SkillSubmissionAuthorizationResult mapAuthorization(
            SkillDefinitionStoreSubmissionPort.AuthoritySnapshot snapshot,
            SkillOwnerId owner,
            SkillId skillId) {
        return switch (snapshot) {
            case SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Absent absent ->
                    new SkillSubmissionAuthorizationResult.Authorized(
                            owner, new AuthorizedSkillState.New(absent.skillId()));
            case SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Owned owned ->
                    new SkillSubmissionAuthorizationResult.Authorized(
                            owner, new AuthorizedSkillState.Existing(owned.latest()));
            case SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.ForeignOwned ignored ->
                    new SkillSubmissionAuthorizationResult.Rejected(
                            skillId, SkillIdentityRejectionCode.NOT_AUTHORIZED);
            case SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Unavailable ignored ->
                    throw new IllegalStateException("Unavailable authority was already mapped");
        };
    }

    private static SkillSubmissionCompositionOutcome mapCommitResult(
            SkillDefinitionStoreSubmissionPort.SubmissionCommitResult result,
            SkillReference target,
            ValidationResult report) {
        return switch (result) {
            case SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed committed ->
                    committed.reference().equals(target)
                            ? null
                            : postCommitInvariant(target, report);
            case SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.DomainRejected rejected ->
                    mapDomainRejection(rejected.result(), report);
            case SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.PreparedBaseMismatch mismatch ->
                    switch (mismatch.code()) {
                        case AUTHORITY_CHANGED ->
                                new SkillSubmissionCompositionOutcome.CommitConflict(
                                        new SkillSubmissionCompositionOutcome
                                                .CommitConflictDetail.AuthorityChanged(
                                                        target.skillId()),
                                        report);
                        case STATE_IDENTITY_CHANGED -> afterPreparationUnavailable(
                                target,
                                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                        .STORE_JOURNAL_STATE_CHANGED,
                                report);
                        case TRANSITION_CHANGED -> afterPreparationUnavailable(
                                target,
                                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                                        .PLAN_TRANSITION_PAIRING_FAILURE,
                                report);
                    };
            case SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Unavailable unavailable ->
                    afterPreparationUnavailable(
                            target, storeUnavailable(unavailable.reason()), report);
            case SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                            .PostCommitInvariantFailure failure ->
                    switch (failure.code()) {
                        case POST_COMMIT_INVARIANT_FAILURE ->
                                postCommitInvariant(target, report);
                    };
        };
    }

    private static SkillSubmissionCompositionOutcome mapDomainRejection(
            SkillStoreCommitResult result, ValidationResult report) {
        return switch (result) {
            case SkillStoreCommitResult.Committed ignored ->
                    throw new IllegalStateException(
                            "DomainRejected cannot contain a committed result");
            case SkillStoreCommitResult.Conflict conflict ->
                    new SkillSubmissionCompositionOutcome.CommitConflict(
                            mapConflict(conflict.conflict()), report);
            case SkillStoreCommitResult.QuotaRejected rejected ->
                    new SkillSubmissionCompositionOutcome.QuotaRejected(
                            rejected.skillId(),
                            rejected.current(),
                            rejected.maximum(),
                            report);
            case SkillStoreCommitResult.CapacityRejected rejected ->
                    new SkillSubmissionCompositionOutcome.CapacityRejected(
                            rejected.scope(),
                            rejected.current(),
                            rejected.maximum(),
                            report);
            case SkillStoreCommitResult.OwnerRejected rejected ->
                    new SkillSubmissionCompositionOutcome.IdentityRejected(
                            rejected.skillId(),
                            SkillIdentityRejectionCode.NOT_AUTHORIZED,
                            report);
        };
    }

    private static SkillSubmissionCompositionOutcome.CommitConflictDetail mapConflict(
            SkillStoreCommitConflict conflict) {
        return switch (conflict) {
            case SkillStoreCommitConflict.ExpectedAbsentButPresent rejected ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail
                            .ExpectedAbsentButPresent(rejected.skillId());
            case SkillStoreCommitConflict.ExpectedLatestButAbsent rejected ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail
                            .ExpectedLatestButAbsent(rejected.expected());
            case SkillStoreCommitConflict.LatestMismatch rejected ->
                    new SkillSubmissionCompositionOutcome.CommitConflictDetail.LatestMismatch(
                            rejected.expected(), rejected.observed());
        };
    }

    private static Optional<SkillSubmissionCompositionOutcome.PersistenceCapacityScope>
            persistenceCapacity(
                    SkillDefinitionStoreSubmissionPort.PreparationFailure failure) {
        return switch (failure) {
            case DOCUMENT_BLOB_CAPACITY_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope.DOCUMENT_BLOB);
            case REVISION_BLOB_CAPACITY_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope.REVISION_BLOB);
            case HISTORY_BLOB_CAPACITY_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope.HISTORY_BLOB);
            case STORE_BLOB_CAPACITY_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope.STORE_BLOB);
            case JOURNAL_ENTRY_COUNT_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                            .JOURNAL_ENTRY_COUNT);
            case JOURNAL_ENCODED_CAPACITY_REJECTED -> Optional.of(
                    SkillSubmissionCompositionOutcome.PersistenceCapacityScope
                            .JOURNAL_ENCODED_BYTES);
            case TRANSITION_SERVER_MISMATCH,
                    NORMAL_SUBMISSION_NO_OP,
                    PLAN_TRANSITION_PAIRING_FAILURE,
                    AUTHORITY_PRECONDITION_MISMATCH,
                    STORE_CARRIER_INVARIANT_FAILURE,
                    JOURNAL_CHAIN_INVARIANT_FAILURE,
                    SAVED_DATA_CARRIER_INVARIANT_FAILURE -> Optional.empty();
        };
    }

    private static SkillSubmissionCompositionOutcome.AfterPreparationFailure preparationFailure(
            SkillDefinitionStoreSubmissionPort.PreparationFailure failure) {
        return switch (failure) {
            case TRANSITION_SERVER_MISMATCH,
                    PLAN_TRANSITION_PAIRING_FAILURE ->
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
                    throw new IllegalStateException("capacity rejection was already mapped");
        };
    }

    private static SkillSubmissionCompositionOutcome beforePreparationUnavailable(
            SkillId skillId,
            PlayerSkillAttachmentService.UnavailableReason reason) {
        var code = switch (reason) {
            case PRESERVED_RAW_QUARANTINE ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
            case OVERSIZE_QUARANTINE ->
                    SkillSubmissionCompositionOutcome.BeforePreparationFailureCode
                            .ATTACHMENT_OVERSIZE_QUARANTINE;
        };
        return new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                skillId,
                SkillSubmissionCompositionOutcome.BeforePreparationFailure.of(code));
    }

    private static SkillSubmissionCompositionOutcome beforePreparationUnavailable(
            SkillId skillId,
            SkillDefinitionStoreSubmissionPort.UnavailableReason reason) {
        var code = switch (reason) {
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
        return new SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation(
                skillId,
                SkillSubmissionCompositionOutcome.BeforePreparationFailure.of(code));
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
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure.JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure.STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE ->
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure.AUTHORITY_UNAVAILABLE;
        };
    }

    private static SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation
            afterPreparationUnavailable(
                    SkillReference target,
                    SkillSubmissionCompositionOutcome.AfterPreparationFailure failure,
                    ValidationResult report) {
        return new SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation(
                target,
                SkillSubmissionCompositionOutcome.AfterPreparationPhase.PRE_COMMIT,
                failure,
                report);
    }

    private static SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation
            postCommitInvariant(SkillReference target, ValidationResult report) {
        return new SkillSubmissionCompositionOutcome.SubsystemUnavailableAfterPreparation(
                target,
                SkillSubmissionCompositionOutcome.AfterPreparationPhase
                        .POST_COMMIT_STORE_COMMITTED,
                SkillSubmissionCompositionOutcome.AfterPreparationFailure
                        .STORE_JOURNAL_PUBLICATION_INVARIANT,
                report);
    }

    private static SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery
            pendingAttachmentRecovery(
                    SkillReference target,
                    SkillSubmissionCompositionOutcome.AttachmentPublicationFailure failure,
                    ValidationResult report) {
        return new SkillSubmissionCompositionOutcome.CommittedPendingAttachmentRecovery(
                target, failure, report);
    }

    sealed interface TransitionStep
            permits PreparedTransitionStep,
                    RejectedTransitionStep,
                    UnavailableTransitionStep {
    }

    record PreparedTransitionStep(Object handle, boolean noOp) implements TransitionStep {
        PreparedTransitionStep {
            Objects.requireNonNull(handle, "handle");
        }
    }

    record RejectedTransitionStep(
            PlayerSkillAttachmentService.TransitionRejectionCode code)
            implements TransitionStep {
        RejectedTransitionStep {
            Objects.requireNonNull(code, "code");
        }
    }

    record UnavailableTransitionStep(PlayerSkillAttachmentService.UnavailableReason reason)
            implements TransitionStep {
        UnavailableTransitionStep {
            Objects.requireNonNull(reason, "reason");
        }
    }

    sealed interface StorePreparationStep
            permits PreparedStorePreparationStep,
                    RejectedStorePreparationStep,
                    UnavailableStorePreparationStep {
    }

    record PreparedStorePreparationStep(Object handle) implements StorePreparationStep {
        PreparedStorePreparationStep {
            Objects.requireNonNull(handle, "handle");
        }
    }

    record RejectedStorePreparationStep(
            SkillDefinitionStoreSubmissionPort.PreparationFailure failure)
            implements StorePreparationStep {
        RejectedStorePreparationStep {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record UnavailableStorePreparationStep(
            SkillDefinitionStoreSubmissionPort.UnavailableReason reason)
            implements StorePreparationStep {
        UnavailableStorePreparationStep {
            Objects.requireNonNull(reason, "reason");
        }
    }

    interface Dependencies {
        PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object playerIdentity, SkillId skillId);

        DraftSubmissionPrecheck precheck(SkillDraft draft);

        SkillDefinitionStoreSubmissionPort.AuthoritySnapshot observeSubmissionAuthority(
                Object serverIdentity, SkillId skillId, SkillOwnerId owner);

        SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization);

        SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid);

        SkillSubmissionOutcome map(SubmissionAuthorityCheck.IdentityRejected rejected);

        SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict);

        SkillSubmissionPolicySnapshot snapshotPolicy(Object serverIdentity);

        SkillSubmissionOutcome prepareAndMap(
                SubmissionAuthorityCheck.Passed passed,
                com.yo1no.gramarye.magic.validation.ValidationContext context);

        TransitionStep prepareLatestTransitionToCurrent(
                Object playerIdentity, SkillId skillId, SkillReference target);

        StorePreparationStep prepareSubmissionCommit(
                Object serverIdentity,
                SkillSubmissionPlan plan,
                com.yo1no.gramarye.magic.definition.store.SkillQuota quota,
                Object transitionHandle);

        PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.TransitionCurrentness>
                checkPreparedTransitionCurrent(
                        Object playerIdentity, Object transitionHandle);

        SkillDefinitionStoreSubmissionPort.SubmissionCommitResult commitPreparedSubmission(
                Object serverIdentity, Object storeHandle);

        PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publishPreparedTransition(Object playerIdentity, Object transitionHandle);
    }

    static final class ProductionDependencies implements Dependencies {
        private final PlayerSkillAttachmentService attachments;
        private final SkillDefinitionStoreSubmissionPort storePort;
        private final SkillSubmissionPolicyProvider policyProvider;
        private final SkillSubmissionPreparationPipeline pipeline;

        ProductionDependencies(
                PlayerSkillAttachmentService attachments,
                SkillDefinitionStoreSubmissionPort storePort,
                SkillSubmissionPolicyProvider policyProvider,
                SkillSubmissionPreparationPipeline pipeline) {
            this.attachments = Objects.requireNonNull(attachments, "attachments");
            this.storePort = Objects.requireNonNull(storePort, "storePort");
            this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider");
            this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        }

        @Override
        public PlayerSkillAttachmentService.Result<Optional<SkillDraft>> findDraft(
                Object playerIdentity, SkillId skillId) {
            return attachments.findDraft((ServerPlayer) playerIdentity, skillId);
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillDraft draft) {
            return pipeline.precheck(draft);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.AuthoritySnapshot
                observeSubmissionAuthority(
                        Object serverIdentity, SkillId skillId, SkillOwnerId owner) {
            return storePort.observeSubmissionAuthority(
                    (MinecraftServer) serverIdentity, skillId, owner);
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
            return policyProvider.snapshot((MinecraftServer) serverIdentity);
        }

        @Override
        public SkillSubmissionOutcome prepareAndMap(
                SubmissionAuthorityCheck.Passed passed,
                com.yo1no.gramarye.magic.validation.ValidationContext context) {
            return pipeline.prepareAndMap(passed, context);
        }

        @Override
        public TransitionStep prepareLatestTransitionToCurrent(
                Object playerIdentity, SkillId skillId, SkillReference target) {
            var result = attachments.prepareLatestTransitionToCurrent(
                    (ServerPlayer) playerIdentity, skillId, target);
            if (result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
                return new UnavailableTransitionStep(unavailable.reason());
            }
            var preparation = ((PlayerSkillAttachmentService.Available<
                    PlayerSkillAttachmentService.TransitionPreparation>) result).value();
            return switch (preparation) {
                case PlayerSkillAttachmentService.Prepared prepared ->
                        new PreparedTransitionStep(
                                prepared.transition(), prepared.transition().isNoOp());
                case PlayerSkillAttachmentService.TransitionRejected rejected ->
                        new RejectedTransitionStep(rejected.code());
            };
        }

        @Override
        public StorePreparationStep prepareSubmissionCommit(
                Object serverIdentity,
                SkillSubmissionPlan plan,
                com.yo1no.gramarye.magic.definition.store.SkillQuota quota,
                Object transitionHandle) {
            var result = storePort.prepareSubmissionCommit(
                    (MinecraftServer) serverIdentity,
                    plan,
                    quota,
                    (PlayerSkillAttachmentService.PreparedPlayerSkillTransition)
                            transitionHandle);
            return switch (result) {
                case SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Prepared
                                prepared ->
                        new PreparedStorePreparationStep(prepared.handle());
                case SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Rejected
                                rejected ->
                        new RejectedStorePreparationStep(rejected.failure());
                case SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Unavailable
                                unavailable ->
                        new UnavailableStorePreparationStep(unavailable.reason());
            };
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        PlayerSkillAttachmentService.TransitionCurrentness>
                checkPreparedTransitionCurrent(
                        Object playerIdentity, Object transitionHandle) {
            return attachments.checkPreparedTransitionCurrent(
                    (ServerPlayer) playerIdentity,
                    (PlayerSkillAttachmentService.PreparedPlayerSkillTransition)
                            transitionHandle);
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                commitPreparedSubmission(Object serverIdentity, Object storeHandle) {
            return storePort.commitPreparedSubmission(
                    (MinecraftServer) serverIdentity,
                    (SkillDefinitionStoreSubmissionPort.PreparedStoreSubmissionCommit)
                            storeHandle);
        }

        @Override
        public PlayerSkillAttachmentService.Result<PlayerSkillAttachmentService.MutationOutcome>
                publishPreparedTransition(Object playerIdentity, Object transitionHandle) {
            return attachments.publishPreparedTransition(
                    (ServerPlayer) playerIdentity,
                    (PlayerSkillAttachmentService.PreparedPlayerSkillTransition)
                            transitionHandle);
        }
    }
}
