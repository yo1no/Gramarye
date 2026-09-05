package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Synchronous package owner for the sole P4-D-to-E2 production continuation. */
final class P4E2OnlineReconciliationCoordinator
        implements P4E2OnlineReconciliationDependency {
    private static final int MAX_EXCEPTION_CLASS_LENGTH = 160;

    private final SkillDefinitionStoreService storeService;
    private final PlayerSkillAttachmentService attachmentService;
    private final SkillRetentionRootAuditService rootAuditService;
    private final P7ServerAuthorizationBoundary.LoginReadyPort loginReadyPort;
    private final P4E2QualificationFacade.StoreView qualificationStoreView;
    private final P4E2QualificationFacade.PlayerView qualificationPlayerView;

    private record RecoveryStatus(
            RecoveryKind kind,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> exceptionClass) {
        private RecoveryStatus {
            Objects.requireNonNull(kind, "kind");
            exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass");
            if (entriesCleared < 0
                    || stepsReplayed < 0
                    || entriesCleared > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES
                    || stepsReplayed > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES
                    || (long) entriesCleared + stepsReplayed
                            > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES) {
                throw new IllegalArgumentException("recovery progress is outside its bound");
            }
            if (exceptionClass.stream().anyMatch(value -> value.isEmpty()
                    || value.length() > MAX_EXCEPTION_CLASS_LENGTH)) {
                throw new IllegalArgumentException(
                        "exceptionClass must be a bounded class name");
            }
            if ((kind == RecoveryKind.RUNTIME_EXCEPTION) != exceptionClass.isPresent()) {
                throw new IllegalArgumentException(
                        "only runtime recovery failures carry an exception class");
            }
        }

        private boolean recoveryChanged() {
            return entriesCleared > 0 || stepsReplayed > 0;
        }
    }

    P4E2OnlineReconciliationCoordinator(
            SkillDefinitionStoreService storeService,
            PlayerSkillAttachmentService attachmentService,
            SkillRetentionRootAuditService rootAuditService,
            P7ServerAuthorizationBoundary.LoginReadyPort loginReadyPort) {
        this(storeService, attachmentService, rootAuditService, loginReadyPort, null, null);
    }

    P4E2OnlineReconciliationCoordinator(
            SkillDefinitionStoreService storeService,
            PlayerSkillAttachmentService attachmentService,
            SkillRetentionRootAuditService rootAuditService,
            P7ServerAuthorizationBoundary.LoginReadyPort loginReadyPort,
            P4E2QualificationFacade.StoreView qualificationStoreView,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.attachmentService = Objects.requireNonNull(
                attachmentService, "attachmentService");
        this.rootAuditService = Objects.requireNonNull(rootAuditService, "rootAuditService");
        this.loginReadyPort = Objects.requireNonNull(loginReadyPort, "loginReadyPort");
        if ((qualificationStoreView == null) != (qualificationPlayerView == null)) {
            throw new IllegalArgumentException(
                    "qualification views must be both present or both absent");
        }
        this.qualificationStoreView = qualificationStoreView;
        this.qualificationPlayerView = qualificationPlayerView;
    }

    @Override
    public void reconcileAfterRecovery(
            ServerPlayer player,
            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
            RecoveryKind kind,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> existingExceptionClass) {
        var exactPlayer = Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(exactPlayer.getServer(), "player server");
        SkillDefinitionStoreService.requireServerThread(server);
        var playerId = exactPlayer.getUUID();
        var playerMost = playerId.getMostSignificantBits();
        var playerLeast = playerId.getLeastSignificantBits();
        var observing = qualificationStoreView != null
                && qualificationStoreView.enabledFor(server, playerMost, playerLeast);
        if (observing) {
            qualificationStoreView.recordContinuation(server, playerMost, playerLeast);
        }
        var result = reconcile(
                exactPlayer,
                continuation,
                kind,
                entriesCleared,
                stepsReplayed,
                existingExceptionClass);
        if (observing) {
            P4E2QualificationFacade.ReconciliationVariant variant;
            P4E2QualificationFacade.ReconciliationDetail detail;
            P4E2ReconciliationResult.Summary summary;
            switch (result) {
                case P4E2ReconciliationResult.NoChanges exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES;
                    detail = P4E2QualificationFacade.ReconciliationDetail.NONE;
                    summary = exact.summary();
                }
                case P4E2ReconciliationResult.RecoveryChanged exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant.RECOVERY_CHANGED;
                    detail = P4E2QualificationFacade.ReconciliationDetail.NONE;
                    summary = exact.summary();
                }
                case P4E2ReconciliationResult.Changed exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant.CHANGED;
                    detail = P4E2QualificationFacade.ReconciliationDetail.NONE;
                    summary = exact.summary();
                }
                case P4E2ReconciliationResult.Deferred exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant.DEFERRED;
                    detail = switch (exact.reason()) {
                        case RECOVERY_OPERATIONAL_UNAVAILABLE ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .RECOVERY_OPERATIONAL_UNAVAILABLE;
                        case STORE_UNAVAILABLE ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .STORE_UNAVAILABLE;
                        case ATTACHMENT_PRESERVED_RAW ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .ATTACHMENT_PRESERVED_RAW;
                        case ATTACHMENT_OVERSIZE ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .ATTACHMENT_OVERSIZE;
                    };
                    summary = exact.summary();
                }
                case P4E2ReconciliationResult.Failed exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant.FAILED;
                    detail = switch (exact.reason()) {
                        case RECOVERY_CONFLICT ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .RECOVERY_CONFLICT;
                        case RECOVERY_TARGET_INVALID ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .RECOVERY_TARGET_INVALID;
                        case RECOVERY_RUNTIME_FAILURE ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .RECOVERY_RUNTIME_FAILURE;
                        case PLAYER_GENERATION_EXHAUSTED ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .PLAYER_GENERATION_EXHAUSTED;
                        case ATTACHMENT_CAPACITY_REJECTED ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .ATTACHMENT_CAPACITY_REJECTED;
                        case ATTACHMENT_INVARIANT_REJECTED ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .ATTACHMENT_INVARIANT_REJECTED;
                        case FRESHNESS_LOST ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .FRESHNESS_LOST;
                        case INTERNAL_RUNTIME_FAILURE ->
                                P4E2QualificationFacade.ReconciliationDetail
                                        .INTERNAL_RUNTIME_FAILURE;
                    };
                    summary = exact.summary();
                }
                case P4E2ReconciliationResult.GenerationExhausted exact -> {
                    variant = P4E2QualificationFacade.ReconciliationVariant
                            .GENERATION_EXHAUSTED;
                    detail = P4E2QualificationFacade.ReconciliationDetail.NONE;
                    summary = exact.summary();
                }
            }
            qualificationStoreView.recordReconciliation(
                    server,
                    playerMost,
                    playerLeast,
                    variant,
                    detail,
                    summary.recoveryEntriesCleared(),
                    summary.recoveryStepsReplayed(),
                    summary.staleLatestObserved(),
                    summary.staleLatestPruned(),
                    summary.staleEquippedObserved(),
                    summary.staleEquippedPruned(),
                    summary.missingCount(),
                    summary.ownerMismatchCount(),
                    summary.acceptedGeneration().isPresent());
        }
        if (isLoginReadyTerminal(result)) {
            if (server.getPlayerList().getPlayer(playerId) != exactPlayer) {
                throw new IllegalStateException("P7_LOGIN_READY_PLAYER_NOT_CURRENT");
            }
            loginReadyPort.onLoginReady(server, exactPlayer);
        }
    }

    static boolean isLoginReadyTerminal(P4E2ReconciliationResult result) {
        return switch (Objects.requireNonNull(result, "result")) {
            case P4E2ReconciliationResult.NoChanges ignored -> true;
            case P4E2ReconciliationResult.RecoveryChanged ignored -> true;
            case P4E2ReconciliationResult.Changed ignored -> true;
            case P4E2ReconciliationResult.Deferred ignored -> false;
            case P4E2ReconciliationResult.Failed ignored -> false;
            case P4E2ReconciliationResult.GenerationExhausted ignored -> false;
        };
    }

    P4E2ReconciliationResult reconcile(
            ServerPlayer player,
            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
            RecoveryKind kind,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> existingExceptionClass) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(continuation, "continuation");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(existingExceptionClass, "existingExceptionClass");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        SkillDefinitionStoreService.requireServerThread(server);

        SkillRetentionRootAuditService.InvalidationResult.Accepted accepted = null;
        RecoveryStatus status = null;
        try {
            if (entriesCleared > 0 || stepsReplayed > 0) {
                var invalidation = invalidate(server, player);
                if (invalidation
                        instanceof SkillRetentionRootAuditService.InvalidationResult
                                .GenerationExhausted) {
                    status = recoveryStatus(
                            kind, entriesCleared, stepsReplayed, existingExceptionClass);
                    return new P4E2ReconciliationResult.GenerationExhausted(
                            summary(status, null, 0, 0, null));
                }
                accepted = (SkillRetentionRootAuditService.InvalidationResult.Accepted)
                        invalidation;
            }

            status = recoveryStatus(
                    kind, entriesCleared, stepsReplayed, existingExceptionClass);
            var ineligible = ineligibleResult(status, accepted);
            if (ineligible.isPresent()) {
                return ineligible.orElseThrow();
            }
            return reconcileEligible(player, server, continuation, status, accepted);
        } catch (RuntimeException exception) {
            var exactStatus = status == null
                    ? recoveryStatus(
                            kind, entriesCleared, stepsReplayed, existingExceptionClass)
                    : status;
            return failed(
                    exactStatus,
                    null,
                    accepted,
                    P4E2ReconciliationResult.FailureReason.INTERNAL_RUNTIME_FAILURE,
                    Optional.of(boundedClassName(exception)));
        }
    }

    private P4E2ReconciliationResult reconcileEligible(
            ServerPlayer player,
            MinecraftServer server,
            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
            RecoveryStatus status,
            SkillRetentionRootAuditService.InvalidationResult.Accepted earlyAccepted) {
        PlayerSkillAttachmentService.OnlineReconciliationHandle handle = null;
        P4E2GroupedStoreValidation.StoreReadyWitness storeWitness = null;
        PlayerSkillAttachmentService.PreparedOnlineReconciliation prepared = null;
        var handleOwned = false;
        var preparedOwned = false;
        P4E2GroupedStoreValidation.Validated validation = null;
        var accepted = earlyAccepted;
        try {
            handle = attachmentService.observeOnlineForReconciliation(player);
            handleOwned = true;
            var state = attachmentService.onlineReconciliationState(handle);
            var observedStore = storeService.observeP4E2StoreReady(server, this);
            if (observedStore instanceof P4E2GroupedStoreValidation.StoreObservation.Unavailable) {
                attachmentService.discardOnlineReconciliationProjection(handle);
                return new P4E2ReconciliationResult.Deferred(
                        summary(status, null, 0, 0, accepted),
                        P4E2ReconciliationResult.DeferredReason.STORE_UNAVAILABLE);
            }
            storeWitness = ((P4E2GroupedStoreValidation.StoreObservation.Ready) observedStore)
                    .witness();
            if (state != PlayerSkillAttachmentService.OnlineReconciliationState.READY) {
                attachmentService.discardOnlineReconciliationProjection(handle);
                var playerCurrent = attachmentService.isOnlineReconciliationCurrent(
                        handle, player);
                var storeCurrent = playerCurrent && storeWitness.isCurrent();
                var indexCurrent = storeCurrent && invalidationCurrent(server, accepted);
                if (!allFresh(playerCurrent, storeCurrent, indexCurrent)) {
                    return failed(
                            status,
                            null,
                            accepted,
                            P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                            Optional.empty());
                }
                return switch (state) {
                    case MISSING -> unchanged(status, accepted);
                    case PRESERVED_RAW -> new P4E2ReconciliationResult.Deferred(
                            summary(status, null, 0, 0, accepted),
                            P4E2ReconciliationResult.DeferredReason
                                    .ATTACHMENT_PRESERVED_RAW);
                    case OVERSIZE -> new P4E2ReconciliationResult.Deferred(
                            summary(status, null, 0, 0, accepted),
                            P4E2ReconciliationResult.DeferredReason.ATTACHMENT_OVERSIZE);
                    case READY -> throw new IllegalStateException(
                            "P4E2_READY_BRANCH_MISMATCH");
                };
            }

            var grouped = new P4E2GroupedStoreValidation(
                    new SkillOwnerId(player.getUUID()));
            attachmentService.drainOnlineReconciliation(handle, grouped);
            validation = grouped.validate(storeWitness);

            if (!validation.hasStaleRoutes()) {
                var playerCurrent = attachmentService.isOnlineReconciliationCurrent(
                        handle, player);
                var storeCurrent = playerCurrent && storeWitness.isCurrent();
                var indexCurrent = storeCurrent && invalidationCurrent(server, accepted);
                if (!allFresh(playerCurrent, storeCurrent, indexCurrent)) {
                    return failed(
                            status,
                            validation,
                            accepted,
                            P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                            Optional.empty());
                }
                return unchanged(status, accepted, validation);
            }

            var capability = new P4E2BoundPlayerSkillAttachmentReconciliationCapability(
                    attachmentService,
                    handle,
                    continuation,
                    this,
                    validation,
                    validation.staleLatestOrdinals(),
                    validation.staleEquippedOrdinals(),
                    qualificationPlayerView);
            handleOwned = false;
            var preparation = attachmentService.prepareOnlineReconciliation(player, capability);
            if (preparation
                    instanceof PlayerSkillAttachmentService.ReconciliationStateChanged) {
                return failed(
                        status,
                        validation,
                        accepted,
                        P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                        Optional.empty());
            }
            if (preparation
                    instanceof PlayerSkillAttachmentService
                            .ReconciliationGenerationExhausted) {
                return failed(
                        status,
                        validation,
                        accepted,
                        P4E2ReconciliationResult.FailureReason.PLAYER_GENERATION_EXHAUSTED,
                        Optional.empty());
            }
            if (preparation
                    instanceof PlayerSkillAttachmentService.ReconciliationBuildRejected
                            rejected) {
                return buildRejected(status, validation, accepted, rejected);
            }

            prepared = ((PlayerSkillAttachmentService.PreparedReconciliation) preparation)
                    .prepared();
            preparedOwned = true;
            var playerCurrent = attachmentService.checkPreparedReconciliationCurrent(
                    player, prepared)
                    == PlayerSkillAttachmentService.ReconciliationCurrentness.CURRENT;
            var storeCurrent = playerCurrent && storeWitness.isCurrent();
            var indexCurrent = storeCurrent && invalidationCurrent(server, accepted);
            if (!allFresh(playerCurrent, storeCurrent, indexCurrent)) {
                return failed(
                        status,
                        validation,
                        accepted,
                        P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                        Optional.empty());
            }

            if (accepted == null) {
                var invalidation = invalidate(server, player);
                if (invalidation
                        instanceof SkillRetentionRootAuditService.InvalidationResult
                                .GenerationExhausted) {
                    return new P4E2ReconciliationResult.GenerationExhausted(
                            summary(status, validation, 0, 0, null));
                }
                accepted = (SkillRetentionRootAuditService.InvalidationResult.Accepted)
                        invalidation;
            }
            playerCurrent = attachmentService.checkPreparedReconciliationCurrent(
                    player, prepared)
                    == PlayerSkillAttachmentService.ReconciliationCurrentness.CURRENT;
            storeCurrent = playerCurrent && storeWitness.isCurrent();
            indexCurrent = storeCurrent
                    && rootAuditService.isReconciliationInvalidationCurrent(server, accepted);
            if (!allFresh(playerCurrent, storeCurrent, indexCurrent)) {
                return failed(
                        status,
                        validation,
                        accepted,
                        P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                        Optional.empty());
            }

            preparedOwned = false;
            var publication = attachmentService.publishPreparedReconciliation(player, prepared);
            if (publication != PlayerSkillAttachmentService.ReconciliationPublication.APPLIED) {
                return failed(
                        status,
                        validation,
                        accepted,
                        P4E2ReconciliationResult.FailureReason.FRESHNESS_LOST,
                        Optional.empty());
            }
            return new P4E2ReconciliationResult.Changed(summary(
                    status,
                    validation,
                    validation.staleLatestCount(),
                    validation.staleEquippedCount(),
                    accepted));
        } catch (RuntimeException exception) {
            return failed(
                    status,
                    validation,
                    accepted,
                    P4E2ReconciliationResult.FailureReason.INTERNAL_RUNTIME_FAILURE,
                    Optional.of(boundedClassName(exception)));
        } finally {
            if (preparedOwned) {
                attachmentService.discardPreparedReconciliation(prepared);
            } else if (handleOwned) {
                attachmentService.discardOnlineReconciliationHandle(handle);
            }
            if (storeWitness != null) {
                storeWitness.discard();
            }
        }
    }

    private Optional<P4E2ReconciliationResult> ineligibleResult(
            RecoveryStatus status,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted) {
        return switch (status.kind()) {
            case NO_PENDING, CLEARED, REPLAYED, CLEARED_AND_REPLAYED -> Optional.empty();
            case CONFLICT -> Optional.of(failed(
                    status,
                    null,
                    accepted,
                    P4E2ReconciliationResult.FailureReason.RECOVERY_CONFLICT,
                    Optional.empty()));
            case TARGET_INVALID -> Optional.of(failed(
                    status,
                    null,
                    accepted,
                    P4E2ReconciliationResult.FailureReason.RECOVERY_TARGET_INVALID,
                    Optional.empty()));
            case RUNTIME_EXCEPTION -> Optional.of(failed(
                    status,
                    null,
                    accepted,
                    P4E2ReconciliationResult.FailureReason.RECOVERY_RUNTIME_FAILURE,
                    status.exceptionClass()));
            case JOURNAL_NOT_BOOTSTRAPPED,
                    JOURNAL_UNAVAILABLE,
                    STORE_UNAVAILABLE,
                    AUTHORITY_UNAVAILABLE,
                    ATTACHMENT_PRESERVED_RAW_QUARANTINE,
                    ATTACHMENT_OVERSIZE_QUARANTINE -> Optional.of(
                            new P4E2ReconciliationResult.Deferred(
                                    summary(status, null, 0, 0, accepted),
                                    P4E2ReconciliationResult.DeferredReason
                                            .RECOVERY_OPERATIONAL_UNAVAILABLE));
        };
    }

    private SkillRetentionRootAuditService.InvalidationResult invalidate(
            MinecraftServer server, ServerPlayer player) {
        var playerId = player.getUUID();
        var playerMost = playerId.getMostSignificantBits();
        var playerLeast = playerId.getLeastSignificantBits();
        if (qualificationStoreView != null) {
            qualificationStoreView.recordInvalidationAttempt(
                    server, playerMost, playerLeast);
        }
        var result = rootAuditService.invalidateForReconciliation(server);
        if (result instanceof SkillRetentionRootAuditService.InvalidationResult.Accepted
                && qualificationStoreView != null) {
            qualificationStoreView.recordInvalidationAccepted(
                    server, playerMost, playerLeast);
        }
        return result;
    }

    private boolean invalidationCurrent(
            MinecraftServer server,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted) {
        return accepted == null
                || rootAuditService.isReconciliationInvalidationCurrent(server, accepted);
    }

    static boolean allFresh(
            boolean playerCurrent,
            boolean storeCurrent,
            boolean invalidationCurrent) {
        return playerCurrent && storeCurrent && invalidationCurrent;
    }

    private static RecoveryStatus recoveryStatus(
            RecoveryKind kind,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> existingExceptionClass) {
        var boundedExceptionClass = existingExceptionClass.map(value ->
                value.length() <= MAX_EXCEPTION_CLASS_LENGTH
                        ? value
                        : value.substring(0, MAX_EXCEPTION_CLASS_LENGTH));
        return new RecoveryStatus(
                kind, entriesCleared, stepsReplayed, boundedExceptionClass);
    }

    private static P4E2ReconciliationResult unchanged(
            RecoveryStatus status,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted) {
        return unchanged(status, accepted, null);
    }

    private static P4E2ReconciliationResult unchanged(
            RecoveryStatus status,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted,
            P4E2GroupedStoreValidation.Validated validation) {
        var summary = summary(status, validation, 0, 0, accepted);
        return status.recoveryChanged()
                ? new P4E2ReconciliationResult.RecoveryChanged(summary)
                : new P4E2ReconciliationResult.NoChanges(summary);
    }

    private static P4E2ReconciliationResult buildRejected(
            RecoveryStatus status,
            P4E2GroupedStoreValidation.Validated validation,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted,
            PlayerSkillAttachmentService.ReconciliationBuildRejected rejected) {
        var reason = switch (rejected.reason()) {
            case CAPACITY_REJECTED ->
                    P4E2ReconciliationResult.FailureReason.ATTACHMENT_CAPACITY_REJECTED;
            case INVARIANT_REJECTED ->
                    P4E2ReconciliationResult.FailureReason.ATTACHMENT_INVARIANT_REJECTED;
            case INTERNAL_RUNTIME_FAILURE ->
                    P4E2ReconciliationResult.FailureReason.INTERNAL_RUNTIME_FAILURE;
        };
        return failed(status, validation, accepted, reason, rejected.exceptionClass());
    }

    private static P4E2ReconciliationResult.Failed failed(
            RecoveryStatus status,
            P4E2GroupedStoreValidation.Validated validation,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted,
            P4E2ReconciliationResult.FailureReason reason,
            Optional<String> exceptionClass) {
        return new P4E2ReconciliationResult.Failed(
                summary(status, validation, 0, 0, accepted), reason, exceptionClass);
    }

    private static P4E2ReconciliationResult.Summary summary(
            RecoveryStatus status,
            P4E2GroupedStoreValidation.Validated validation,
            int staleLatestPruned,
            int staleEquippedPruned,
            SkillRetentionRootAuditService.InvalidationResult.Accepted accepted) {
        return new P4E2ReconciliationResult.Summary(
                status.entriesCleared(),
                status.stepsReplayed(),
                validation == null ? 0 : validation.staleLatestCount(),
                staleLatestPruned,
                validation == null ? 0 : validation.staleEquippedCount(),
                staleEquippedPruned,
                validation == null ? 0 : validation.missingCount(),
                validation == null ? 0 : validation.ownerMismatchCount(),
                accepted == null
                        ? OptionalLong.empty()
                        : OptionalLong.of(accepted.generation()));
    }

    private static String boundedClassName(RuntimeException exception) {
        var className = exception.getClass().getName();
        return className.length() <= MAX_EXCEPTION_CLASS_LENGTH
                ? className
                : className.substring(0, MAX_EXCEPTION_CLASS_LENGTH);
    }
}
