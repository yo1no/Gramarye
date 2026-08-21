package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.P4E2OnlineReconciliationDependency;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Performs bounded, readback-confirmed recovery for one authenticated player's journal chains. */
public final class SkillSubmissionRecoveryService {
    private static final Optional<String> NO_RECOVERY_EXCEPTION = Optional.empty();

    private final Dependencies dependencies;
    private final P4E2OnlineReconciliationDependency onlineReconciliationDependency;
    private final P4E2QualificationFacade.SubmissionView qualificationView;
    private boolean registered;

    private SkillSubmissionRecoveryService(
            PlayerSkillAttachmentService attachmentService,
            SkillDefinitionStoreSubmissionPort storePort,
            P4E2OnlineReconciliationDependency onlineReconciliationDependency,
            P4E2QualificationFacade.SubmissionView qualificationView) {
        this(
                new ProductionDependencies(attachmentService, storePort),
                onlineReconciliationDependency,
                qualificationView);
    }

    SkillSubmissionRecoveryService(Dependencies dependencies) {
        this(dependencies, null, null);
    }

    SkillSubmissionRecoveryService(
            Dependencies dependencies,
            P4E2OnlineReconciliationDependency onlineReconciliationDependency) {
        this(dependencies, onlineReconciliationDependency, null);
    }

    SkillSubmissionRecoveryService(
            Dependencies dependencies,
            P4E2OnlineReconciliationDependency onlineReconciliationDependency,
            P4E2QualificationFacade.SubmissionView qualificationView) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.onlineReconciliationDependency = onlineReconciliationDependency;
        this.qualificationView = qualificationView;
    }

    public static SkillSubmissionRecoveryService create(
            PlayerSkillAttachmentService attachmentService,
            SkillDefinitionStoreSubmissionPort storePort,
            P4E2OnlineReconciliationDependency onlineReconciliationDependency) {
        return new SkillSubmissionRecoveryService(
                Objects.requireNonNull(attachmentService, "attachmentService"),
                Objects.requireNonNull(storePort, "storePort"),
                Objects.requireNonNull(
                        onlineReconciliationDependency, "onlineReconciliationDependency"),
                null);
    }

    public static SkillSubmissionRecoveryService create(
            PlayerSkillAttachmentService attachmentService,
            SkillDefinitionStoreSubmissionPort storePort,
            P4E2OnlineReconciliationDependency onlineReconciliationDependency,
            P4E2QualificationFacade.SubmissionView qualificationView) {
        return new SkillSubmissionRecoveryService(
                Objects.requireNonNull(attachmentService, "attachmentService"),
                Objects.requireNonNull(storePort, "storePort"),
                Objects.requireNonNull(
                        onlineReconciliationDependency, "onlineReconciliationDependency"),
                Objects.requireNonNull(qualificationView, "qualificationView"));
    }

    /** Registers exactly the persisted-player login recovery listener. */
    public void registerOn(IEventBus neoForgeEventBus) {
        Objects.requireNonNull(neoForgeEventBus, "neoForgeEventBus");
        if (registered) {
            throw new IllegalStateException("skill submission recovery was already registered");
        }
        neoForgeEventBus.addListener(this::onPlayerLoggedIn);
        registered = true;
    }

    RecoveryOutcome recoverPersistedPlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("skill submission recovery requires the server thread");
        }
        return recoverCore(player, server, new SkillOwnerId(player.getUUID()));
    }

    RecoveryOutcome recoverCore(Object player, Object server, SkillOwnerId owner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(owner, "owner");

        var projected = Objects.requireNonNull(
                dependencies.observePendingRecovery(server, owner),
                "pending recovery projection");
        if (projected
                instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                        .Unavailable unavailable) {
            return unavailable(
                    mapJournalUnavailable(unavailable.reason()), 0, 0, Optional.empty());
        }
        if (projected
                instanceof SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                        .TargetInvalid invalid) {
            return new TargetInvalid(
                    invalid.skillId(), invalid.target(), invalid.reason(), 0, 0);
        }

        var chains = ((SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available)
                projected).chains();
        if (chains.isEmpty()) {
            return NoPending.INSTANCE;
        }

        var observed = Objects.requireNonNull(
                dependencies.observeLatestStates(player), "latest-state observation");
        if (observed instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
            return unavailable(
                    mapAttachmentUnavailable(unavailable.reason()), 0, 0, Optional.empty());
        }
        if (!(observed instanceof PlayerSkillAttachmentService.Available<
                List<PlayerSkillAttachmentService.LatestStateView>> availableLatestStates)) {
            throw new IllegalStateException("unknown latest-state observation result");
        }
        var latestStates = availableLatestStates.value();
        var currentBySkill = indexLatestStates(latestStates);
        var processedSkills = new HashSet<SkillId>();
        int entriesCleared = 0;
        int stepsReplayed = 0;

        for (var chain : chains) {
            if (!processedSkills.add(chain.skillId())) {
                throw new IllegalStateException("recovery projection repeated a SkillId chain");
            }
            var steps = chain.steps();
            if (steps.isEmpty()) {
                throw new IllegalStateException("recovery projection contained an empty chain");
            }
            var loaded = currentBySkill.getOrDefault(chain.skillId(), LatestTuple.IMPLICIT);
            var classification = classify(loaded, steps);
            if (classification.kind() == ClassificationKind.THIRD_STATE) {
                return new Conflict(
                        chain.skillId(),
                        RecoveryConflictCode.THIRD_STATE,
                        entriesCleared,
                        stepsReplayed);
            }

            int replayStart = 0;
            if (classification.kind() == ClassificationKind.INTERMEDIATE_TARGET
                    || classification.kind() == ClassificationKind.FINAL_TARGET) {
                var confirmedPrefixLength = Math.incrementExact(classification.targetIndex());
                var confirmed = steps.get(classification.targetIndex());
                var preparedClear = Objects.requireNonNull(
                        dependencies.prepareClear(
                                server,
                                owner,
                                chain.skillId(),
                                confirmed.targetGeneration(),
                                confirmed.targetPointer()),
                        "journal clear preparation");
                if (preparedClear instanceof ClearPreparation.Unavailable unavailable) {
                    return unavailable(
                            unavailable.reason(),
                            entriesCleared,
                            stepsReplayed,
                            Optional.empty());
                }
                if (!(preparedClear instanceof ClearPreparation.Prepared prepared)) {
                    return new Conflict(
                            chain.skillId(),
                            RecoveryConflictCode.CLEAR_PREPARATION_REJECTED,
                            entriesCleared,
                            stepsReplayed);
                }

                var committedClear = Objects.requireNonNull(
                        dependencies.commitClear(server, prepared.handle()),
                        "journal clear commit");
                if (committedClear instanceof ClearCommit.Unavailable unavailable) {
                    return unavailable(
                            unavailable.reason(),
                            entriesCleared,
                            stepsReplayed,
                            Optional.empty());
                }
                if (!(committedClear instanceof ClearCommit.Cleared cleared)) {
                    return new Conflict(
                            chain.skillId(),
                            RecoveryConflictCode.CLEAR_COMMIT_REJECTED,
                            entriesCleared,
                            stepsReplayed);
                }
                entriesCleared = addBounded(entriesCleared, cleared.entriesRemoved());
                if (cleared.entriesRemoved() != confirmedPrefixLength) {
                    return new Conflict(
                            chain.skillId(),
                            RecoveryConflictCode.CLEAR_COMMIT_REJECTED,
                            entriesCleared,
                            stepsReplayed);
                }
                if (classification.kind() == ClassificationKind.FINAL_TARGET) {
                    continue;
                }
                replayStart = confirmedPrefixLength;
            }

            var current = loaded;
            for (int index = replayStart; index < steps.size(); index++) {
                var step = steps.get(index);
                if (!current.matches(step.expectedPointer(), step.expectedGeneration())) {
                    return new Conflict(
                            chain.skillId(),
                            RecoveryConflictCode.REPLAY_PREPARATION_REJECTED,
                            entriesCleared,
                            stepsReplayed);
                }

                try {
                    var preparedResult = Objects.requireNonNull(
                            dependencies.prepareTransition(
                                    player, chain.skillId(), step.targetPointer()),
                            "Attachment transition preparation");
                    if (preparedResult instanceof TransitionPreparation.Unavailable unavailable) {
                        return unavailable(
                                unavailable.reason(),
                                entriesCleared,
                                stepsReplayed,
                                Optional.empty());
                    }
                    if (!(preparedResult instanceof TransitionPreparation.Prepared prepared)) {
                        return new Conflict(
                                chain.skillId(),
                                RecoveryConflictCode.REPLAY_PREPARATION_REJECTED,
                                entriesCleared,
                                stepsReplayed);
                    }
                    var transition = prepared.handle();
                    if (transition.isNoOp()) {
                        return new Conflict(
                                chain.skillId(),
                                RecoveryConflictCode.REPLAY_UNEXPECTED_NO_OP,
                                entriesCleared,
                                stepsReplayed);
                    }
                    if (!transition.isBoundTo(server)
                            || !transition.owner().equals(owner)
                            || !transition.skillId().equals(chain.skillId())
                            || !transition.expectedPointer().equals(step.expectedPointer())
                            || transition.expectedGeneration() != step.expectedGeneration()
                            || !transition.targetPointer().equals(Optional.of(step.targetPointer()))
                            || transition.targetGeneration() != step.targetGeneration()) {
                        return new Conflict(
                                chain.skillId(),
                                RecoveryConflictCode.REPLAY_PREPARATION_REJECTED,
                                entriesCleared,
                                stepsReplayed);
                    }

                    var currentness = Objects.requireNonNull(
                            dependencies.checkCurrent(player, transition),
                            "Attachment transition currentness");
                    if (currentness instanceof TransitionCheck.Unavailable unavailable) {
                        return unavailable(
                                unavailable.reason(),
                                entriesCleared,
                                stepsReplayed,
                                Optional.empty());
                    }
                    if (currentness != TransitionCheck.Current.INSTANCE) {
                        return new Conflict(
                                chain.skillId(),
                                RecoveryConflictCode.REPLAY_CURRENTNESS_CHANGED,
                                entriesCleared,
                                stepsReplayed);
                    }

                    var published = Objects.requireNonNull(
                            dependencies.publishTransition(player, transition),
                            "Attachment transition publication");
                    if (published instanceof TransitionPublication.Unavailable unavailable) {
                        return unavailable(
                                unavailable.reason(),
                                entriesCleared,
                                stepsReplayed,
                                Optional.empty());
                    }
                    if (published != TransitionPublication.Applied.INSTANCE) {
                        return new Conflict(
                                chain.skillId(),
                                RecoveryConflictCode.REPLAY_PUBLICATION_REJECTED,
                                entriesCleared,
                                stepsReplayed);
                    }
                } catch (RuntimeException exception) {
                    return unavailable(
                            RecoveryUnavailableReason.RUNTIME_EXCEPTION,
                            entriesCleared,
                            stepsReplayed,
                            Optional.of(boundedClassName(exception)));
                }

                current = LatestTuple.target(step);
                currentBySkill.put(chain.skillId(), current);
                stepsReplayed = addBounded(stepsReplayed, 1);
            }
        }

        return successfulOutcome(entriesCleared, stepsReplayed);
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getEntity() instanceof ServerPlayer player) {
            requireE2RecoveryVocabularyInitialized();
            var server = Objects.requireNonNull(player.getServer(), "player server");
            var playerId = player.getUUID();
            var playerMost = playerId.getMostSignificantBits();
            var playerLeast = playerId.getLeastSignificantBits();
            var exactView = qualificationView;
            var observationEnabled = exactView != null
                    && exactView.enabledFor(server, playerMost, playerLeast);
            try {
                var continuation = new RecoveryContinuation(
                        this,
                        Objects.requireNonNull(
                                onlineReconciliationDependency,
                                "online reconciliation dependency"),
                        player);
                var outcome = recoverPersistedPlayer(player);
                if (observationEnabled) {
                    switch (outcome) {
                        case NoPending ignored -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.NO_PENDING,
                                P4E2QualificationFacade.RecoveryDetail.NONE,
                                0,
                                0);
                        case Cleared cleared -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.CLEARED,
                                P4E2QualificationFacade.RecoveryDetail.NONE,
                                cleared.entriesCleared(),
                                0);
                        case Replayed replayed -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.REPLAYED,
                                P4E2QualificationFacade.RecoveryDetail.NONE,
                                0,
                                replayed.stepsReplayed());
                        case ClearedAndReplayed changed -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.CLEARED_AND_REPLAYED,
                                P4E2QualificationFacade.RecoveryDetail.NONE,
                                changed.entriesCleared(),
                                changed.stepsReplayed());
                        case Conflict conflict -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.CONFLICT,
                                switch (conflict.code()) {
                                    case THIRD_STATE ->
                                            P4E2QualificationFacade.RecoveryDetail.THIRD_STATE;
                                    case CLEAR_PREPARATION_REJECTED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .CLEAR_PREPARATION_REJECTED;
                                    case CLEAR_COMMIT_REJECTED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .CLEAR_COMMIT_REJECTED;
                                    case REPLAY_PREPARATION_REJECTED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .REPLAY_PREPARATION_REJECTED;
                                    case REPLAY_CURRENTNESS_CHANGED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .REPLAY_CURRENTNESS_CHANGED;
                                    case REPLAY_PUBLICATION_REJECTED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .REPLAY_PUBLICATION_REJECTED;
                                    case REPLAY_UNEXPECTED_NO_OP ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .REPLAY_UNEXPECTED_NO_OP;
                                },
                                conflict.entriesClearedBeforeFailure(),
                                conflict.stepsReplayedBeforeFailure());
                        case TargetInvalid invalid -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.TARGET_INVALID,
                                switch (invalid.reason()) {
                                    case MISSING ->
                                            P4E2QualificationFacade.RecoveryDetail.TARGET_MISSING;
                                    case OWNER_MISMATCH ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .TARGET_OWNER_MISMATCH;
                                },
                                invalid.entriesClearedBeforeFailure(),
                                invalid.stepsReplayedBeforeFailure());
                        case Unavailable unavailable -> exactView.recordRecovery(
                                server,
                                playerMost,
                                playerLeast,
                                P4E2QualificationFacade.RecoveryVariant.UNAVAILABLE,
                                switch (unavailable.reason()) {
                                    case JOURNAL_NOT_BOOTSTRAPPED ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .JOURNAL_NOT_BOOTSTRAPPED;
                                    case JOURNAL_UNAVAILABLE ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .JOURNAL_UNAVAILABLE;
                                    case STORE_UNAVAILABLE ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .STORE_UNAVAILABLE;
                                    case AUTHORITY_UNAVAILABLE ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .AUTHORITY_UNAVAILABLE;
                                    case ATTACHMENT_PRESERVED_RAW_QUARANTINE ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
                                    case ATTACHMENT_OVERSIZE_QUARANTINE ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .ATTACHMENT_OVERSIZE_QUARANTINE;
                                    case RUNTIME_EXCEPTION ->
                                            P4E2QualificationFacade.RecoveryDetail
                                                    .RUNTIME_EXCEPTION;
                                },
                                unavailable.entriesClearedBeforeFailure(),
                                unavailable.stepsReplayedBeforeFailure());
                    }
                }
                continuation.consume(this, outcome);
            } finally {
                if (observationEnabled) {
                    // A normal continuation return guarantees that its wrapper recorded every
                    // mandatory result; an incomplete cell at this finally is therefore abnormal.
                    exactView.completeAfterContinuation(server, playerMost, playerLeast);
                }
            }
        }
    }

    private static void requireE2RecoveryVocabularyInitialized() {
        if (P4E2OnlineReconciliationDependency.RecoveryKind.NO_PENDING.ordinal() != 0
                || recoveryKind(RecoveryUnavailableReason.JOURNAL_NOT_BOOTSTRAPPED)
                        != P4E2OnlineReconciliationDependency.RecoveryKind
                                .JOURNAL_NOT_BOOTSTRAPPED) {
            throw new IllegalStateException("P4E2_RECOVERY_KIND_ORDER_MISMATCH");
        }
    }

    private static P4E2OnlineReconciliationDependency.RecoveryKind recoveryKind(
            RecoveryUnavailableReason reason) {
        return switch (reason) {
            case JOURNAL_NOT_BOOTSTRAPPED ->
                    P4E2OnlineReconciliationDependency.RecoveryKind.JOURNAL_NOT_BOOTSTRAPPED;
            case JOURNAL_UNAVAILABLE ->
                    P4E2OnlineReconciliationDependency.RecoveryKind.JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE ->
                    P4E2OnlineReconciliationDependency.RecoveryKind.STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE ->
                    P4E2OnlineReconciliationDependency.RecoveryKind.AUTHORITY_UNAVAILABLE;
            case ATTACHMENT_PRESERVED_RAW_QUARANTINE ->
                    P4E2OnlineReconciliationDependency.RecoveryKind
                            .ATTACHMENT_PRESERVED_RAW_QUARANTINE;
            case ATTACHMENT_OVERSIZE_QUARANTINE ->
                    P4E2OnlineReconciliationDependency.RecoveryKind
                            .ATTACHMENT_OVERSIZE_QUARANTINE;
            case RUNTIME_EXCEPTION ->
                    P4E2OnlineReconciliationDependency.RecoveryKind.RUNTIME_EXCEPTION;
        };
    }

    private static Map<SkillId, LatestTuple> indexLatestStates(
            List<PlayerSkillAttachmentService.LatestStateView> latestStates) {
        Objects.requireNonNull(latestStates, "latestStates");
        if (latestStates.size() > MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES) {
            throw new IllegalStateException(
                    "latest-state observation exceeded the Attachment route ceiling");
        }
        var indexed = new HashMap<SkillId, LatestTuple>();
        for (var latest : latestStates) {
            Objects.requireNonNull(latest, "latest state");
            var previous = indexed.put(
                    latest.skillId(),
                    new LatestTuple(latest.pointer(), latest.mutationGeneration()));
            if (previous != null) {
                throw new IllegalStateException("latest-state observation repeated a SkillId");
            }
        }
        return indexed;
    }

    private static Classification classify(
            LatestTuple loaded,
            List<SkillDefinitionStoreSubmissionPort.PendingRecoveryStep> steps) {
        var first = steps.getFirst();
        if (loaded.matches(first.expectedPointer(), first.expectedGeneration())) {
            return Classification.BASE;
        }
        for (int index = 0; index < steps.size(); index++) {
            var step = steps.get(index);
            if (loaded.matches(Optional.of(step.targetPointer()), step.targetGeneration())) {
                return index == steps.size() - 1
                        ? Classification.finalTarget(index)
                        : Classification.intermediate(index);
            }
        }
        return Classification.THIRD;
    }

    private static RecoveryOutcome successfulOutcome(int entriesCleared, int stepsReplayed) {
        if (entriesCleared > 0 && stepsReplayed > 0) {
            return new ClearedAndReplayed(entriesCleared, stepsReplayed);
        }
        if (entriesCleared > 0) {
            return new Cleared(entriesCleared);
        }
        if (stepsReplayed > 0) {
            return new Replayed(stepsReplayed);
        }
        return NoPending.INSTANCE;
    }

    private static Unavailable unavailable(
            RecoveryUnavailableReason reason,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> exceptionClass) {
        return new Unavailable(reason, entriesCleared, stepsReplayed, exceptionClass);
    }

    private static RecoveryUnavailableReason mapJournalUnavailable(
            SkillDefinitionStoreSubmissionPort.PendingRecoveryUnavailableReason reason) {
        return switch (reason) {
            case JOURNAL_NOT_BOOTSTRAPPED ->
                    RecoveryUnavailableReason.JOURNAL_NOT_BOOTSTRAPPED;
            case JOURNAL_UNAVAILABLE -> RecoveryUnavailableReason.JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE -> RecoveryUnavailableReason.STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE -> RecoveryUnavailableReason.AUTHORITY_UNAVAILABLE;
        };
    }

    private static RecoveryUnavailableReason mapStoreUnavailable(
            SkillDefinitionStoreSubmissionPort.UnavailableReason reason) {
        return switch (reason) {
            case JOURNAL_NOT_BOOTSTRAPPED ->
                    RecoveryUnavailableReason.JOURNAL_NOT_BOOTSTRAPPED;
            case JOURNAL_UNAVAILABLE -> RecoveryUnavailableReason.JOURNAL_UNAVAILABLE;
            case STORE_UNAVAILABLE -> RecoveryUnavailableReason.STORE_UNAVAILABLE;
            case AUTHORITY_UNAVAILABLE -> RecoveryUnavailableReason.AUTHORITY_UNAVAILABLE;
        };
    }

    private static RecoveryUnavailableReason mapAttachmentUnavailable(
            PlayerSkillAttachmentService.UnavailableReason reason) {
        return switch (reason) {
            case PRESERVED_RAW_QUARANTINE ->
                    RecoveryUnavailableReason.ATTACHMENT_PRESERVED_RAW_QUARANTINE;
            case OVERSIZE_QUARANTINE ->
                    RecoveryUnavailableReason.ATTACHMENT_OVERSIZE_QUARANTINE;
        };
    }

    private static String boundedClassName(RuntimeException exception) {
        var className = exception.getClass().getName();
        return className.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? className
                : className.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }

    private static int addBounded(int current, int delta) {
        if (delta <= 0) {
            throw new IllegalStateException("recovery progress delta must be positive");
        }
        var result = Math.addExact(current, delta);
        if (result > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES) {
            throw new IllegalStateException("recovery progress exceeded the journal entry ceiling");
        }
        return result;
    }

    /** Same-call-chain, consume-first bridge from one completed typed recovery to P4-E2. */
    public static final class RecoveryContinuation {
        private SkillSubmissionRecoveryService owner;
        private P4E2OnlineReconciliationDependency dependency;
        private ServerPlayer player;
        private final ContinuationLifecycle lifecycle = new ContinuationLifecycle();

        private RecoveryContinuation(
                SkillSubmissionRecoveryService owner,
                P4E2OnlineReconciliationDependency dependency,
                ServerPlayer player) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.dependency = Objects.requireNonNull(dependency, "dependency");
            this.player = Objects.requireNonNull(player, "player");
        }

        private void consume(SkillSubmissionRecoveryService candidate, RecoveryOutcome outcome) {
            lifecycle.claim();
            var exactOwner = owner;
            var exactDependency = dependency;
            var exactPlayer = player;
            owner = null;
            dependency = null;
            player = null;
            if (exactOwner != Objects.requireNonNull(candidate, "candidate")) {
                throw new IllegalStateException("P4E2_RECOVERY_CONTINUATION_OWNER_MISMATCH");
            }
            var exactOutcome = Objects.requireNonNull(outcome, "outcome");
            var kind = exactOutcome.e2Kind();
            var entriesCleared = exactOutcome.e2EntriesCleared();
            var stepsReplayed = exactOutcome.e2StepsReplayed();
            var existingExceptionClass = exactOutcome.e2ExceptionClass();
            exactDependency.reconcileAfterRecovery(
                    exactPlayer,
                    this,
                    kind,
                    entriesCleared,
                    stepsReplayed,
                    existingExceptionClass);
        }
    }

    static final class ContinuationLifecycle {
        private boolean claimed;

        void claim() {
            if (claimed) {
                throw new IllegalStateException(
                        "P4E2_RECOVERY_CONTINUATION_ALREADY_CONSUMED");
            }
            claimed = true;
        }
    }

    interface Dependencies {
        SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection observePendingRecovery(
                Object server, SkillOwnerId owner);

        PlayerSkillAttachmentService.Result<
                        List<PlayerSkillAttachmentService.LatestStateView>>
                observeLatestStates(Object player);

        ClearPreparation prepareClear(
                Object server,
                SkillOwnerId owner,
                SkillId skillId,
                int confirmedTargetGeneration,
                SkillReference confirmedTargetPointer);

        ClearCommit commitClear(Object server, ClearHandle handle);

        TransitionPreparation prepareTransition(
                Object player, SkillId skillId, SkillReference targetPointer);

        TransitionCheck checkCurrent(Object player, TransitionHandle transition);

        TransitionPublication publishTransition(Object player, TransitionHandle transition);
    }

    interface ClearHandle {
    }

    sealed interface ClearPreparation permits ClearPreparation.Prepared,
            ClearPreparation.NoOp, ClearPreparation.Rejected, ClearPreparation.Unavailable {
        record Prepared(ClearHandle handle) implements ClearPreparation {
            public Prepared {
                Objects.requireNonNull(handle, "handle");
            }
        }

        enum NoOp implements ClearPreparation {
            INSTANCE
        }

        enum Rejected implements ClearPreparation {
            INSTANCE
        }

        record Unavailable(RecoveryUnavailableReason reason) implements ClearPreparation {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    sealed interface ClearCommit permits ClearCommit.Cleared, ClearCommit.NoOp,
            ClearCommit.Rejected, ClearCommit.Unavailable {
        record Cleared(int entriesRemoved) implements ClearCommit {
            public Cleared {
                if (entriesRemoved <= 0) {
                    throw new IllegalArgumentException("entriesRemoved must be positive");
                }
            }
        }

        enum NoOp implements ClearCommit {
            INSTANCE
        }

        enum Rejected implements ClearCommit {
            INSTANCE
        }

        record Unavailable(RecoveryUnavailableReason reason) implements ClearCommit {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    interface TransitionHandle {
        SkillOwnerId owner();

        SkillId skillId();

        Optional<SkillReference> expectedPointer();

        int expectedGeneration();

        Optional<SkillReference> targetPointer();

        int targetGeneration();

        boolean isNoOp();

        boolean isBoundTo(Object server);
    }

    sealed interface TransitionPreparation permits TransitionPreparation.Prepared,
            TransitionPreparation.Rejected, TransitionPreparation.Unavailable {
        record Prepared(TransitionHandle handle) implements TransitionPreparation {
            public Prepared {
                Objects.requireNonNull(handle, "handle");
            }
        }

        enum Rejected implements TransitionPreparation {
            INSTANCE
        }

        record Unavailable(RecoveryUnavailableReason reason)
                implements TransitionPreparation {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    sealed interface TransitionCheck permits TransitionCheck.Current,
            TransitionCheck.Changed, TransitionCheck.Unavailable {
        enum Current implements TransitionCheck {
            INSTANCE
        }

        enum Changed implements TransitionCheck {
            INSTANCE
        }

        record Unavailable(RecoveryUnavailableReason reason) implements TransitionCheck {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    sealed interface TransitionPublication permits TransitionPublication.Applied,
            TransitionPublication.Rejected, TransitionPublication.Unavailable {
        enum Applied implements TransitionPublication {
            INSTANCE
        }

        enum Rejected implements TransitionPublication {
            INSTANCE
        }

        record Unavailable(RecoveryUnavailableReason reason)
                implements TransitionPublication {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    sealed interface RecoveryOutcome permits NoPending, Cleared, Replayed,
            ClearedAndReplayed, Conflict, TargetInvalid, Unavailable {
        P4E2OnlineReconciliationDependency.RecoveryKind e2Kind();

        int e2EntriesCleared();

        int e2StepsReplayed();

        Optional<String> e2ExceptionClass();
    }

    enum NoPending implements RecoveryOutcome {
        INSTANCE;

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.NO_PENDING;
        }

        @Override
        public int e2EntriesCleared() {
            return 0;
        }

        @Override
        public int e2StepsReplayed() {
            return 0;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    record Cleared(int entriesCleared) implements RecoveryOutcome {
        Cleared {
            requireProgress(entriesCleared, "entriesCleared");
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.CLEARED;
        }

        @Override
        public int e2EntriesCleared() {
            return entriesCleared;
        }

        @Override
        public int e2StepsReplayed() {
            return 0;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    record Replayed(int stepsReplayed) implements RecoveryOutcome {
        Replayed {
            requireProgress(stepsReplayed, "stepsReplayed");
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.REPLAYED;
        }

        @Override
        public int e2EntriesCleared() {
            return 0;
        }

        @Override
        public int e2StepsReplayed() {
            return stepsReplayed;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    record ClearedAndReplayed(int entriesCleared, int stepsReplayed)
            implements RecoveryOutcome {
        ClearedAndReplayed {
            requireProgress(entriesCleared, "entriesCleared");
            requireProgress(stepsReplayed, "stepsReplayed");
            requireCombinedProgress(entriesCleared, stepsReplayed);
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.CLEARED_AND_REPLAYED;
        }

        @Override
        public int e2EntriesCleared() {
            return entriesCleared;
        }

        @Override
        public int e2StepsReplayed() {
            return stepsReplayed;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    record Conflict(
            SkillId skillId,
            RecoveryConflictCode code,
            int entriesClearedBeforeFailure,
            int stepsReplayedBeforeFailure) implements RecoveryOutcome {
        Conflict {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(code, "code");
            requireBoundedProgress(entriesClearedBeforeFailure, "entriesClearedBeforeFailure");
            requireBoundedProgress(stepsReplayedBeforeFailure, "stepsReplayedBeforeFailure");
            requireCombinedProgress(entriesClearedBeforeFailure, stepsReplayedBeforeFailure);
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.CONFLICT;
        }

        @Override
        public int e2EntriesCleared() {
            return entriesClearedBeforeFailure;
        }

        @Override
        public int e2StepsReplayed() {
            return stepsReplayedBeforeFailure;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    /**
     * Bounded target failure. The variant plus its exact D1 reason forms TARGET_MISSING or
     * TARGET_OWNER_MISMATCH without duplicating the Store-side vocabulary.
     */
    record TargetInvalid(
            SkillId skillId,
            SkillReference target,
            SkillDefinitionStoreSubmissionPort.PendingRecoveryTargetFailure reason,
            int entriesClearedBeforeFailure,
            int stepsReplayedBeforeFailure) implements RecoveryOutcome {
        TargetInvalid {
            Objects.requireNonNull(skillId, "skillId");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(reason, "reason");
            if (!target.skillId().equals(skillId)) {
                throw new IllegalArgumentException("invalid target must match its SkillId route");
            }
            requireBoundedProgress(entriesClearedBeforeFailure, "entriesClearedBeforeFailure");
            requireBoundedProgress(stepsReplayedBeforeFailure, "stepsReplayedBeforeFailure");
            requireCombinedProgress(entriesClearedBeforeFailure, stepsReplayedBeforeFailure);
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return P4E2OnlineReconciliationDependency.RecoveryKind.TARGET_INVALID;
        }

        @Override
        public int e2EntriesCleared() {
            return entriesClearedBeforeFailure;
        }

        @Override
        public int e2StepsReplayed() {
            return stepsReplayedBeforeFailure;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return NO_RECOVERY_EXCEPTION;
        }
    }

    record Unavailable(
            RecoveryUnavailableReason reason,
            int entriesClearedBeforeFailure,
            int stepsReplayedBeforeFailure,
            Optional<String> exceptionClass) implements RecoveryOutcome {
        Unavailable {
            Objects.requireNonNull(reason, "reason");
            exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass");
            requireBoundedProgress(entriesClearedBeforeFailure, "entriesClearedBeforeFailure");
            requireBoundedProgress(stepsReplayedBeforeFailure, "stepsReplayedBeforeFailure");
            requireCombinedProgress(entriesClearedBeforeFailure, stepsReplayedBeforeFailure);
            if (exceptionClass.stream().anyMatch(value -> value.isEmpty()
                    || value.length() > MagicSafetyCeilings.MAX_STRING_LENGTH)) {
                throw new IllegalArgumentException("exceptionClass must be a bounded class name");
            }
            if ((reason == RecoveryUnavailableReason.RUNTIME_EXCEPTION)
                    != exceptionClass.isPresent()) {
                throw new IllegalArgumentException(
                        "only runtime failures carry an exception class");
            }
        }

        @Override
        public P4E2OnlineReconciliationDependency.RecoveryKind e2Kind() {
            return recoveryKind(reason);
        }

        @Override
        public int e2EntriesCleared() {
            return entriesClearedBeforeFailure;
        }

        @Override
        public int e2StepsReplayed() {
            return stepsReplayedBeforeFailure;
        }

        @Override
        public Optional<String> e2ExceptionClass() {
            return exceptionClass;
        }
    }

    enum RecoveryConflictCode {
        THIRD_STATE,
        CLEAR_PREPARATION_REJECTED,
        CLEAR_COMMIT_REJECTED,
        REPLAY_PREPARATION_REJECTED,
        REPLAY_CURRENTNESS_CHANGED,
        REPLAY_PUBLICATION_REJECTED,
        REPLAY_UNEXPECTED_NO_OP
    }

    enum RecoveryUnavailableReason {
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        // Typed refinements of the recovery vocabulary's ATTACHMENT_UNAVAILABLE code.
        ATTACHMENT_PRESERVED_RAW_QUARANTINE,
        ATTACHMENT_OVERSIZE_QUARANTINE,
        RUNTIME_EXCEPTION
    }

    private enum ClassificationKind {
        BASE_EXPECTED,
        INTERMEDIATE_TARGET,
        FINAL_TARGET,
        THIRD_STATE
    }

    private record Classification(ClassificationKind kind, int targetIndex) {
        private static final Classification BASE =
                new Classification(ClassificationKind.BASE_EXPECTED, -1);
        private static final Classification THIRD =
                new Classification(ClassificationKind.THIRD_STATE, -1);

        private Classification {
            Objects.requireNonNull(kind, "kind");
            if ((kind == ClassificationKind.INTERMEDIATE_TARGET
                            || kind == ClassificationKind.FINAL_TARGET)
                    != (targetIndex >= 0)) {
                throw new IllegalArgumentException("classification target index mismatch");
            }
        }

        private static Classification intermediate(int index) {
            return new Classification(ClassificationKind.INTERMEDIATE_TARGET, index);
        }

        private static Classification finalTarget(int index) {
            return new Classification(ClassificationKind.FINAL_TARGET, index);
        }
    }

    private record LatestTuple(Optional<SkillReference> pointer, int generation) {
        private static final LatestTuple IMPLICIT = new LatestTuple(Optional.empty(), 0);

        private LatestTuple {
            pointer = Objects.requireNonNull(pointer, "pointer");
            if (generation < 0) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
        }

        private boolean matches(
                Optional<SkillReference> candidatePointer, int candidateGeneration) {
            return generation == candidateGeneration && pointer.equals(candidatePointer);
        }

        private static LatestTuple target(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryStep step) {
            return new LatestTuple(Optional.of(step.targetPointer()), step.targetGeneration());
        }
    }

    private record ProductionClearHandle(
            SkillDefinitionStoreSubmissionPort.PreparedJournalPrefixClear handle)
            implements ClearHandle {
        private ProductionClearHandle {
            Objects.requireNonNull(handle, "handle");
        }
    }

    private record ProductionTransitionHandle(
            PlayerSkillAttachmentService.PreparedPlayerSkillTransition transition)
            implements TransitionHandle {
        private ProductionTransitionHandle {
            Objects.requireNonNull(transition, "transition");
        }

        @Override
        public SkillOwnerId owner() {
            return transition.owner();
        }

        @Override
        public SkillId skillId() {
            return transition.skillId();
        }

        @Override
        public Optional<SkillReference> expectedPointer() {
            return transition.expectedPointer();
        }

        @Override
        public int expectedGeneration() {
            return transition.expectedGeneration();
        }

        @Override
        public Optional<SkillReference> targetPointer() {
            return transition.targetPointer();
        }

        @Override
        public int targetGeneration() {
            return transition.targetGeneration();
        }

        @Override
        public boolean isNoOp() {
            return transition.isNoOp();
        }

        @Override
        public boolean isBoundTo(Object server) {
            return server instanceof MinecraftServer minecraftServer
                    && transition.isBoundTo(minecraftServer);
        }
    }

    private static final class ProductionDependencies implements Dependencies {
        private final PlayerSkillAttachmentService attachmentService;
        private final SkillDefinitionStoreSubmissionPort storePort;

        private ProductionDependencies(
                PlayerSkillAttachmentService attachmentService,
                SkillDefinitionStoreSubmissionPort storePort) {
            this.attachmentService = Objects.requireNonNull(
                    attachmentService, "attachmentService");
            this.storePort = Objects.requireNonNull(storePort, "storePort");
        }

        @Override
        public SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                observePendingRecovery(Object server, SkillOwnerId owner) {
            return storePort.observePendingRecovery((MinecraftServer) server, owner);
        }

        @Override
        public PlayerSkillAttachmentService.Result<
                        List<PlayerSkillAttachmentService.LatestStateView>>
                observeLatestStates(Object player) {
            return attachmentService.observeLatestStates((ServerPlayer) player);
        }

        @Override
        public ClearPreparation prepareClear(
                Object server,
                SkillOwnerId owner,
                SkillId skillId,
                int confirmedTargetGeneration,
                SkillReference confirmedTargetPointer) {
            var result = storePort.prepareJournalPrefixClear(
                    (MinecraftServer) server,
                    owner,
                    skillId,
                    confirmedTargetGeneration,
                    confirmedTargetPointer);
            return switch (result) {
                case SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Prepared
                        prepared -> new ClearPreparation.Prepared(
                                new ProductionClearHandle(prepared.handle()));
                case SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.NoOp
                        ignored ->
                        ClearPreparation.NoOp.INSTANCE;
                case SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Rejected
                        ignored ->
                        ClearPreparation.Rejected.INSTANCE;
                case SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Unavailable
                        unavailable -> new ClearPreparation.Unavailable(
                                mapStoreUnavailable(unavailable.reason()));
            };
        }

        @Override
        public ClearCommit commitClear(Object server, ClearHandle handle) {
            if (!(handle instanceof ProductionClearHandle production)) {
                throw new IllegalStateException("journal clear handle is not production-owned");
            }
            return switch (storePort.commitPreparedJournalClear(
                    (MinecraftServer) server, production.handle())) {
                case SkillDefinitionStoreSubmissionPort.JournalClearCommitResult.Cleared cleared ->
                        new ClearCommit.Cleared(cleared.entriesRemoved());
                case SkillDefinitionStoreSubmissionPort.JournalClearCommitResult.NoOp ignored ->
                        ClearCommit.NoOp.INSTANCE;
                case SkillDefinitionStoreSubmissionPort.JournalClearCommitResult
                                .PreparedBaseMismatch ignored ->
                        ClearCommit.Rejected.INSTANCE;
                case SkillDefinitionStoreSubmissionPort.JournalClearCommitResult.Unavailable
                        unavailable -> new ClearCommit.Unavailable(
                                mapStoreUnavailable(unavailable.reason()));
            };
        }

        @Override
        public TransitionPreparation prepareTransition(
                Object player, SkillId skillId, SkillReference targetPointer) {
            var result = attachmentService.prepareLatestTransitionToCurrent(
                    (ServerPlayer) player, skillId, targetPointer);
            if (result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
                return new TransitionPreparation.Unavailable(
                        mapAttachmentUnavailable(unavailable.reason()));
            }
            if (!(result instanceof PlayerSkillAttachmentService.Available<
                    PlayerSkillAttachmentService.TransitionPreparation> available)) {
                throw new IllegalStateException("unknown transition preparation result");
            }
            var preparation = available.value();
            return switch (preparation) {
                case PlayerSkillAttachmentService.Prepared prepared ->
                        new TransitionPreparation.Prepared(
                                new ProductionTransitionHandle(prepared.transition()));
                case PlayerSkillAttachmentService.TransitionRejected ignored ->
                        TransitionPreparation.Rejected.INSTANCE;
            };
        }

        @Override
        public TransitionCheck checkCurrent(Object player, TransitionHandle transition) {
            if (!(transition instanceof ProductionTransitionHandle production)) {
                throw new IllegalStateException("Attachment transition is not production-owned");
            }
            var result = attachmentService.checkPreparedTransitionCurrent(
                    (ServerPlayer) player, production.transition());
            if (result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
                return new TransitionCheck.Unavailable(
                        mapAttachmentUnavailable(unavailable.reason()));
            }
            if (!(result instanceof PlayerSkillAttachmentService.Available<
                    PlayerSkillAttachmentService.TransitionCurrentness> available)) {
                throw new IllegalStateException("unknown transition currentness result");
            }
            var currentness = available.value();
            return currentness == PlayerSkillAttachmentService.TransitionCurrentness.CURRENT
                    ? TransitionCheck.Current.INSTANCE
                    : TransitionCheck.Changed.INSTANCE;
        }

        @Override
        public TransitionPublication publishTransition(
                Object player, TransitionHandle transition) {
            if (!(transition instanceof ProductionTransitionHandle production)) {
                throw new IllegalStateException("Attachment transition is not production-owned");
            }
            var result = attachmentService.publishPreparedTransition(
                    (ServerPlayer) player, production.transition());
            if (result instanceof PlayerSkillAttachmentService.Unavailable<?> unavailable) {
                return new TransitionPublication.Unavailable(
                        mapAttachmentUnavailable(unavailable.reason()));
            }
            if (!(result instanceof PlayerSkillAttachmentService.Available<
                    PlayerSkillAttachmentService.MutationOutcome> available)) {
                throw new IllegalStateException("unknown transition publication result");
            }
            var outcome = available.value();
            return outcome == PlayerSkillAttachmentService.Applied.INSTANCE
                    ? TransitionPublication.Applied.INSTANCE
                    : TransitionPublication.Rejected.INSTANCE;
        }
    }

    private static void requireProgress(int value, String name) {
        if (value <= 0 || value > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES) {
            throw new IllegalArgumentException(name + " must be within the journal entry ceiling");
        }
    }

    private static void requireBoundedProgress(int value, String name) {
        if (value < 0 || value > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES) {
            throw new IllegalArgumentException(name + " must be within the journal entry ceiling");
        }
    }

    private static void requireCombinedProgress(int entriesCleared, int stepsReplayed) {
        if ((long) entriesCleared + stepsReplayed
                > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES) {
            throw new IllegalArgumentException("combined recovery progress exceeded the ceiling");
        }
    }
}
