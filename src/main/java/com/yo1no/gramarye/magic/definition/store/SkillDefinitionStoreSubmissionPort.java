package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillCommitPrecondition;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * Narrow server-thread Store submission and pending-journal port.
 *
 * <p>The port exposes bounded observations and single-use prepared handles only. It never exposes
 * the live Store, SavedData state, encoded carriers, or pending bytes.</p>
 */
public final class SkillDefinitionStoreSubmissionPort {
    private static final StoreCommitInvoker PRODUCTION_STORE_COMMIT =
            (store, plan, quota) -> store.commit(plan, quota);

    private final SkillDefinitionStoreService service;

    SkillDefinitionStoreSubmissionPort(SkillDefinitionStoreService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public AuthoritySnapshot observeSubmissionAuthority(
            MinecraftServer server, SkillId skillId, SkillOwnerId requester) {
        SkillDefinitionStoreService.requireServerThread(server);
        return observeSubmissionAuthorityCore(
                service.installedAdapter(server), skillId, requester);
    }

    AuthoritySnapshot observeSubmissionAuthorityCore(
            GramaryeSkillSavedData adapter, SkillId skillId, SkillOwnerId requester) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(requester, "requester");
        var access = readyAccess(adapter);
        if (access instanceof AccessUnavailable unavailable) {
            return new AuthoritySnapshot.Unavailable(unavailable.reason());
        }
        var ready = (AccessReady) access;
        return switch (ready.savedDataReady().store()
                .observeSubmissionAuthority(skillId, requester)) {
            case StoreSubmissionAuthorityObservation.Absent absent ->
                    new AuthoritySnapshot.Absent(absent.skillId());
            case StoreSubmissionAuthorityObservation.Owned owned ->
                    new AuthoritySnapshot.Owned(owned.latest());
            case StoreSubmissionAuthorityObservation.ForeignOwned foreign ->
                    new AuthoritySnapshot.ForeignOwned(foreign.skillId());
        };
    }

    public BootstrapResult bootstrapJournal(MinecraftServer server) {
        SkillDefinitionStoreService.requireServerThread(server);
        var adapter = service.installedAdapter(server);
        return bootstrapJournalCore(adapter);
    }

    BootstrapResult bootstrapJournalCore(GramaryeSkillSavedData adapter) {
        return bootstrapJournalCore(
                adapter,
                PendingAttachmentJournalFraming::load,
                (expected, replacement, markDirty) -> {
                    adapter.publishState(expected, replacement);
                    if (markDirty) {
                        adapter.setDirty();
                    }
                });
    }

    BootstrapResult bootstrapJournalCore(
            GramaryeSkillSavedData adapter,
            BootstrapJournalLoader loader,
            BootstrapPublisher publisher) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(publisher, "publisher");
        var state = adapter.state();
        if (!(state instanceof SkillSavedDataState.Ready savedDataReady)) {
            return new BootstrapResult.Unavailable(UnavailableReason.STORE_UNAVAILABLE);
        }
        if (!(savedDataReady.journalLifecycle()
                instanceof PendingAttachmentJournalLifecycle.Uninitialized)) {
            throw new SkillSubsystemLifecycleException(
                    SkillSubsystemLifecycleException.Code.JOURNAL_BOOTSTRAP_ALREADY_INSTALLED);
        }

        var sourcePending = savedDataReady.innerCarrier().pending();
        var loaded = Objects.requireNonNull(loader.load(sourcePending), "loaded");
        if (loaded instanceof PendingAttachmentJournalLoadResult.Rejected rejected) {
            var unavailable = new PendingAttachmentJournalState.Unavailable(
                    new PendingAttachmentJournalOperationalFailure.Persistence(
                            rejected.failure()));
            publisher.publish(
                    savedDataReady,
                    savedDataReady.withJournalLifecycle(
                            new PendingAttachmentJournalLifecycle.Installed(unavailable)),
                    false);
            return new BootstrapResult.Unavailable(UnavailableReason.JOURNAL_UNAVAILABLE);
        }

        var candidate = ((PendingAttachmentJournalLoadResult.Loaded) loaded).candidate();
        JournalTargetAuditProof.AuditedExisting proof;
        if (candidate.journal().entryCount() == 0) {
            proof = new JournalTargetAuditProof.AuditedExisting(candidate.journal());
        } else {
            var audited = savedDataReady.store().auditJournalTargets(candidate.journal());
            if (audited instanceof JournalTargetAuditResult.Rejected rejected) {
                var unavailable = new PendingAttachmentJournalState.Unavailable(
                        new PendingAttachmentJournalOperationalFailure.TargetAudit(
                                rejected.failure()));
                publisher.publish(
                        savedDataReady,
                        savedDataReady.withJournalLifecycle(
                                new PendingAttachmentJournalLifecycle.Installed(unavailable)),
                        false);
                return new BootstrapResult.Unavailable(UnavailableReason.JOURNAL_UNAVAILABLE);
            }
            proof = ((JournalTargetAuditResult.Audited) audited).proof();
        }

        var journalReady = new PendingAttachmentJournalState.Ready(
                candidate.journal(),
                candidate.encoded(),
                candidate.rewriteRequired()
                        ? candidate.encoded().pending()
                        : candidate.sourcePending(),
                candidate.rewriteRequired(),
                proof);
        var lifecycle = new PendingAttachmentJournalLifecycle.Installed(journalReady);
        SkillSavedDataState.Ready replacement;
        if (candidate.rewriteRequired()) {
            SkillSavedDataInnerCarrier replacementInner;
            try {
                replacementInner = innerCarrier(
                        savedDataReady.storeCarrier(), candidate.encoded().pending());
            } catch (IllegalArgumentException | ArithmeticException exception) {
                var unavailable = new PendingAttachmentJournalState.Unavailable(
                        new PendingAttachmentJournalOperationalFailure.Persistence(
                                PendingAttachmentJournalFailure.simple(
                                        PendingAttachmentJournalFailure.Code
                                                .CARRIER_INVARIANT_FAILURE)));
                publisher.publish(
                        savedDataReady,
                        savedDataReady.withJournalLifecycle(
                                new PendingAttachmentJournalLifecycle.Installed(unavailable)),
                        false);
                return new BootstrapResult.Unavailable(
                        UnavailableReason.JOURNAL_UNAVAILABLE);
            }
            replacement = savedDataReady.afterJournalPublication(
                    replacementInner, lifecycle);
        } else {
            replacement = savedDataReady.withJournalLifecycle(lifecycle);
        }
        var result = new BootstrapResult.Ready(
                candidate.journal().entryCount(), candidate.rewriteRequired());
        publisher.publish(savedDataReady, replacement, candidate.rewriteRequired());
        return result;
    }

    public JournalStatus journalStatus(MinecraftServer server) {
        SkillDefinitionStoreService.requireServerThread(server);
        return journalStatusCore(service.installedAdapter(server));
    }

    JournalStatus journalStatusCore(GramaryeSkillSavedData adapter) {
        var access = readyAccess(Objects.requireNonNull(adapter, "adapter"));
        if (access instanceof AccessUnavailable unavailable) {
            return new JournalStatus.Unavailable(unavailable.reason());
        }
        var journal = ((AccessReady) access).journalReady().journal();
        return new JournalStatus.Ready(journal.entryCount());
    }

    public JournalRootProjection journalRoots(MinecraftServer server) {
        SkillDefinitionStoreService.requireServerThread(server);
        return journalRootsCore(service.installedAdapter(server));
    }

    JournalRootProjection journalRootsCore(GramaryeSkillSavedData adapter) {
        var access = readyAccess(Objects.requireNonNull(adapter, "adapter"));
        if (access instanceof AccessUnavailable unavailable) {
            return new JournalRootProjection.Unavailable(unavailable.reason());
        }
        return new JournalRootProjection.Available(
                ((AccessReady) access).journalReady().journal().targetReferences());
    }

    public SubmissionPreparationResult prepareSubmissionCommit(
            MinecraftServer server,
            SkillSubmissionPlan plan,
            SkillQuota quota,
            PlayerSkillAttachmentService.PreparedPlayerSkillTransition transition) {
        SkillDefinitionStoreService.requireServerThread(server);
        var adapter = service.installedAdapter(server);
        return prepareSubmissionCommitCore(
                server,
                adapter,
                plan,
                quota,
                TransitionView.production(server, transition));
    }

    SubmissionPreparationResult prepareSubmissionCommitCore(
            Object serverIdentity,
            GramaryeSkillSavedData adapter,
            SkillSubmissionPlan plan,
            SkillQuota quota,
            TransitionView transition) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(transition, "transition");
        var access = readyAccess(adapter);
        if (access instanceof AccessUnavailable unavailable) {
            return new SubmissionPreparationResult.Unavailable(unavailable.reason());
        }
        var ready = (AccessReady) access;
        var pairing = validatePairing(serverIdentity, plan, transition);
        if (pairing.isPresent()) {
            return new SubmissionPreparationResult.Rejected(pairing.orElseThrow());
        }
        if (!authorityMatchesPlan(ready.savedDataReady().store(), plan)) {
            return new SubmissionPreparationResult.Rejected(
                    PreparationFailure.AUTHORITY_PRECONDITION_MISMATCH);
        }

        CarrierUpdateResult carrierResult;
        try {
            carrierResult = SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                    ready.savedDataReady().storeCarrier(), plan);
        } catch (CarrierInvariantException exception) {
            return new SubmissionPreparationResult.Rejected(
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE);
        }
        if (carrierResult instanceof CarrierUpdateResult.Failure failure) {
            return new SubmissionPreparationResult.Rejected(
                    mapStoreCarrierFailure(failure.failure()));
        }
        var carrierUpdate = ((CarrierUpdateResult.Prepared) carrierResult).update();
        var target = transition.targetPointer().orElseThrow();
        var appended = ready.journalReady().journal().append(new PendingAttachmentJournalEntry(
                plan.owner(),
                target.skillId(),
                transition.expectedGeneration(),
                transition.targetGeneration(),
                transition.expectedPointer(),
                target));
        if (appended instanceof PendingAttachmentJournal.DomainMutation.Rejected rejected) {
            return new SubmissionPreparationResult.Rejected(
                    mapJournalFailure(rejected.failure()));
        }
        var prospectiveJournal = ((PendingAttachmentJournal.DomainMutation.Updated) appended)
                .journal();
        var encodedResult = PendingAttachmentJournalFraming.encode(prospectiveJournal);
        if (encodedResult
                instanceof PendingAttachmentJournalFraming.JournalEncodingResult.Rejected
                        rejected) {
            return new SubmissionPreparationResult.Rejected(
                    mapJournalFailure(rejected.failure()));
        }
        var encoded = ((PendingAttachmentJournalFraming.JournalEncodingResult.Encoded)
                encodedResult).journal();

        SkillSavedDataInnerCarrier prospectiveInner;
        try {
            prospectiveInner = innerCarrier(
                    carrierUpdate.prospectiveCarrier(), encoded.pending());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return new SubmissionPreparationResult.Rejected(
                    PreparationFailure.SAVED_DATA_CARRIER_INVARIANT_FAILURE);
        }
        var proof = new JournalTargetAuditProof.ConditionalOnExactCommit(
                ready.journalReady(),
                carrierUpdate,
                prospectiveJournal,
                plan.owner(),
                target.skillId(),
                target,
                carrierUpdate.proposedReference());
        var prospectiveJournalReady = new PendingAttachmentJournalState.Ready(
                prospectiveJournal,
                encoded,
                encoded.pending(),
                false,
                proof);
        var prospectiveSavedDataReady = ready.savedDataReady().afterJournalPublication(
                prospectiveInner,
                new PendingAttachmentJournalLifecycle.Installed(prospectiveJournalReady));
        var committed = new SubmissionCommitResult.Committed(target);
        var postCommitFailure = new SubmissionCommitResult.PostCommitInvariantFailure(
                PostCommitFailureCode.POST_COMMIT_INVARIANT_FAILURE);
        var fallback = new SkillSavedDataState.Unavailable(
                SkillSavedDataRuntimeFailure.submissionPostCommitInvariant());
        var handle = new PreparedStoreSubmissionCommit(
                this,
                new SubmissionPayload(
                        serverIdentity,
                        ready.adapter(),
                        ready.savedDataReady(),
                        ready.savedDataReady().storeCarrier(),
                        ready.savedDataReady().innerCarrier().pending(),
                        ready.journalReady(),
                        plan,
                        quota,
                        transition,
                        carrierUpdate,
                        proof,
                        prospectiveJournalReady,
                        prospectiveSavedDataReady,
                        fallback,
                        committed,
                        postCommitFailure));
        return new SubmissionPreparationResult.Prepared(handle);
    }

    public SubmissionCommitResult commitPreparedSubmission(
            MinecraftServer server, PreparedStoreSubmissionCommit handle) {
        SkillDefinitionStoreService.requireServerThread(server);
        return commitPreparedSubmissionCore(
                server,
                handle,
                () -> service.installedAdapter(server),
                PRODUCTION_STORE_COMMIT);
    }

    SubmissionCommitResult commitPreparedSubmissionCore(
            Object serverIdentity,
            PreparedStoreSubmissionCommit handle,
            AdapterResolver adapterResolver,
            StoreCommitInvoker commitInvoker) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(adapterResolver, "adapterResolver");
        Objects.requireNonNull(commitInvoker, "commitInvoker");
        handle.requireOwner(this, serverIdentity);
        handle.requireTransitionBinding(serverIdentity);
        var payload = handle.consume();
        var installedAdapter = Objects.requireNonNull(
                adapterResolver.resolve(), "installedAdapter");
        if (!submissionBaseMatches(payload, installedAdapter)) {
            return new SubmissionCommitResult.PreparedBaseMismatch(
                    PreparedBaseMismatchCode.STATE_IDENTITY_CHANGED);
        }
        if (!authorityMatchesPlan(payload.baseReady().store(), payload.plan())) {
            return new SubmissionCommitResult.PreparedBaseMismatch(
                    PreparedBaseMismatchCode.AUTHORITY_CHANGED);
        }
        if (!transitionSnapshotMatches(
                payload.transition(), payload.plan())) {
            return new SubmissionCommitResult.PreparedBaseMismatch(
                    PreparedBaseMismatchCode.TRANSITION_CHANGED);
        }

        var commitResult = Objects.requireNonNull(
                commitInvoker.commit(
                        payload.baseReady().store(), payload.plan(), payload.quota()),
                "commitResult");
        if (!(commitResult instanceof SkillStoreCommitResult.Committed committed)) {
            return new SubmissionCommitResult.DomainRejected(commitResult);
        }
        var conditional = payload.conditionalProof();
        if (!conditional.isFor(payload.prospectiveJournalReady().journal())
                || !prospectiveStateMatches(payload)
                || !payload.committedResult().reference().equals(committed.committed())
                || !conditional.satisfy(
                        payload.baseJournalReady(),
                        payload.carrierUpdate(),
                        payload.plan().owner(),
                        payload.plan().precondition().skillId(),
                        payload.transition().targetPointer().orElseThrow(),
                        committed.committed(),
                        payload.prospectiveJournalReady().targetAuditProof())
                || !conditional.isSatisfied()) {
            payload.adapter().publishState(payload.baseReady(), payload.fallbackState());
            payload.adapter().setDirty(false);
            return payload.postCommitFailure();
        }

        payload.adapter().publishState(payload.baseReady(), payload.prospectiveReady());
        payload.adapter().setDirty();
        return payload.committedResult();
    }

    public JournalClearPreparationResult prepareJournalPrefixClear(
            MinecraftServer server,
            SkillOwnerId owner,
            SkillId skillId,
            int confirmedTargetGeneration,
            SkillReference confirmedTargetPointer) {
        SkillDefinitionStoreService.requireServerThread(server);
        return prepareJournalPrefixClearCore(
                server,
                service.installedAdapter(server),
                owner,
                skillId,
                confirmedTargetGeneration,
                confirmedTargetPointer);
    }

    JournalClearPreparationResult prepareJournalPrefixClearCore(
            Object serverIdentity,
            GramaryeSkillSavedData adapter,
            SkillOwnerId owner,
            SkillId skillId,
            int confirmedTargetGeneration,
            SkillReference confirmedTargetPointer) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(confirmedTargetPointer, "confirmedTargetPointer");
        if (confirmedTargetGeneration < 0
                || !skillId.equals(confirmedTargetPointer.skillId())) {
            return new JournalClearPreparationResult.Rejected(
                    JournalClearFailure.PREFIX_TARGET_MISMATCH);
        }
        var access = readyAccess(adapter);
        if (access instanceof AccessUnavailable unavailable) {
            return new JournalClearPreparationResult.Unavailable(unavailable.reason());
        }
        var ready = (AccessReady) access;
        var cleared = ready.journalReady().journal().clearPrefix(
                owner, skillId, confirmedTargetGeneration, confirmedTargetPointer);
        if (cleared instanceof PendingAttachmentJournal.PrefixClear.NoChain) {
            return JournalClearPreparationResult.NoOp.INSTANCE;
        }
        if (cleared instanceof PendingAttachmentJournal.PrefixClear.TargetMismatch) {
            return new JournalClearPreparationResult.Rejected(
                    JournalClearFailure.PREFIX_TARGET_MISMATCH);
        }
        var result = (PendingAttachmentJournal.PrefixClear.Cleared) cleared;
        var encodedResult = PendingAttachmentJournalFraming.encode(result.journal());
        if (encodedResult instanceof PendingAttachmentJournalFraming.JournalEncodingResult.Rejected) {
            return new JournalClearPreparationResult.Rejected(
                    JournalClearFailure.CARRIER_INVARIANT_FAILURE);
        }
        var encoded = ((PendingAttachmentJournalFraming.JournalEncodingResult.Encoded)
                encodedResult).journal();
        SkillSavedDataInnerCarrier prospectiveInner;
        try {
            prospectiveInner = innerCarrier(
                    ready.savedDataReady().storeCarrier(), encoded.pending());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return new JournalClearPreparationResult.Rejected(
                    JournalClearFailure.CARRIER_INVARIANT_FAILURE);
        }
        var prospectiveJournalReady = new PendingAttachmentJournalState.Ready(
                result.journal(),
                encoded,
                encoded.pending(),
                false,
                new JournalTargetAuditProof.AuditedExisting(result.journal()));
        var prospectiveReady = ready.savedDataReady().afterJournalPublication(
                prospectiveInner,
                new PendingAttachmentJournalLifecycle.Installed(prospectiveJournalReady));
        var handle = new PreparedJournalPrefixClear(
                this,
                new ClearPayload(
                        serverIdentity,
                        ready.adapter(),
                        ready.savedDataReady(),
                        ready.savedDataReady().innerCarrier().pending(),
                        ready.journalReady(),
                        prospectiveReady,
                        new JournalClearCommitResult.Cleared(result.entriesRemoved())));
        return new JournalClearPreparationResult.Prepared(handle);
    }

    public JournalClearCommitResult commitPreparedJournalClear(
            MinecraftServer server, PreparedJournalPrefixClear handle) {
        SkillDefinitionStoreService.requireServerThread(server);
        return commitPreparedJournalClearCore(
                server, handle, () -> service.installedAdapter(server));
    }

    JournalClearCommitResult commitPreparedJournalClearCore(
            Object serverIdentity,
            PreparedJournalPrefixClear handle,
            AdapterResolver adapterResolver) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(adapterResolver, "adapterResolver");
        handle.requireOwner(this, serverIdentity);
        var payload = handle.consume();
        var installedAdapter = Objects.requireNonNull(
                adapterResolver.resolve(), "installedAdapter");
        if (installedAdapter != payload.adapter()
                || payload.adapter().state() != payload.baseReady()
                || payload.baseReady().innerCarrier().pending() != payload.basePending()
                || !(payload.baseReady().journalLifecycle()
                        instanceof PendingAttachmentJournalLifecycle.Installed installed)
                || installed.state() != payload.baseJournalReady()) {
            return new JournalClearCommitResult.PreparedBaseMismatch(
                    PreparedBaseMismatchCode.STATE_IDENTITY_CHANGED);
        }
        payload.adapter().publishState(payload.baseReady(), payload.prospectiveReady());
        payload.adapter().setDirty();
        return payload.result();
    }

    private static Access readyAccess(GramaryeSkillSavedData adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (!(adapter.state() instanceof SkillSavedDataState.Ready savedDataReady)) {
            return new AccessUnavailable(UnavailableReason.STORE_UNAVAILABLE);
        }
        if (savedDataReady.journalLifecycle()
                instanceof PendingAttachmentJournalLifecycle.Uninitialized) {
            return new AccessUnavailable(UnavailableReason.JOURNAL_NOT_BOOTSTRAPPED);
        }
        var installed = (PendingAttachmentJournalLifecycle.Installed)
                savedDataReady.journalLifecycle();
        if (!(installed.state() instanceof PendingAttachmentJournalState.Ready journalReady)) {
            return new AccessUnavailable(UnavailableReason.JOURNAL_UNAVAILABLE);
        }
        if (journalReady.sourcePending() != savedDataReady.innerCarrier().pending()) {
            return new AccessUnavailable(UnavailableReason.JOURNAL_UNAVAILABLE);
        }
        return new AccessReady(adapter, savedDataReady, journalReady);
    }

    private static Optional<PreparationFailure> validatePairing(
            Object serverIdentity,
            SkillSubmissionPlan plan,
            TransitionView transition) {
        if (!transition.isBoundTo(serverIdentity)) {
            return Optional.of(PreparationFailure.TRANSITION_SERVER_MISMATCH);
        }
        if (transition.isNoOp()) {
            return Optional.of(PreparationFailure.NORMAL_SUBMISSION_NO_OP);
        }
        if (transition.targetPointer().isEmpty()) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        var target = transition.targetPointer().orElseThrow();
        if (!plan.owner().equals(transition.owner())) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        if (!plan.precondition().skillId().equals(transition.skillId())) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        if (!plan.proposedDocument().skillId().equals(target.skillId())
                || !plan.proposedDocument().revision().equals(target.revision())) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        var preconditionPointer = switch (plan.precondition()) {
            case SkillCommitPrecondition.ExpectedAbsent ignored -> Optional.<SkillReference>empty();
            case SkillCommitPrecondition.ExpectedLatest expected -> Optional.of(expected.latest());
        };
        if (!transition.expectedPointer().equals(preconditionPointer)) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        if (!PlayerSkillAttachmentService.isChangedGenerationSuccessor(
                transition.expectedGeneration(), transition.targetGeneration())) {
            return Optional.of(PreparationFailure.PLAN_TRANSITION_PAIRING_FAILURE);
        }
        return Optional.empty();
    }

    static PreparationFailure mapStoreCarrierFailure(StorePersistenceFailure failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure) {
            case StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded ignored ->
                    PreparationFailure.DOCUMENT_BLOB_CAPACITY_REJECTED;
            case StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded ignored ->
                    PreparationFailure.REVISION_BLOB_CAPACITY_REJECTED;
            case StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded ignored ->
                    PreparationFailure.HISTORY_BLOB_CAPACITY_REJECTED;
            case StorePersistenceFailure.StoreBlobEncodedCapacityExceeded ignored ->
                    PreparationFailure.STORE_BLOB_CAPACITY_REJECTED;
            case StorePersistenceFailure.MalformedStoreEnvelope ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.MalformedHistoryEnvelope ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.MalformedRevisionEnvelope ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.UnsupportedStoreSchema ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.UnsupportedDocumentEncoding ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.StoreEnvelopeMigrationFailed ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.DocumentMigrationFailed ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.OpaqueTokenInvariantViolation ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.DocumentDecodeFailed ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.RegistryContextUnavailable ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.StoreRestoreRejected ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
            case StorePersistenceFailure.EncodeFailed ignored ->
                    PreparationFailure.STORE_CARRIER_INVARIANT_FAILURE;
        };
    }

    static PreparationFailure mapJournalFailure(PendingAttachmentJournalFailure failure) {
        Objects.requireNonNull(failure, "failure");
        return switch (failure.code()) {
            case ENTRY_COUNT_EXCEEDED ->
                    PreparationFailure.JOURNAL_ENTRY_COUNT_REJECTED;
            case ENCODED_CAPACITY_EXCEEDED ->
                    PreparationFailure.JOURNAL_ENCODED_CAPACITY_REJECTED;
            case MALFORMED_ROOT,
                    DUPLICATE_PHYSICAL_FIELD,
                    MISSING_FIELD,
                    UNKNOWN_FIELD,
                    WRONG_TAG_TYPE,
                    TRAILING_DATA,
                    UNSUPPORTED_SCHEMA,
                    MISSING_MIGRATION_EDGE,
                    MIGRATION_EXCEPTION,
                    MIGRATION_PARTIAL,
                    GENERATION_INVALID,
                    GENERATION_EXHAUSTED,
                    POINTER_ROUTE_MISMATCH,
                    DUPLICATE_STABLE_KEY,
                    BROKEN_GENERATION_CHAIN,
                    BROKEN_POINTER_CHAIN,
                    TARGET_MISSING,
                    TARGET_OWNER_MISMATCH,
                    JOURNAL_NOT_BOOTSTRAPPED,
                    JOURNAL_UNAVAILABLE,
                    STORE_UNAVAILABLE,
                    AUTHORITY_UNAVAILABLE,
                    BOOTSTRAP_ALREADY_INSTALLED,
                    PREPARED_BASE_MISMATCH,
                    PREPARED_ALREADY_CONSUMED,
                    PREFIX_TARGET_MISMATCH,
                    CARRIER_INVARIANT_FAILURE,
                    POST_COMMIT_INVARIANT_FAILURE,
                    TRANSITION_SERVER_MISMATCH ->
                    PreparationFailure.JOURNAL_CHAIN_INVARIANT_FAILURE;
        };
    }

    private static boolean authorityMatchesPlan(
            SkillDefinitionStore store, SkillSubmissionPlan plan) {
        var observed = store.observeSubmissionAuthority(
                plan.precondition().skillId(), plan.owner());
        return switch (plan.precondition()) {
            case SkillCommitPrecondition.ExpectedAbsent ignored ->
                    observed instanceof StoreSubmissionAuthorityObservation.Absent;
            case SkillCommitPrecondition.ExpectedLatest expected ->
                    observed instanceof StoreSubmissionAuthorityObservation.Owned owned
                            && owned.latest().equals(expected.latest());
        };
    }

    private static boolean transitionSnapshotMatches(
            TransitionView transition,
            SkillSubmissionPlan plan) {
        var preconditionPointer = switch (plan.precondition()) {
            case SkillCommitPrecondition.ExpectedAbsent ignored -> Optional.<SkillReference>empty();
            case SkillCommitPrecondition.ExpectedLatest expected -> Optional.of(expected.latest());
        };
        return transition.matchesSource()
                && transition.owner().equals(plan.owner())
                && transition.skillId().equals(plan.precondition().skillId())
                && transition.expectedPointer().equals(preconditionPointer)
                && transition.targetPointer().isPresent()
                && transition.targetPointer().orElseThrow().equals(
                        new SkillReference(
                                plan.proposedDocument().skillId(),
                                plan.proposedDocument().revision()))
                && PlayerSkillAttachmentService.isChangedGenerationSuccessor(
                        transition.expectedGeneration(), transition.targetGeneration());
    }

    private static boolean submissionBaseMatches(
            SubmissionPayload payload, GramaryeSkillSavedData installedAdapter) {
        if (payload.adapter() != installedAdapter
                || payload.adapter().state() != payload.baseReady()
                || payload.baseReady().storeCarrier() != payload.baseCarrier()
                || payload.baseReady().innerCarrier().pending() != payload.basePending()) {
            return false;
        }
        if (!(payload.baseReady().journalLifecycle()
                instanceof PendingAttachmentJournalLifecycle.Installed installed)) {
            return false;
        }
        return installed.state() == payload.baseJournalReady()
                && payload.baseJournalReady().sourcePending() == payload.basePending()
                && payload.carrierUpdate().isFor(payload.baseCarrier());
    }

    private static boolean prospectiveStateMatches(SubmissionPayload payload) {
        if (payload.prospectiveReady().store() != payload.baseReady().store()
                || payload.prospectiveReady().storeCarrier()
                        != payload.carrierUpdate().prospectiveCarrier()
                || payload.prospectiveReady().innerCarrier().pending()
                        != payload.prospectiveJournalReady().encoded().pending()) {
            return false;
        }
        if (!(payload.prospectiveReady().journalLifecycle()
                instanceof PendingAttachmentJournalLifecycle.Installed installed)) {
            return false;
        }
        return installed.state() == payload.prospectiveJournalReady()
                && payload.prospectiveJournalReady().sourcePending()
                        == payload.prospectiveReady().innerCarrier().pending();
    }

    private static SkillSavedDataInnerCarrier innerCarrier(
            EncodedSkillStoreCarrier storeCarrier,
            OpaquePendingAttachmentUpdatesBlob pending) {
        var encodedByteCount = Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(storeCarrier.storeByteCount(), pending.byteCount()));
        return SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                storeCarrier, pending, encodedByteCount);
    }

    private sealed interface Access permits AccessReady, AccessUnavailable {
    }

    private record AccessReady(
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready savedDataReady,
            PendingAttachmentJournalState.Ready journalReady) implements Access {
    }

    private record AccessUnavailable(UnavailableReason reason) implements Access {
    }

    @FunctionalInterface
    interface AdapterResolver {
        GramaryeSkillSavedData resolve();
    }

    @FunctionalInterface
    interface BootstrapJournalLoader {
        PendingAttachmentJournalLoadResult load(
                OpaquePendingAttachmentUpdatesBlob sourcePending);
    }

    @FunctionalInterface
    interface BootstrapPublisher {
        void publish(
                SkillSavedDataState.Ready expected,
                SkillSavedDataState.Ready replacement,
                boolean markDirty);
    }

    @FunctionalInterface
    interface StoreCommitInvoker {
        SkillStoreCommitResult commit(
                SkillDefinitionStore store,
                SkillSubmissionPlan plan,
                SkillQuota quota);
    }

    public enum UnavailableReason {
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE
    }

    public sealed interface AuthoritySnapshot {
        record Absent(SkillId skillId) implements AuthoritySnapshot {
            public Absent {
                Objects.requireNonNull(skillId, "skillId");
            }
        }

        record Owned(SkillReference latest) implements AuthoritySnapshot {
            public Owned {
                Objects.requireNonNull(latest, "latest");
            }
        }

        record ForeignOwned(SkillId skillId) implements AuthoritySnapshot {
            public ForeignOwned {
                Objects.requireNonNull(skillId, "skillId");
            }
        }

        record Unavailable(UnavailableReason reason) implements AuthoritySnapshot {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface BootstrapResult {
        record Ready(int entryCount, boolean rewritePublished) implements BootstrapResult {
            public Ready {
                if (entryCount < 0) {
                    throw new IllegalArgumentException("entryCount must be non-negative");
                }
            }
        }

        record Unavailable(UnavailableReason reason) implements BootstrapResult {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface JournalStatus {
        record Ready(int entryCount) implements JournalStatus {
            public Ready {
                if (entryCount < 0) {
                    throw new IllegalArgumentException("entryCount must be non-negative");
                }
            }
        }

        record Unavailable(UnavailableReason reason) implements JournalStatus {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface JournalRootProjection {
        record Available(List<SkillReference> references) implements JournalRootProjection {
            public Available {
                references = List.copyOf(Objects.requireNonNull(references, "references"));
            }
        }

        record Unavailable(UnavailableReason reason) implements JournalRootProjection {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface SubmissionPreparationResult {
        record Prepared(PreparedStoreSubmissionCommit handle)
                implements SubmissionPreparationResult {
            public Prepared {
                Objects.requireNonNull(handle, "handle");
            }
        }

        record Rejected(PreparationFailure failure) implements SubmissionPreparationResult {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record Unavailable(UnavailableReason reason) implements SubmissionPreparationResult {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface SubmissionCommitResult {
        record Committed(SkillReference reference) implements SubmissionCommitResult {
            public Committed {
                Objects.requireNonNull(reference, "reference");
            }
        }

        record DomainRejected(SkillStoreCommitResult result)
                implements SubmissionCommitResult {
            public DomainRejected {
                Objects.requireNonNull(result, "result");
                if (result instanceof SkillStoreCommitResult.Committed) {
                    throw new IllegalArgumentException(
                            "DomainRejected cannot wrap a committed result");
                }
            }
        }

        record PreparedBaseMismatch(PreparedBaseMismatchCode code)
                implements SubmissionCommitResult {
            public PreparedBaseMismatch {
                Objects.requireNonNull(code, "code");
            }
        }

        record Unavailable(UnavailableReason reason) implements SubmissionCommitResult {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record PostCommitInvariantFailure(PostCommitFailureCode code)
                implements SubmissionCommitResult {
            public PostCommitInvariantFailure {
                Objects.requireNonNull(code, "code");
            }
        }
    }

    public sealed interface JournalClearPreparationResult {
        record Prepared(PreparedJournalPrefixClear handle)
                implements JournalClearPreparationResult {
            public Prepared {
                Objects.requireNonNull(handle, "handle");
            }
        }

        enum NoOp implements JournalClearPreparationResult {
            INSTANCE
        }

        record Rejected(JournalClearFailure code) implements JournalClearPreparationResult {
            public Rejected {
                Objects.requireNonNull(code, "code");
            }
        }

        record Unavailable(UnavailableReason reason)
                implements JournalClearPreparationResult {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public sealed interface JournalClearCommitResult {
        record Cleared(int entriesRemoved) implements JournalClearCommitResult {
            public Cleared {
                if (entriesRemoved <= 0) {
                    throw new IllegalArgumentException("entriesRemoved must be positive");
                }
            }
        }

        enum NoOp implements JournalClearCommitResult {
            INSTANCE
        }

        record PreparedBaseMismatch(PreparedBaseMismatchCode code)
                implements JournalClearCommitResult {
            public PreparedBaseMismatch {
                Objects.requireNonNull(code, "code");
            }
        }

        record Unavailable(UnavailableReason reason) implements JournalClearCommitResult {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    public enum PreparationFailure {
        TRANSITION_SERVER_MISMATCH,
        NORMAL_SUBMISSION_NO_OP,
        PLAN_TRANSITION_PAIRING_FAILURE,
        AUTHORITY_PRECONDITION_MISMATCH,
        DOCUMENT_BLOB_CAPACITY_REJECTED,
        REVISION_BLOB_CAPACITY_REJECTED,
        HISTORY_BLOB_CAPACITY_REJECTED,
        STORE_BLOB_CAPACITY_REJECTED,
        JOURNAL_ENTRY_COUNT_REJECTED,
        JOURNAL_ENCODED_CAPACITY_REJECTED,
        STORE_CARRIER_INVARIANT_FAILURE,
        JOURNAL_CHAIN_INVARIANT_FAILURE,
        SAVED_DATA_CARRIER_INVARIANT_FAILURE
    }

    public enum PreparedBaseMismatchCode {
        STATE_IDENTITY_CHANGED,
        AUTHORITY_CHANGED,
        TRANSITION_CHANGED
    }

    public enum PostCommitFailureCode {
        POST_COMMIT_INVARIANT_FAILURE
    }

    public enum JournalClearFailure {
        PREFIX_TARGET_MISMATCH,
        CARRIER_INVARIANT_FAILURE
    }

    public static final class PreparedStoreSubmissionCommit {
        private final SkillDefinitionStoreSubmissionPort owner;
        private SubmissionPayload payload;

        private PreparedStoreSubmissionCommit(
                SkillDefinitionStoreSubmissionPort owner,
                SubmissionPayload payload) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        private void requireOwner(
                SkillDefinitionStoreSubmissionPort candidate,
                Object candidateServerIdentity) {
            if (owner != candidate) {
                throw new IllegalStateException("prepared handle belongs to another port");
            }
            if (payload == null) {
                throw new IllegalStateException("prepared handle was already consumed");
            }
            if (payload.serverIdentity() != candidateServerIdentity) {
                throw new IllegalStateException("prepared handle belongs to another server");
            }
        }

        private void requireTransitionBinding(Object candidateServerIdentity) {
            if (!payload.transition().isBoundTo(candidateServerIdentity)) {
                throw new IllegalStateException("prepared transition belongs to another server");
            }
        }

        private SubmissionPayload consume() {
            var captured = payload;
            if (captured == null) {
                throw new IllegalStateException("prepared handle was already consumed");
            }
            payload = null;
            return captured;
        }
    }

    public static final class PreparedJournalPrefixClear {
        private final SkillDefinitionStoreSubmissionPort owner;
        private ClearPayload payload;

        private PreparedJournalPrefixClear(
                SkillDefinitionStoreSubmissionPort owner,
                ClearPayload payload) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.payload = Objects.requireNonNull(payload, "payload");
        }

        private void requireOwner(
                SkillDefinitionStoreSubmissionPort candidate,
                Object candidateServerIdentity) {
            if (owner != candidate) {
                throw new IllegalStateException("prepared handle belongs to another port");
            }
            if (payload == null) {
                throw new IllegalStateException("prepared handle was already consumed");
            }
            if (payload.serverIdentity() != candidateServerIdentity) {
                throw new IllegalStateException("prepared handle belongs to another server");
            }
        }

        private ClearPayload consume() {
            var captured = payload;
            if (captured == null) {
                throw new IllegalStateException("prepared handle was already consumed");
            }
            payload = null;
            return captured;
        }
    }

    private record SubmissionPayload(
            Object serverIdentity,
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready baseReady,
            EncodedSkillStoreCarrier baseCarrier,
            OpaquePendingAttachmentUpdatesBlob basePending,
            PendingAttachmentJournalState.Ready baseJournalReady,
            SkillSubmissionPlan plan,
            SkillQuota quota,
            TransitionView transition,
            PreparedCarrierUpdate carrierUpdate,
            JournalTargetAuditProof.ConditionalOnExactCommit conditionalProof,
            PendingAttachmentJournalState.Ready prospectiveJournalReady,
            SkillSavedDataState.Ready prospectiveReady,
            SkillSavedDataState.Unavailable fallbackState,
            SubmissionCommitResult.Committed committedResult,
            SubmissionCommitResult.PostCommitInvariantFailure postCommitFailure) {
    }

    static final class TransitionView {
        private final Object serverIdentity;
        private final PlayerSkillAttachmentService.PreparedPlayerSkillTransition source;
        private final SkillOwnerId owner;
        private final SkillId skillId;
        private final Optional<SkillReference> expectedPointer;
        private final int expectedGeneration;
        private final Optional<SkillReference> targetPointer;
        private final int targetGeneration;
        private final boolean noOp;

        private TransitionView(
                Object serverIdentity,
                PlayerSkillAttachmentService.PreparedPlayerSkillTransition source,
                SkillOwnerId owner,
                SkillId skillId,
                Optional<SkillReference> expectedPointer,
                int expectedGeneration,
                Optional<SkillReference> targetPointer,
                int targetGeneration,
                boolean noOp) {
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.source = source;
            this.owner = Objects.requireNonNull(owner, "owner");
            this.skillId = Objects.requireNonNull(skillId, "skillId");
            this.expectedPointer = Objects.requireNonNull(expectedPointer, "expectedPointer");
            this.expectedGeneration = expectedGeneration;
            this.targetPointer = Objects.requireNonNull(targetPointer, "targetPointer");
            this.targetGeneration = targetGeneration;
            this.noOp = noOp;
        }

        static TransitionView production(
                MinecraftServer server,
                PlayerSkillAttachmentService.PreparedPlayerSkillTransition transition) {
            Objects.requireNonNull(transition, "transition");
            return new TransitionView(
                    server,
                    transition,
                    transition.owner(),
                    transition.skillId(),
                    transition.expectedPointer(),
                    transition.expectedGeneration(),
                    transition.targetPointer(),
                    transition.targetGeneration(),
                    transition.isNoOp());
        }

        static TransitionView capture(
                Object serverIdentity,
                SkillOwnerId owner,
                SkillId skillId,
                Optional<SkillReference> expectedPointer,
                int expectedGeneration,
                Optional<SkillReference> targetPointer,
                int targetGeneration,
                boolean noOp) {
            return new TransitionView(
                    serverIdentity,
                    null,
                    owner,
                    skillId,
                    expectedPointer,
                    expectedGeneration,
                    targetPointer,
                    targetGeneration,
                    noOp);
        }

        boolean isBoundTo(Object candidate) {
            Objects.requireNonNull(candidate, "candidate");
            if (serverIdentity != candidate) {
                return false;
            }
            return source == null
                    || candidate instanceof MinecraftServer server
                            && source.isBoundTo(server);
        }

        boolean matchesSource() {
            return source == null
                    || source.owner().equals(owner)
                            && source.skillId().equals(skillId)
                            && source.expectedPointer().equals(expectedPointer)
                            && source.expectedGeneration() == expectedGeneration
                            && source.targetPointer().equals(targetPointer)
                            && source.targetGeneration() == targetGeneration
                            && source.isNoOp() == noOp;
        }

        SkillOwnerId owner() {
            return owner;
        }

        SkillId skillId() {
            return skillId;
        }

        Optional<SkillReference> expectedPointer() {
            return expectedPointer;
        }

        int expectedGeneration() {
            return expectedGeneration;
        }

        Optional<SkillReference> targetPointer() {
            return targetPointer;
        }

        int targetGeneration() {
            return targetGeneration;
        }

        boolean isNoOp() {
            return noOp;
        }
    }

    private record ClearPayload(
            Object serverIdentity,
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready baseReady,
            OpaquePendingAttachmentUpdatesBlob basePending,
            PendingAttachmentJournalState.Ready baseJournalReady,
            SkillSavedDataState.Ready prospectiveReady,
            JournalClearCommitResult.Cleared result) {
    }
}
