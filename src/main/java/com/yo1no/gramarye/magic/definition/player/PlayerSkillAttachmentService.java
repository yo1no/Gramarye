package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.PlayerSkillAttachmentAdmissionSource;
import com.yo1no.gramarye.magic.definition.store.PlayerSkillAttachmentReconciliationCapability;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.bus.api.IEventBus;

/** Server-thread controlled port for one player's permanent skill Attachment. */
public final class PlayerSkillAttachmentService {
    private static final long CLEARED_ENCODED_WIDTH = -1L;
    private static final int MAX_RECONCILIATION_EXCEPTION_CLASS_LENGTH = 160;
    private final PlayerSkillAttachmentAdmission rootAuditAdmission;

    PlayerSkillAttachmentService() {
        this.rootAuditAdmission = new PlayerSkillAttachmentAdmission();
    }

    /** Registers the unique Attachment type on the mod bus and returns a stateless service. */
    public static PlayerSkillAttachmentService registerOn(IEventBus modBus) {
        PlayerSkillAttachments.register(Objects.requireNonNull(modBus, "modBus"));
        return new PlayerSkillAttachmentService();
    }

    /**
     * Tests the one legal changed-pointer generation step without exposing the internal
     * generation arithmetic owner.
     */
    public static boolean isChangedGenerationSuccessor(
            int expectedGeneration, int targetGeneration) {
        if (expectedGeneration < 0 || targetGeneration < 0) {
            return false;
        }
        var successor = MutationGeneration.successor(expectedGeneration);
        return successor.isPresent() && successor.getAsInt() == targetGeneration;
    }

    /** Supplies the existing P4-C encoded admission boundary to the closed E1 bridge. */
    public static long maximumRootAuditAttachmentEncodedBytes() {
        return AttachmentTagSize.maximum();
    }

    public Result<Optional<SkillDraft>> findDraft(ServerPlayer player, SkillId skillId) {
        requireServerThread(player);
        Objects.requireNonNull(skillId, "skillId");
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(Optional.empty());
            case ObservedPlayerSkillAttachment.Ready ready -> new Available<>(ready.state()
                    .drafts()
                    .stream()
                    .filter(entry -> entry.skillId().equals(skillId))
                    .map(PlayerDraftEntry::draft)
                    .findFirst());
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    public Result<Integer> draftCount(ServerPlayer player) {
        requireServerThread(player);
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored -> new Available<>(0);
            case ObservedPlayerSkillAttachment.Ready ready ->
                    new Available<>(ready.state().drafts().size());
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    public Result<Optional<LatestStateView>> findLatestState(
            ServerPlayer player, SkillId skillId) {
        requireServerThread(player);
        Objects.requireNonNull(skillId, "skillId");
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(Optional.empty());
            case ObservedPlayerSkillAttachment.Ready ready -> new Available<>(ready.state()
                    .latestStates()
                    .stream()
                    .filter(state -> state.skillId().equals(skillId))
                    .map(LatestStateView::from)
                    .findFirst());
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    /**
     * Observes every explicit latest-pointer route from one non-installing Attachment read.
     *
     * <p>Absence from this immutable canonical list retains its V0 meaning: an implicit empty
     * pointer at generation zero.</p>
     */
    public Result<List<LatestStateView>> observeLatestStates(ServerPlayer player) {
        requireServerThread(player);
        return latestStateBatch(observeChecked(player));
    }

    public Result<Optional<SkillReference>> equippedAt(ServerPlayer player, int slot) {
        requireServerThread(player);
        requireEquippedSlot(slot);
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(Optional.empty());
            case ObservedPlayerSkillAttachment.Ready ready -> new Available<>(ready.state()
                    .equipped()
                    .stream()
                    .filter(entry -> entry.slot() == slot)
                    .map(EquippedSkillReference::reference)
                    .findFirst());
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    public Result<EditorStateView> editorState(ServerPlayer player) {
        requireServerThread(player);
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(EditorStateView.empty());
            case ObservedPlayerSkillAttachment.Ready ready ->
                    new Available<>(EditorStateView.from(ready.state().editor()));
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    public Result<SkillOwnerId> ownerId(ServerPlayer player) {
        requireServerThread(player);
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(ownerOf(player));
            case ObservedPlayerSkillAttachment.Ready ignored ->
                    new Available<>(ownerOf(player));
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    public Result<MutationOutcome> putDraft(ServerPlayer player, SkillDraft draft) {
        requireServerThread(player);
        Objects.requireNonNull(draft, "draft");
        var observed = observeChecked(player);
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return new Unavailable<>(quarantined.reason());
        }

        var encodedResult = SkillDraftPersistenceFacade.encodeCurrent(draft);
        if (encodedResult instanceof SkillDraftPersistenceFacade.EncodeRejected) {
            return new Available<>(new MutationRejected(
                    MutationRejectionCode.DRAFT_PERSISTENCE_REJECTED));
        }
        var encoded = ((SkillDraftPersistenceFacade.Encoded) encodedResult).draft();
        var base = readyForMutation(observed);
        var existing = base.drafts().stream()
                .filter(entry -> entry.skillId().equals(draft.skillId()))
                .findFirst();
        if (existing.isPresent() && existing.orElseThrow().encodedDraft().equals(encoded)) {
            return new Available<>(NoOp.INSTANCE);
        }
        if (existing.isEmpty()
                && !PlayerSkillAttachmentPersistenceBridge.canAddDraftRoute(
                        base.drafts().size())) {
            return new Available<>(new MutationRejected(
                    MutationRejectionCode.DRAFT_LIMIT_REACHED));
        }

        var replacementDrafts = new ArrayList<>(base.drafts());
        replacementDrafts.removeIf(entry -> entry.skillId().equals(draft.skillId()));
        replacementDrafts.add(new PlayerDraftEntry(draft.skillId(), draft, encoded));
        return publishMutation(player, PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                replacementDrafts,
                base.latestStates(),
                base.equipped(),
                base.editor()));
    }

    public Result<MutationOutcome> removeDraft(ServerPlayer player, SkillId skillId) {
        requireServerThread(player);
        Objects.requireNonNull(skillId, "skillId");
        var observed = observeChecked(player);
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return new Unavailable<>(quarantined.reason());
        }
        if (observed instanceof ObservedPlayerSkillAttachment.Missing) {
            return new Available<>(NoOp.INSTANCE);
        }
        var base = ((ObservedPlayerSkillAttachment.Ready) observed).state();
        if (base.drafts().stream().noneMatch(entry -> entry.skillId().equals(skillId))) {
            return new Available<>(NoOp.INSTANCE);
        }

        var replacementDrafts = new ArrayList<>(base.drafts());
        replacementDrafts.removeIf(entry -> entry.skillId().equals(skillId));
        return publishMutation(player, PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                replacementDrafts,
                base.latestStates(),
                base.equipped(),
                base.editor()));
    }

    public Result<MutationOutcome> setEquipped(
            ServerPlayer player, int slot, Optional<SkillReference> reference) {
        requireServerThread(player);
        requireEquippedSlot(slot);
        reference = Objects.requireNonNull(reference, "reference");
        var observed = observeChecked(player);
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return new Unavailable<>(quarantined.reason());
        }
        if (observed instanceof ObservedPlayerSkillAttachment.Missing && reference.isEmpty()) {
            return new Available<>(NoOp.INSTANCE);
        }
        var base = readyForMutation(observed);
        var current = base.equipped().stream()
                .filter(entry -> entry.slot() == slot)
                .map(EquippedSkillReference::reference)
                .findFirst();
        if (current.equals(reference)) {
            return new Available<>(NoOp.INSTANCE);
        }

        var replacementEquipped = new ArrayList<>(base.equipped());
        replacementEquipped.removeIf(entry -> entry.slot() == slot);
        reference.ifPresent(value -> replacementEquipped.add(
                new EquippedSkillReference(slot, value)));
        return publishMutation(player, PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                base.drafts(),
                base.latestStates(),
                replacementEquipped,
                base.editor()));
    }

    public Result<MutationOutcome> setEditorState(
            ServerPlayer player, EditorStateView editor) {
        requireServerThread(player);
        Objects.requireNonNull(editor, "editor");
        var observed = observeChecked(player);
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return new Unavailable<>(quarantined.reason());
        }
        var replacementEditor = editor.toInternal();
        if (observed instanceof ObservedPlayerSkillAttachment.Missing
                && replacementEditor.equals(PlayerSkillEditorState.empty())) {
            return new Available<>(NoOp.INSTANCE);
        }
        var base = readyForMutation(observed);
        if (base.editor().equals(replacementEditor)) {
            return new Available<>(NoOp.INSTANCE);
        }
        return publishMutation(player, PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                base.drafts(),
                base.latestStates(),
                base.equipped(),
                replacementEditor));
    }

    public Result<TransitionPreparation> prepareLatestTransition(
            ServerPlayer player,
            SkillId skillId,
            Optional<SkillReference> expectedPointer,
            int expectedGeneration,
            Optional<SkillReference> targetPointer) {
        var server = requireServerThread(player);
        Objects.requireNonNull(skillId, "skillId");
        expectedPointer = Objects.requireNonNull(expectedPointer, "expectedPointer");
        targetPointer = Objects.requireNonNull(targetPointer, "targetPointer");
        if (expectedGeneration < 0) {
            throw new IllegalArgumentException("expectedGeneration must be non-negative");
        }
        if (!referenceMatchesRoute(expectedPointer, skillId)
                || !referenceMatchesRoute(targetPointer, skillId)) {
            return new Available<>(new TransitionRejected(
                    TransitionRejectionCode.TARGET_ROUTE_MISMATCH));
        }

        var observed = observeChecked(player);
        return prepareLatestTransitionFromObservation(
                server,
                player,
                skillId,
                expectedPointer,
                expectedGeneration,
                targetPointer,
                observed);
    }

    /**
     * Prepares a present-pointer transition from the exact latest state seen in one Attachment
     * observation.
     */
    public Result<TransitionPreparation> prepareLatestTransitionToCurrent(
            ServerPlayer player, SkillId skillId, SkillReference targetReference) {
        var server = requireServerThread(player);
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(targetReference, "targetReference");
        if (!targetReference.skillId().equals(skillId)) {
            return new Available<>(new TransitionRejected(
                    TransitionRejectionCode.TARGET_ROUTE_MISMATCH));
        }

        var observed = observeChecked(player);
        var current = effectiveLatest(observed, skillId);
        return prepareLatestTransitionFromObservation(
                server,
                player,
                skillId,
                current.pointer(),
                current.mutationGeneration(),
                Optional.of(targetReference),
                observed);
    }

    private static Result<TransitionPreparation> prepareLatestTransitionFromObservation(
            MinecraftServer server,
            ServerPlayer player,
            SkillId skillId,
            Optional<SkillReference> expectedPointer,
            int expectedGeneration,
            Optional<SkillReference> targetPointer,
            ObservedPlayerSkillAttachment observed) {
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return new Unavailable<>(quarantined.reason());
        }
        var current = effectiveLatest(observed, skillId);
        if (current.mutationGeneration() != expectedGeneration) {
            return new Available<>(new TransitionRejected(
                    TransitionRejectionCode.GENERATION_MISMATCH));
        }
        if (!current.pointer().equals(expectedPointer)) {
            return new Available<>(new TransitionRejected(
                    TransitionRejectionCode.POINTER_MISMATCH));
        }

        var original = originalState(observed);
        var owner = ownerOf(player);
        if (current.pointer().equals(targetPointer)) {
            var transition = new PreparedPlayerSkillTransition(
                    server,
                    player.getUUID(),
                    original,
                    owner,
                    skillId,
                    expectedPointer,
                    expectedGeneration,
                    targetPointer,
                    expectedGeneration,
                    null);
            return new Available<>(new Prepared(transition));
        }

        var successor = MutationGeneration.successor(expectedGeneration);
        if (successor.isEmpty()) {
            return new Available<>(new TransitionRejected(
                    TransitionRejectionCode.GENERATION_EXHAUSTED));
        }
        var targetGeneration = successor.getAsInt();
        var base = readyForMutation(observed);
        var replacementLatest = new ArrayList<>(base.latestStates());
        replacementLatest.removeIf(state -> state.skillId().equals(skillId));
        replacementLatest.add(new PlayerLatestState(skillId, targetPointer, targetGeneration));
        var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                base.drafts(), replacementLatest, base.equipped(), base.editor());
        if (rebuilt instanceof PlayerSkillAttachmentBuildResult.Rejected rejected) {
            if (rejected.failure().code()
                            == PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENCODED_CAPACITY_EXCEEDED
                    || rejected.failure().stage()
                            == PlayerSkillAttachmentFailure.Stage.LATEST_COUNT) {
                return new Available<>(new TransitionRejected(
                        TransitionRejectionCode.ATTACHMENT_CAPACITY_REJECTED));
            }
            throw new IllegalStateException(
                    "Ready latest transition rebuild violated invariant: "
                            + rejected.failure().code());
        }
        var replacement = ((PlayerSkillAttachmentBuildResult.Built) rebuilt).ready();
        var transition = new PreparedPlayerSkillTransition(
                server,
                player.getUUID(),
                original,
                owner,
                skillId,
                expectedPointer,
                expectedGeneration,
                targetPointer,
                targetGeneration,
                replacement);
        return new Available<>(new Prepared(transition));
    }

    /** Checks whether a prepared token still describes the current Attachment state. */
    public Result<TransitionCurrentness> checkPreparedTransitionCurrent(
            ServerPlayer player, PreparedPlayerSkillTransition transition) {
        var server = requireServerThread(player);
        Objects.requireNonNull(transition, "transition");
        var validation = validatePreparedTransition(server, player, transition);
        if (validation.unavailableReason() != null) {
            return new Unavailable<>(validation.unavailableReason());
        }
        if (validation.changeCode() != null) {
            return new Available<>(TransitionCurrentness.STATE_CHANGED);
        }
        return new Available<>(TransitionCurrentness.CURRENT);
    }

    public Result<MutationOutcome> publishPreparedTransition(
            ServerPlayer player, PreparedPlayerSkillTransition transition) {
        var server = requireServerThread(player);
        Objects.requireNonNull(transition, "transition");
        var validation = validatePreparedTransition(server, player, transition);
        if (validation.unavailableReason() != null) {
            return new Unavailable<>(validation.unavailableReason());
        }
        if (validation.changeCode() != null) {
            return new Available<>(new MutationRejected(validation.changeCode()));
        }
        if (transition.isNoOp()) {
            return new Available<>(NoOp.INSTANCE);
        }
        if (transition.replacement == null) {
            throw new IllegalStateException("Changed transition has no replacement Ready state");
        }
        Result<MutationOutcome> applied = new Available<>(Applied.INSTANCE);
        publishReplacement(player, transition.replacement);
        return applied;
    }

    private static PreparedTransitionValidation validatePreparedTransition(
            MinecraftServer server,
            ServerPlayer player,
            PreparedPlayerSkillTransition transition) {
        if (server != transition.server) {
            return PreparedTransitionValidation.changed(
                    MutationRejectionCode.WRONG_SERVER);
        }
        if (!player.getUUID().equals(transition.playerId)) {
            return PreparedTransitionValidation.changed(
                    MutationRejectionCode.WRONG_PLAYER);
        }

        var observed = observeChecked(player);
        if (observed instanceof ObservedPlayerSkillAttachment.Quarantined quarantined) {
            return PreparedTransitionValidation.quarantined(quarantined.reason());
        }
        if (!transition.original.matches(observed)) {
            return PreparedTransitionValidation.changed(
                    MutationRejectionCode.STATE_CHANGED);
        }
        var current = effectiveLatest(observed, transition.skillId);
        if (current.mutationGeneration() != transition.expectedGeneration
                || !current.pointer().equals(transition.expectedPointer)) {
            return PreparedTransitionValidation.changed(
                    MutationRejectionCode.STATE_CHANGED);
        }
        return PreparedTransitionValidation.current();
    }

    public Result<PlayerSkillRootProjection> rootProjection(ServerPlayer player) {
        requireServerThread(player);
        return switch (observeChecked(player)) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(new PlayerSkillRootProjection(List.of()));
            case ObservedPlayerSkillAttachment.Ready ready -> new Available<>(
                    new PlayerSkillRootProjection(
                            PlayerSkillAttachmentSourceObservation.rootsForReady(
                                    ready.state())));
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    /**
     * Observes one exact online Attachment source without copying its root projection.
     *
     * <p>The returned service-owned handle is single-use for roots, then remains only as an
     * identity witness until its final discard. It exposes no Attachment state or collection.</p>
     */
    public OnlineRootAuditHandle observeOnlineForRootAudit(ServerPlayer player) {
        requireServerThread(player);
        return new OnlineRootAuditHandle(
                this, PlayerSkillAttachmentSourceObservation.observe(player));
    }

    /** Returns the bounded state classification of one exact online handle. */
    public OnlineRootAuditState onlineRootState(OnlineRootAuditHandle handle) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.NEW);
        if (observation instanceof PlayerSkillAttachmentSourceObservation.Missing) {
            return OnlineRootAuditState.MISSING;
        }
        if (observation instanceof PlayerSkillAttachmentSourceObservation.Ready) {
            return OnlineRootAuditState.READY;
        }
        return OnlineRootAuditState.QUARANTINED;
    }

    /** Returns the quarantine reason; any non-quarantined use is programming misuse. */
    public UnavailableReason onlineRootUnavailableReason(OnlineRootAuditHandle handle) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.NEW);
        if (observation instanceof PlayerSkillAttachmentSourceObservation.Quarantined quarantined) {
            return quarantined.reason();
        }
        throw misuse("P4E1_ONLINE_ROOT_SOURCE_NOT_QUARANTINED");
    }

    /** Returns the exact callback count without exposing root backing. */
    public int onlineRootCount(OnlineRootAuditHandle handle) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.NEW);
        if (!observation.rootsAvailable()) {
            throw misuse("P4E1_ONLINE_ROOT_SOURCE_QUARANTINED");
        }
        return observation.rootCount();
    }

    /** Drains latest-present roots followed by equipped-slot roots exactly once. */
    public void drainOnlineRootProjection(
            OnlineRootAuditHandle handle, RootAuditSink sink) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.NEW);
        if (!observation.rootsAvailable()) {
            throw misuse("P4E1_ONLINE_ROOT_SOURCE_QUARANTINED");
        }
        handle.stage = OnlineHandleStage.WITNESS_ONLY;
        observation.drain(Objects.requireNonNull(sink, "sink"));
    }

    /** Discards the root capability while retaining only the exact online identity witness. */
    public void discardOnlineRootProjection(OnlineRootAuditHandle handle) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.NEW);
        observation.discardRoots();
        handle.stage = OnlineHandleStage.WITNESS_ONLY;
    }

    /** Checks the exact player, presence, and state identity after the root capability is gone. */
    public boolean isOnlineRootWitnessCurrent(
            OnlineRootAuditHandle handle, ServerPlayer player) {
        var observation = requireOnlineRootHandle(handle, OnlineHandleStage.WITNESS_ONLY);
        Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        if (!server.isSameThread()) {
            throw misuse("P4E1_ONLINE_ROOT_WITNESS_WRONG_THREAD");
        }
        if (!player.getUUID().equals(handle.playerId)) {
            throw misuse("P4E1_ONLINE_ROOT_WITNESS_WRONG_PLAYER");
        }
        return observation.isCurrent(player);
    }

    /** Clears the final identity witness exactly once. */
    public void discardOnlineRootWitness(OnlineRootAuditHandle handle) {
        requireOnlineRootHandle(handle, OnlineHandleStage.WITNESS_ONLY);
        handle.stage = OnlineHandleStage.DISCARDED;
        handle.observation = null;
    }

    /** Clears either a NEW or witness-only handle during unpublished failure cleanup. */
    public void discardOnlineRootAuditHandle(OnlineRootAuditHandle handle) {
        Objects.requireNonNull(handle, "handle");
        if (handle.owner != this) {
            throw misuse("P4E1_ONLINE_ROOT_HANDLE_OWNER_MISMATCH");
        }
        if (handle.stage == OnlineHandleStage.DISCARDED || handle.observation == null) {
            throw misuse("P4E1_ONLINE_ROOT_HANDLE_LIFECYCLE_MISMATCH");
        }
        handle.observation.requireCurrentThread();
        if (handle.stage == OnlineHandleStage.NEW) {
            handle.observation.discardRoots();
        }
        handle.stage = OnlineHandleStage.DISCARDED;
        handle.observation = null;
    }

    /**
     * Captures one fresh, non-installing E2 source and every identity needed to prove that the
     * authenticated online player remains current through one synchronous reconciliation chain.
     */
    public OnlineReconciliationHandle observeOnlineForReconciliation(ServerPlayer player) {
        var server = requireServerThread(player);
        var playerList = Objects.requireNonNull(server.getPlayerList(), "server PlayerList");
        var playerId = player.getUUID();
        if (playerList.getPlayer(playerId) != player) {
            throw misuse("P4E2_ONLINE_PLAYER_IDENTITY_MISMATCH");
        }

        var type = PlayerSkillAttachments.type();
        if (!player.hasData(type)) {
            return new OnlineReconciliationHandle(
                    this,
                    server,
                    Thread.currentThread(),
                    playerList,
                    playerId,
                    player,
                    OnlineReconciliationState.MISSING,
                    null);
        }
        var state = player.getData(type);
        var classification = switch (state) {
            case PlayerSkillAttachmentReady ignored -> OnlineReconciliationState.READY;
            case PlayerSkillAttachmentPreservedRaw ignored ->
                    OnlineReconciliationState.PRESERVED_RAW;
            case PlayerSkillAttachmentOversizeMarker ignored ->
                    OnlineReconciliationState.OVERSIZE;
        };
        return new OnlineReconciliationHandle(
                this,
                server,
                Thread.currentThread(),
                playerList,
                playerId,
                player,
                classification,
                state);
    }

    /** Returns the bounded source classification before its projection is consumed or discarded. */
    public OnlineReconciliationState onlineReconciliationState(
            OnlineReconciliationHandle handle) {
        requireOnlineReconciliationHandle(handle, OnlineReconciliationHandleStage.NEW);
        return handle.classification;
    }

    /**
     * Drains every explicit latest route, including empty pointers, followed by every equipped
     * slot. Ordinals are source-local coordinates and no backing collection crosses the API.
     */
    public void drainOnlineReconciliation(
            OnlineReconciliationHandle handle, OnlineReconciliationSink sink) {
        requireOnlineReconciliationHandle(handle, OnlineReconciliationHandleStage.NEW);
        handle.stage = OnlineReconciliationHandleStage.WITNESS_ONLY;
        Objects.requireNonNull(sink, "sink");
        if (handle.classification != OnlineReconciliationState.READY
                || !(handle.stateIdentity instanceof PlayerSkillAttachmentReady ready)) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_SOURCE_NOT_READY");
        }
        for (var ordinal = 0; ordinal < ready.latestStates().size(); ordinal++) {
            var latest = ready.latestStates().get(ordinal);
            sink.latest(
                    ordinal,
                    latest.skillId(),
                    latest.pointer(),
                    latest.mutationGeneration());
        }
        for (var ordinal = 0; ordinal < ready.equipped().size(); ordinal++) {
            var equipped = ready.equipped().get(ordinal);
            sink.equipped(ordinal, equipped.slot(), equipped.reference());
        }
    }

    /** Discards an unused projection while retaining only its exact E2 identity witness. */
    public void discardOnlineReconciliationProjection(OnlineReconciliationHandle handle) {
        requireOnlineReconciliationHandle(handle, OnlineReconciliationHandleStage.NEW);
        handle.stage = OnlineReconciliationHandleStage.WITNESS_ONLY;
    }

    /** Checks the exact server thread, PlayerList, player, presence, and Attachment identity. */
    public boolean isOnlineReconciliationCurrent(
            OnlineReconciliationHandle handle, ServerPlayer player) {
        requireOnlineReconciliationHandle(
                handle, OnlineReconciliationHandleStage.WITNESS_ONLY);
        return onlineReconciliationIdentityCurrent(handle, player);
    }

    /** Clears either a fresh projection or witness-only E2 source exactly once. */
    public void discardOnlineReconciliationHandle(OnlineReconciliationHandle handle) {
        Objects.requireNonNull(handle, "handle");
        if (handle.owner != this) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_HANDLE_OWNER_MISMATCH");
        }
        if (handle.stage == OnlineReconciliationHandleStage.DISCARDED) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_HANDLE_ALREADY_DISCARDED");
        }
        discardOnlineReconciliationHandleInternal(handle);
    }

    /**
     * Claims one store-issued nominal capability, prevalidates the whole generation batch, and
     * prepares one complete immutable replacement without publishing it.
     */
    public ReconciliationPreparationResult prepareOnlineReconciliation(
            ServerPlayer player,
            PlayerSkillAttachmentReconciliationCapability<?, ?> capability) {
        Objects.requireNonNull(capability, "capability");
        OpaqueReconciliationCapability<?, ?> opaque = capability;
        opaque.lifecycle.claim();
        var exactCapability = capability;
        var ownerIdentity = opaque.ownerIdentity;
        var handle = opaque.handleIdentity;
        var handleWitnessIdentity = opaque.handleWitnessIdentity;
        var continuationIdentity = opaque.continuationIdentity;
        var continuationWitnessIdentity = opaque.continuationWitnessIdentity;
        var coordinatorIdentity = opaque.coordinatorIdentity;
        var coordinatorWitnessIdentity = opaque.coordinatorWitnessIdentity;
        var validationIdentity = opaque.validationIdentity;
        var validationWitnessIdentity = opaque.validationWitnessIdentity;
        var staleLatestOrdinals = opaque.staleLatestOrdinals;
        var staleEquippedOrdinals = opaque.staleEquippedOrdinals;
        var qualificationPlayerView = opaque.qualificationPlayerView;
        opaque.consumeAndClear();

        var preparedOwnership = false;
        try {
            Objects.requireNonNull(player, "player");
            if (ownerIdentity != this) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_OWNER_MISMATCH");
            }
            if (handle == null || handle != handleWitnessIdentity) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_HANDLE_MISMATCH");
            }
            if (continuationIdentity == null
                    || continuationIdentity != continuationWitnessIdentity) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_CONTINUATION_MISMATCH");
            }
            if (coordinatorIdentity == null
                    || coordinatorIdentity != coordinatorWitnessIdentity) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_COORDINATOR_MISMATCH");
            }
            if (validationIdentity == null
                    || validationIdentity != validationWitnessIdentity) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_VALIDATION_MISMATCH");
            }
            Objects.requireNonNull(staleLatestOrdinals, "staleLatestOrdinals");
            Objects.requireNonNull(staleEquippedOrdinals, "staleEquippedOrdinals");
            requireOnlineReconciliationHandle(
                    handle, OnlineReconciliationHandleStage.WITNESS_ONLY);
            if (!onlineReconciliationIdentityCurrent(handle, player)) {
                return ReconciliationStateChanged.INSTANCE;
            }
            if (handle.classification != OnlineReconciliationState.READY
                    || !(handle.stateIdentity instanceof PlayerSkillAttachmentReady ready)) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_SOURCE_NOT_READY");
            }

            var rebuilt = rebuildForOnlineReconciliation(
                    ready,
                    staleLatestOrdinals,
                    staleEquippedOrdinals);
            if (rebuilt instanceof ReconciliationRebuildOutcome.GenerationExhausted) {
                return ReconciliationGenerationExhausted.INSTANCE;
            }
            if (rebuilt instanceof ReconciliationRebuildOutcome.Rejected rejected) {
                return rejected.result();
            }
            var replacement = ((ReconciliationRebuildOutcome.Prepared) rebuilt).replacement();
            var result = new PreparedReconciliation(new PreparedOnlineReconciliation(
                    this,
                    exactCapability,
                    handle,
                    ready,
                    replacement,
                    qualificationPlayerView));
            preparedOwnership = true;
            return result;
        } finally {
            if (staleLatestOrdinals != null) {
                Arrays.fill(staleLatestOrdinals, -1);
            }
            if (staleEquippedOrdinals != null) {
                Arrays.fill(staleEquippedOrdinals, -1);
            }
            if (!preparedOwnership) {
                discardOnlineReconciliationHandleInternal(handle);
            }
        }
    }

    /** Performs the first exact player/Attachment identity recheck without consuming the token. */
    public ReconciliationCurrentness checkPreparedReconciliationCurrent(
            ServerPlayer player, PreparedOnlineReconciliation prepared) {
        Objects.requireNonNull(player, "player");
        requirePreparedReconciliation(prepared);
        return preparedReconciliationCurrent(prepared, player)
                ? ReconciliationCurrentness.CURRENT
                : ReconciliationCurrentness.STATE_CHANGED;
    }

    /**
     * Consumes the prepared token, performs the final no-yield identity recheck, and publishes via
     * the existing sole Attachment replacement seam at most once.
     */
    public ReconciliationPublication publishPreparedReconciliation(
            ServerPlayer player, PreparedOnlineReconciliation prepared) {
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.consumed
                || prepared.owner == null
                || prepared.capabilityIdentity == null
                || prepared.handle == null
                || prepared.original == null
                || prepared.replacement == null) {
            throw misuse("P4E2_PREPARED_RECONCILIATION_LIFECYCLE_MISMATCH");
        }
        var ownerIdentity = prepared.owner;
        var capabilityIdentity = prepared.capabilityIdentity;
        var handle = prepared.handle;
        var original = prepared.original;
        var replacement = prepared.replacement;
        var qualificationPlayerView = prepared.qualificationPlayerView;
        prepared.consumeAndClear();
        try {
            Objects.requireNonNull(player, "player");
            if (ownerIdentity != this
                    || capabilityIdentity == null
                    || original == null
                    || handle.stateIdentity != original
                    || !onlineReconciliationIdentityCurrent(handle, player)) {
                return ReconciliationPublication.STATE_CHANGED;
            }
            publishReplacement(player, replacement, qualificationPlayerView);
            return ReconciliationPublication.APPLIED;
        } finally {
            discardOnlineReconciliationHandleInternal(handle);
        }
    }

    /** Clears an unpublished prepared replacement exactly once. */
    public void discardPreparedReconciliation(PreparedOnlineReconciliation prepared) {
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.consumed
                || prepared.owner == null
                || prepared.capabilityIdentity == null
                || prepared.handle == null
                || prepared.original == null
                || prepared.replacement == null) {
            throw misuse("P4E2_PREPARED_RECONCILIATION_LIFECYCLE_MISMATCH");
        }
        var ownerIdentity = prepared.owner;
        var handle = prepared.handle;
        prepared.consumeAndClear();
        if (ownerIdentity != this) {
            discardOnlineReconciliationHandleInternal(handle);
            throw misuse("P4E2_PREPARED_RECONCILIATION_OWNER_MISMATCH");
        }
        discardOnlineReconciliationHandleInternal(handle);
    }

    private OnlineReconciliationHandle requireOnlineReconciliationHandle(
            OnlineReconciliationHandle handle, OnlineReconciliationHandleStage required) {
        Objects.requireNonNull(handle, "handle");
        if (handle.owner != this) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_HANDLE_OWNER_MISMATCH");
        }
        if (handle.stage != required
                || handle.serverIdentity == null
                || handle.threadIdentity == null
                || handle.playerListIdentity == null
                || handle.playerId == null
                || handle.playerIdentity == null
                || handle.classification == null) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_HANDLE_LIFECYCLE_MISMATCH");
        }
        if (Thread.currentThread() != handle.threadIdentity
                || !handle.serverIdentity.isSameThread()) {
            throw misuse("P4E2_ONLINE_RECONCILIATION_HANDLE_WRONG_THREAD");
        }
        return handle;
    }

    private boolean onlineReconciliationIdentityCurrent(
            OnlineReconciliationHandle handle, ServerPlayer player) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(player, "player");
        var bindingsPresent = handle.serverIdentity != null
                && handle.threadIdentity != null
                && handle.playerListIdentity != null
                && handle.playerId != null
                && handle.playerIdentity != null
                && handle.classification != null;
        var serviceIdentityCurrent = handle.owner == this;
        var stageCurrent = handle.stage == OnlineReconciliationHandleStage.WITNESS_ONLY;
        var threadIdentityCurrent = handle.threadIdentity != null
                && Thread.currentThread() == handle.threadIdentity;
        var serverThreadCurrent = handle.serverIdentity != null
                && handle.serverIdentity.isSameThread();
        var playerIdentityCurrent = player == handle.playerIdentity;
        var serverIdentityCurrent = handle.serverIdentity != null
                && player.getServer() == handle.serverIdentity;
        var playerIdCurrent = handle.playerId != null
                && player.getUUID().equals(handle.playerId);
        var playerListIdentityCurrent = bindingsPresent
                && threadIdentityCurrent
                && serverThreadCurrent
                && handle.serverIdentity.getPlayerList() == handle.playerListIdentity;
        var authenticatedLookupCurrent = playerListIdentityCurrent
                && playerIdCurrent
                && handle.playerListIdentity.getPlayer(handle.playerId) == handle.playerIdentity;
        var attachmentPresenceCurrent = false;
        var attachmentStateCurrent = false;
        if (serviceIdentityCurrent
                && stageCurrent
                && bindingsPresent
                && threadIdentityCurrent
                && serverThreadCurrent
                && playerIdentityCurrent
                && serverIdentityCurrent
                && playerIdCurrent
                && playerListIdentityCurrent
                && authenticatedLookupCurrent) {
            var type = PlayerSkillAttachments.type();
            var attachmentPresent = player.hasData(type);
            var missing = handle.classification == OnlineReconciliationState.MISSING;
            attachmentPresenceCurrent = missing
                    ? handle.stateIdentity == null && !attachmentPresent
                    : handle.stateIdentity != null && attachmentPresent;
            attachmentStateCurrent = missing
                    ? handle.stateIdentity == null
                    : handle.stateIdentity != null
                            && attachmentPresent
                            && player.getData(type) == handle.stateIdentity;
        }
        return onlineReconciliationFactsCurrent(new OnlineReconciliationCurrentnessFacts(
                serviceIdentityCurrent,
                stageCurrent,
                bindingsPresent,
                threadIdentityCurrent,
                serverThreadCurrent,
                playerIdentityCurrent,
                serverIdentityCurrent,
                playerIdCurrent,
                playerListIdentityCurrent,
                authenticatedLookupCurrent,
                attachmentPresenceCurrent,
                attachmentStateCurrent));
    }

    static boolean onlineReconciliationFactsCurrent(
            OnlineReconciliationCurrentnessFacts facts) {
        Objects.requireNonNull(facts, "facts");
        return facts.serviceIdentityCurrent()
                && facts.stageCurrent()
                && facts.bindingsPresent()
                && facts.threadIdentityCurrent()
                && facts.serverThreadCurrent()
                && facts.playerIdentityCurrent()
                && facts.serverIdentityCurrent()
                && facts.playerIdCurrent()
                && facts.playerListIdentityCurrent()
                && facts.authenticatedLookupCurrent()
                && facts.attachmentPresenceCurrent()
                && facts.attachmentStateCurrent();
    }

    private void discardOnlineReconciliationHandleInternal(
            OnlineReconciliationHandle handle) {
        if (handle == null || handle.stage == OnlineReconciliationHandleStage.DISCARDED) {
            return;
        }
        handle.stage = OnlineReconciliationHandleStage.DISCARDED;
        handle.serverIdentity = null;
        handle.threadIdentity = null;
        handle.playerListIdentity = null;
        handle.playerId = null;
        handle.playerIdentity = null;
        handle.classification = null;
        handle.stateIdentity = null;
    }

    private static boolean[] selectedOrdinals(
            int[] ordinals, int sourceSize, String family) {
        Objects.requireNonNull(ordinals, "ordinals");
        if (sourceSize < 0) {
            throw new IllegalArgumentException("sourceSize must be non-negative");
        }
        var selected = new boolean[sourceSize];
        for (var ordinal : ordinals) {
            if (ordinal < 0 || ordinal >= sourceSize || selected[ordinal]) {
                throw misuse("P4E2_" + family + "_STALE_COORDINATE_INVALID");
            }
            selected[ordinal] = true;
        }
        return selected;
    }

    static ReconciliationRebuildOutcome rebuildForOnlineReconciliation(
            PlayerSkillAttachmentReady ready,
            int[] staleLatestOrdinals,
            int[] staleEquippedOrdinals) {
        Objects.requireNonNull(ready, "ready");
        Objects.requireNonNull(staleLatestOrdinals, "staleLatestOrdinals");
        Objects.requireNonNull(staleEquippedOrdinals, "staleEquippedOrdinals");
        var staleLatest = selectedOrdinals(
                staleLatestOrdinals, ready.latestStates().size(), "LATEST");
        var staleEquipped = selectedOrdinals(
                staleEquippedOrdinals, ready.equipped().size(), "EQUIPPED");
        if (staleLatestOrdinals.length == 0 && staleEquippedOrdinals.length == 0) {
            throw misuse("P4E2_RECONCILIATION_CAPABILITY_HAS_NO_STALE_COORDINATES");
        }

        var successorByOrdinal = new int[ready.latestStates().size()];
        for (var ordinal = 0; ordinal < ready.latestStates().size(); ordinal++) {
            if (!staleLatest[ordinal]) {
                continue;
            }
            var latest = ready.latestStates().get(ordinal);
            if (latest.pointer().isEmpty()) {
                throw misuse("P4E2_EMPTY_LATEST_ROUTE_CLASSIFIED_STALE");
            }
            var successor = MutationGeneration.successor(latest.mutationGeneration());
            if (successor.isEmpty()) {
                return ReconciliationRebuildOutcome.GenerationExhausted.INSTANCE;
            }
            successorByOrdinal[ordinal] = successor.getAsInt();
        }

        var replacementLatest = new ArrayList<PlayerLatestState>(
                ready.latestStates().size());
        for (var ordinal = 0; ordinal < ready.latestStates().size(); ordinal++) {
            var latest = ready.latestStates().get(ordinal);
            replacementLatest.add(staleLatest[ordinal]
                    ? new PlayerLatestState(
                            latest.skillId(),
                            Optional.empty(),
                            successorByOrdinal[ordinal])
                    : latest);
        }
        var replacementEquipped = new ArrayList<EquippedSkillReference>(
                ready.equipped().size() - staleEquippedOrdinals.length);
        for (var ordinal = 0; ordinal < ready.equipped().size(); ordinal++) {
            if (!staleEquipped[ordinal]) {
                replacementEquipped.add(ready.equipped().get(ordinal));
            }
        }

        var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                ready.drafts(), replacementLatest, replacementEquipped, ready.editor());
        if (rebuilt instanceof PlayerSkillAttachmentBuildResult.Rejected rejected) {
            return new ReconciliationRebuildOutcome.Rejected(
                    reconciliationBuildRejected(rejected.failure()));
        }
        return new ReconciliationRebuildOutcome.Prepared(
                ((PlayerSkillAttachmentBuildResult.Built) rebuilt).ready());
    }

    private static ReconciliationBuildRejected reconciliationBuildRejected(
            PlayerSkillAttachmentFailure failure) {
        Objects.requireNonNull(failure, "failure");
        var reason = failure.code()
                        == PlayerSkillAttachmentFailure.Code
                                .ATTACHMENT_ENCODED_CAPACITY_EXCEEDED
                ? ReconciliationBuildFailure.CAPACITY_REJECTED
                : failure.code() == PlayerSkillAttachmentFailure.Code.INTERNAL_CODEC_EXCEPTION
                                && !failure.exceptionClass().isEmpty()
                        ? ReconciliationBuildFailure.INTERNAL_RUNTIME_FAILURE
                        : ReconciliationBuildFailure.INVARIANT_REJECTED;
        var exceptionClass = failure.exceptionClass().isEmpty()
                ? Optional.<String>empty()
                : Optional.of(boundedReconciliationExceptionClass(
                        failure.exceptionClass()));
        return new ReconciliationBuildRejected(reason, exceptionClass);
    }

    private static String boundedReconciliationExceptionClass(String className) {
        Objects.requireNonNull(className, "className");
        return className.length() <= MAX_RECONCILIATION_EXCEPTION_CLASS_LENGTH
                ? className
                : className.substring(0, MAX_RECONCILIATION_EXCEPTION_CLASS_LENGTH);
    }

    private void requirePreparedReconciliation(PreparedOnlineReconciliation prepared) {
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.owner != this) {
            throw misuse("P4E2_PREPARED_RECONCILIATION_OWNER_MISMATCH");
        }
        if (prepared.consumed
                || prepared.capabilityIdentity == null
                || prepared.handle == null
                || prepared.original == null
                || prepared.replacement == null) {
            throw misuse("P4E2_PREPARED_RECONCILIATION_LIFECYCLE_MISMATCH");
        }
    }

    private boolean preparedReconciliationCurrent(
            PreparedOnlineReconciliation prepared, ServerPlayer player) {
        return prepared.owner == this
                && prepared.capabilityIdentity != null
                && prepared.original != null
                && prepared.handle != null
                && prepared.handle.stateIdentity == prepared.original
                && onlineReconciliationIdentityCurrent(prepared.handle, player);
    }

    private PlayerSkillAttachmentSourceObservation requireOnlineRootHandle(
            OnlineRootAuditHandle handle, OnlineHandleStage required) {
        Objects.requireNonNull(handle, "handle");
        if (handle.owner != this) {
            throw misuse("P4E1_ONLINE_ROOT_HANDLE_OWNER_MISMATCH");
        }
        if (handle.stage != required || handle.observation == null) {
            throw misuse("P4E1_ONLINE_ROOT_HANDLE_LIFECYCLE_MISMATCH");
        }
        handle.observation.requireCurrentThread();
        return handle.observation;
    }

    /**
     * Consumes one closed store-issued source and performs the existing full P4-C admission.
     *
     * <p>The source is irreversibly claimed and cleared before any identity, binding, size, or
     * semantic validation. Expected data failures become a bounded rejection; programming misuse
     * fails fast.</p>
     */
    public RootAuditAdmissionResult admitForRootAudit(
            PlayerSkillAttachmentAdmissionSource<?, ?> source) {
        Objects.requireNonNull(source, "source");
        OpaqueAdmissionSource<?, ?> opaque = source;
        if (opaque.consumed) {
            throw misuse("P4E1_ADMISSION_SOURCE_ALREADY_CONSUMED");
        }

        var owner = opaque.owner;
        var inputIdentity = opaque.inputIdentity;
        var measurementInputIdentity = opaque.measurementInputIdentity;
        var exactEncodedWidth = opaque.exactEncodedWidth;
        var providerIdentity = opaque.providerIdentity;
        var providerWitnessIdentity = opaque.providerWitnessIdentity;
        opaque.consumed = true;
        opaque.inputIdentity = null;
        opaque.measurementInputIdentity = null;
        opaque.exactEncodedWidth = CLEARED_ENCODED_WIDTH;
        opaque.providerIdentity = null;
        opaque.providerWitnessIdentity = null;

        if (owner != this) {
            throw misuse("P4E1_ADMISSION_SOURCE_OWNER_MISMATCH");
        }
        if (inputIdentity != measurementInputIdentity) {
            throw misuse("P4E1_ADMISSION_INPUT_IDENTITY_MISMATCH");
        }
        if (providerIdentity != providerWitnessIdentity) {
            throw misuse("P4E1_ADMISSION_PROVIDER_IDENTITY_MISMATCH");
        }
        if (!(inputIdentity instanceof net.minecraft.nbt.Tag input)) {
            throw misuse("P4E1_ADMISSION_INPUT_BINDING_INVALID");
        }
        if (!(providerIdentity instanceof net.minecraft.core.HolderLookup.Provider provider)) {
            throw misuse("P4E1_ADMISSION_PROVIDER_BINDING_INVALID");
        }

        final AttachmentTagSizeResult measured;
        if (exactEncodedWidth >= 1L && exactEncodedWidth <= AttachmentTagSize.maximum()) {
            measured = new AttachmentTagSizeResult.WithinLimit(exactEncodedWidth);
        } else if (exactEncodedWidth > AttachmentTagSize.maximum()) {
            measured = new AttachmentTagSizeResult.Exceeded(
                    AttachmentTagSize.observedAtLeast(), AttachmentTagSize.maximum());
        } else {
            throw misuse("P4E1_ADMISSION_SIZE_PROOF_INVALID");
        }

        if (measured instanceof AttachmentTagSizeResult.Exceeded) {
            return RootAuditOversize.INSTANCE;
        }
        return switch (rootAuditAdmission.admit(input, measured, Optional.of(provider))) {
            case PlayerSkillAttachmentAdmission.Admitted admitted ->
                    new RootAuditAdmitted(
                            this,
                            admitted.ready().latestStates(),
                            admitted.ready().equipped());
            case PlayerSkillAttachmentAdmission.Rejected rejected ->
                    new RootAuditRejected(rejected.failure());
            case PlayerSkillAttachmentAdmission.Oversize ignored ->
                    RootAuditOversize.INSTANCE;
        };
    }

    /**
     * Returns the exact number of callbacks a later successful drain will make.
     *
     * <p>A future global caller must reserve this many raw-root claims before draining. If any
     * reservation fails, it must discard the handle instead; no callback or root append is then
     * permitted.</p>
     */
    public int rootCount(RootAuditAdmitted admitted) {
        Objects.requireNonNull(admitted, "admitted");
        if (admitted.consumed) {
            throw misuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED");
        }
        if (admitted.owner != this) {
            admitted.consumeAndClear();
            throw misuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH");
        }
        return admitted.rootCount;
    }

    /** Drains latest roots first and equipped roots second after caller-side full reservation. */
    public void drainRootProjection(RootAuditAdmitted admitted, RootAuditSink sink) {
        Objects.requireNonNull(admitted, "admitted");
        if (admitted.consumed) {
            throw misuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED");
        }
        var owner = admitted.owner;
        var latest = admitted.latest;
        var equipped = admitted.equipped;
        admitted.consumeAndClear();
        if (owner != this) {
            throw misuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH");
        }
        Objects.requireNonNull(sink, "sink");
        for (var state : latest) {
            state.pointer().ifPresent(sink::latest);
        }
        for (var entry : equipped) {
            sink.equipped(entry.slot(), entry.reference());
        }
    }

    /** Consumes a reserved-but-unpublished projection without issuing callbacks. */
    public void discardRootProjection(RootAuditAdmitted admitted) {
        Objects.requireNonNull(admitted, "admitted");
        if (admitted.consumed) {
            throw misuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED");
        }
        var owner = admitted.owner;
        admitted.consumeAndClear();
        if (owner != this) {
            throw misuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH");
        }
    }

    /** Package-private registered-state observation for normal GameTest assertions. */
    ObservedPlayerSkillAttachment observe(ServerPlayer player) {
        requireServerThread(player);
        return observeChecked(player);
    }

    private static ObservedPlayerSkillAttachment observeChecked(ServerPlayer player) {
        var type = PlayerSkillAttachments.type();
        if (!player.hasData(type)) {
            return ObservedPlayerSkillAttachment.Missing.INSTANCE;
        }
        return switch (player.getData(type)) {
            case PlayerSkillAttachmentReady ready ->
                    new ObservedPlayerSkillAttachment.Ready(ready);
            case PlayerSkillAttachmentPreservedRaw ignored ->
                    new ObservedPlayerSkillAttachment.Quarantined(
                            UnavailableReason.PRESERVED_RAW_QUARANTINE);
            case PlayerSkillAttachmentOversizeMarker ignored ->
                    new ObservedPlayerSkillAttachment.Quarantined(
                            UnavailableReason.OVERSIZE_QUARANTINE);
        };
    }

    static Result<List<LatestStateView>> latestStateBatch(
            ObservedPlayerSkillAttachment observed) {
        Objects.requireNonNull(observed, "observed");
        return switch (observed) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    new Available<>(List.of());
            case ObservedPlayerSkillAttachment.Ready ready -> new Available<>(ready.state()
                    .latestStates()
                    .stream()
                    .map(LatestStateView::from)
                    .toList());
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
    }

    private static PlayerSkillAttachmentReady readyForMutation(
            ObservedPlayerSkillAttachment observed) {
        return switch (observed) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    PlayerSkillAttachmentPersistenceBridge.freshEmptyReady();
            case ObservedPlayerSkillAttachment.Ready ready -> ready.state();
            case ObservedPlayerSkillAttachment.Quarantined ignored ->
                    throw new IllegalStateException("Quarantined state cannot be mutated");
        };
    }

    private static Result<MutationOutcome> publishMutation(
            ServerPlayer player, PlayerSkillAttachmentBuildResult rebuilt) {
        if (rebuilt instanceof PlayerSkillAttachmentBuildResult.Rejected rejected) {
            return new Available<>(new MutationRejected(mapBuildFailure(rejected.failure())));
        }
        Result<MutationOutcome> applied = new Available<>(Applied.INSTANCE);
        publishReplacement(
                player, ((PlayerSkillAttachmentBuildResult.Built) rebuilt).ready());
        return applied;
    }

    private static void publishReplacement(
            ServerPlayer player, PlayerSkillAttachmentReady replacement) {
        publishReplacement(player, replacement, null);
    }

    private static void publishReplacement(
            ServerPlayer player,
            PlayerSkillAttachmentReady replacement,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        var type = PlayerSkillAttachments.type();
        MinecraftServer qualificationServer = null;
        long playerMost = 0L;
        long playerLeast = 0L;
        if (qualificationPlayerView != null) {
            qualificationServer = Objects.requireNonNull(
                    player.getServer(), "player server");
            var playerId = player.getUUID();
            playerMost = playerId.getMostSignificantBits();
            playerLeast = playerId.getLeastSignificantBits();
            qualificationPlayerView.recordE2SetDataAttempt(
                    qualificationServer, playerMost, playerLeast);
        }
        player.setData(type, replacement);
        if (qualificationPlayerView != null) {
            qualificationPlayerView.recordE2SetDataSuccess(
                    qualificationServer, playerMost, playerLeast);
        }
    }

    private static MutationRejectionCode mapBuildFailure(
            PlayerSkillAttachmentFailure failure) {
        if (failure.code()
                == PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENCODED_CAPACITY_EXCEEDED) {
            return MutationRejectionCode.ATTACHMENT_CAPACITY_REJECTED;
        }
        if (failure.stage() == PlayerSkillAttachmentFailure.Stage.DRAFT_COUNT) {
            return MutationRejectionCode.DRAFT_LIMIT_REACHED;
        }
        return MutationRejectionCode.ATTACHMENT_INVARIANT_REJECTED;
    }

    private static PlayerLatestState effectiveLatest(
            ObservedPlayerSkillAttachment observed, SkillId skillId) {
        if (observed instanceof ObservedPlayerSkillAttachment.Ready ready) {
            return ready.state().latestStates().stream()
                    .filter(state -> state.skillId().equals(skillId))
                    .findFirst()
                    .orElseGet(() -> implicitLatest(skillId));
        }
        return implicitLatest(skillId);
    }

    private static PlayerLatestState implicitLatest(SkillId skillId) {
        return new PlayerLatestState(skillId, Optional.empty(), 0);
    }

    private static OriginalAttachmentState originalState(
            ObservedPlayerSkillAttachment observed) {
        return switch (observed) {
            case ObservedPlayerSkillAttachment.Missing ignored ->
                    OriginalAttachmentState.Missing.INSTANCE;
            case ObservedPlayerSkillAttachment.Ready ready ->
                    new OriginalAttachmentState.Present(ready.state());
            case ObservedPlayerSkillAttachment.Quarantined ignored ->
                    throw new IllegalStateException("Quarantined state cannot prepare a transition");
        };
    }

    private static boolean referenceMatchesRoute(
            Optional<SkillReference> reference, SkillId skillId) {
        return reference.isEmpty() || reference.orElseThrow().skillId().equals(skillId);
    }

    private static SkillOwnerId ownerOf(ServerPlayer player) {
        return new SkillOwnerId(player.getUUID());
    }

    private static MinecraftServer requireServerThread(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        var server = Objects.requireNonNull(player.getServer(), "player server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("Player skill Attachment access requires the server thread");
        }
        return server;
    }

    private static void requireEquippedSlot(int slot) {
        if (!PlayerSkillAttachmentPersistenceBridge.equippedSlotWithinLimit(slot)) {
            throw new IllegalArgumentException("slot is outside the hard equipped boundary");
        }
    }

    private static IllegalStateException misuse(String code) {
        return new IllegalStateException(code);
    }

    /** Generic storage only; no operation accepts this forgeable base type. */
    public abstract static class OpaqueAdmissionSource<I, P> {
        private final PlayerSkillAttachmentService owner;
        private I inputIdentity;
        private I measurementInputIdentity;
        private long exactEncodedWidth;
        private P providerIdentity;
        private P providerWitnessIdentity;
        private boolean consumed;

        protected OpaqueAdmissionSource(
                PlayerSkillAttachmentService owner,
                I inputIdentity,
                I measurementInputIdentity,
                long exactEncodedWidth,
                P providerIdentity,
                P providerWitnessIdentity) {
            this.owner = owner;
            this.inputIdentity = inputIdentity;
            this.measurementInputIdentity = measurementInputIdentity;
            this.exactEncodedWidth = exactEncodedWidth;
            this.providerIdentity = providerIdentity;
            this.providerWitnessIdentity = providerWitnessIdentity;
        }
    }

    /** Typed, callback-only E2 projection; no Attachment backing crosses the boundary. */
    public interface OnlineReconciliationSink {
        void latest(
                int ordinal,
                SkillId skillId,
                Optional<SkillReference> pointer,
                int mutationGeneration);

        void equipped(int ordinal, int slot, SkillReference reference);
    }

    public enum OnlineReconciliationState {
        MISSING,
        READY,
        PRESERVED_RAW,
        OVERSIZE
    }

    /** Opaque exact-identity E2 source and witness with a strict single-use lifecycle. */
    public static final class OnlineReconciliationHandle {
        private final PlayerSkillAttachmentService owner;
        private MinecraftServer serverIdentity;
        private Thread threadIdentity;
        private PlayerList playerListIdentity;
        private UUID playerId;
        private ServerPlayer playerIdentity;
        private OnlineReconciliationState classification;
        private PlayerSkillAttachmentState stateIdentity;
        private OnlineReconciliationHandleStage stage =
                OnlineReconciliationHandleStage.NEW;

        private OnlineReconciliationHandle(
                PlayerSkillAttachmentService owner,
                MinecraftServer serverIdentity,
                Thread threadIdentity,
                PlayerList playerListIdentity,
                UUID playerId,
                ServerPlayer playerIdentity,
                OnlineReconciliationState classification,
                PlayerSkillAttachmentState stateIdentity) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.serverIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
            this.threadIdentity = Objects.requireNonNull(threadIdentity, "threadIdentity");
            this.playerListIdentity = Objects.requireNonNull(
                    playerListIdentity, "playerListIdentity");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.playerIdentity = Objects.requireNonNull(playerIdentity, "playerIdentity");
            this.classification = Objects.requireNonNull(classification, "classification");
            this.stateIdentity = stateIdentity;
            if ((classification == OnlineReconciliationState.MISSING)
                    != (stateIdentity == null)) {
                throw new IllegalArgumentException(
                        "missing reconciliation source presence mismatch");
            }
            if ((classification == OnlineReconciliationState.READY)
                    != (stateIdentity instanceof PlayerSkillAttachmentReady)) {
                throw new IllegalArgumentException(
                        "Ready reconciliation source identity mismatch");
            }
            if (classification == OnlineReconciliationState.PRESERVED_RAW
                    && !(stateIdentity instanceof PlayerSkillAttachmentPreservedRaw)) {
                throw new IllegalArgumentException(
                        "preserved-raw reconciliation source identity mismatch");
            }
            if (classification == OnlineReconciliationState.OVERSIZE
                    && !(stateIdentity instanceof PlayerSkillAttachmentOversizeMarker)) {
                throw new IllegalArgumentException(
                        "oversize reconciliation source identity mismatch");
            }
        }
    }

    private enum OnlineReconciliationHandleStage {
        NEW,
        WITNESS_ONLY,
        DISCARDED
    }

    record OnlineReconciliationCurrentnessFacts(
            boolean serviceIdentityCurrent,
            boolean stageCurrent,
            boolean bindingsPresent,
            boolean threadIdentityCurrent,
            boolean serverThreadCurrent,
            boolean playerIdentityCurrent,
            boolean serverIdentityCurrent,
            boolean playerIdCurrent,
            boolean playerListIdentityCurrent,
            boolean authenticatedLookupCurrent,
            boolean attachmentPresenceCurrent,
            boolean attachmentStateCurrent) {
    }

    static final class ReconciliationCapabilityLifecycle {
        private boolean claimed;

        void claim() {
            if (claimed) {
                throw misuse("P4E2_RECONCILIATION_CAPABILITY_ALREADY_CONSUMED");
            }
            claimed = true;
        }
    }

    /**
     * Generic storage owner for the cross-package sealed E2 capability. No operation accepts this
     * forgeable base type; only the exact store-owned sealed nominal subtype is consumable.
     */
    public abstract static class OpaqueReconciliationCapability<C, V> {
        private PlayerSkillAttachmentService ownerIdentity;
        private OnlineReconciliationHandle handleIdentity;
        private OnlineReconciliationHandle handleWitnessIdentity;
        private SkillSubmissionRecoveryService.RecoveryContinuation continuationIdentity;
        private SkillSubmissionRecoveryService.RecoveryContinuation
                continuationWitnessIdentity;
        private C coordinatorIdentity;
        private C coordinatorWitnessIdentity;
        private V validationIdentity;
        private V validationWitnessIdentity;
        private int[] staleLatestOrdinals;
        private int[] staleEquippedOrdinals;
        private P4E2QualificationFacade.PlayerView qualificationPlayerView;
        private final ReconciliationCapabilityLifecycle lifecycle =
                new ReconciliationCapabilityLifecycle();

        protected OpaqueReconciliationCapability(
                PlayerSkillAttachmentService ownerIdentity,
                OnlineReconciliationHandle handleIdentity,
                OnlineReconciliationHandle handleWitnessIdentity,
                SkillSubmissionRecoveryService.RecoveryContinuation continuationIdentity,
                SkillSubmissionRecoveryService.RecoveryContinuation
                        continuationWitnessIdentity,
                C coordinatorIdentity,
                C coordinatorWitnessIdentity,
                V validationIdentity,
                V validationWitnessIdentity,
                int[] staleLatestOrdinals,
                int[] staleEquippedOrdinals) {
            this(
                    ownerIdentity,
                    handleIdentity,
                    handleWitnessIdentity,
                    continuationIdentity,
                    continuationWitnessIdentity,
                    coordinatorIdentity,
                    coordinatorWitnessIdentity,
                    validationIdentity,
                    validationWitnessIdentity,
                    staleLatestOrdinals,
                    staleEquippedOrdinals,
                    null);
        }

        protected OpaqueReconciliationCapability(
                PlayerSkillAttachmentService ownerIdentity,
                OnlineReconciliationHandle handleIdentity,
                OnlineReconciliationHandle handleWitnessIdentity,
                SkillSubmissionRecoveryService.RecoveryContinuation continuationIdentity,
                SkillSubmissionRecoveryService.RecoveryContinuation
                        continuationWitnessIdentity,
                C coordinatorIdentity,
                C coordinatorWitnessIdentity,
                V validationIdentity,
                V validationWitnessIdentity,
                int[] staleLatestOrdinals,
                int[] staleEquippedOrdinals,
                P4E2QualificationFacade.PlayerView qualificationPlayerView) {
            this.ownerIdentity = Objects.requireNonNull(ownerIdentity, "ownerIdentity");
            this.handleIdentity = Objects.requireNonNull(handleIdentity, "handleIdentity");
            this.handleWitnessIdentity = Objects.requireNonNull(
                    handleWitnessIdentity, "handleWitnessIdentity");
            this.continuationIdentity = Objects.requireNonNull(
                    continuationIdentity, "continuationIdentity");
            this.continuationWitnessIdentity = Objects.requireNonNull(
                    continuationWitnessIdentity, "continuationWitnessIdentity");
            this.coordinatorIdentity = Objects.requireNonNull(
                    coordinatorIdentity, "coordinatorIdentity");
            this.coordinatorWitnessIdentity = Objects.requireNonNull(
                    coordinatorWitnessIdentity, "coordinatorWitnessIdentity");
            this.validationIdentity = Objects.requireNonNull(
                    validationIdentity, "validationIdentity");
            this.validationWitnessIdentity = Objects.requireNonNull(
                    validationWitnessIdentity, "validationWitnessIdentity");
            this.staleLatestOrdinals = Objects.requireNonNull(
                            staleLatestOrdinals, "staleLatestOrdinals")
                    .clone();
            this.staleEquippedOrdinals = Objects.requireNonNull(
                            staleEquippedOrdinals, "staleEquippedOrdinals")
                    .clone();
            this.qualificationPlayerView = qualificationPlayerView;
        }

        private void consumeAndClear() {
            ownerIdentity = null;
            handleIdentity = null;
            handleWitnessIdentity = null;
            continuationIdentity = null;
            continuationWitnessIdentity = null;
            coordinatorIdentity = null;
            coordinatorWitnessIdentity = null;
            validationIdentity = null;
            validationWitnessIdentity = null;
            staleLatestOrdinals = null;
            staleEquippedOrdinals = null;
            qualificationPlayerView = null;
        }
    }

    sealed interface ReconciliationRebuildOutcome
            permits ReconciliationRebuildOutcome.Prepared,
                    ReconciliationRebuildOutcome.GenerationExhausted,
                    ReconciliationRebuildOutcome.Rejected {
        record Prepared(PlayerSkillAttachmentReady replacement)
                implements ReconciliationRebuildOutcome {
            public Prepared {
                Objects.requireNonNull(replacement, "replacement");
            }
        }

        enum GenerationExhausted implements ReconciliationRebuildOutcome {
            INSTANCE
        }

        record Rejected(ReconciliationBuildRejected result)
                implements ReconciliationRebuildOutcome {
            public Rejected {
                Objects.requireNonNull(result, "result");
            }
        }
    }

    public sealed interface ReconciliationPreparationResult
            permits PreparedReconciliation,
                    ReconciliationGenerationExhausted,
                    ReconciliationStateChanged,
                    ReconciliationBuildRejected {
    }

    public record PreparedReconciliation(PreparedOnlineReconciliation prepared)
            implements ReconciliationPreparationResult {
        public PreparedReconciliation {
            Objects.requireNonNull(prepared, "prepared");
        }
    }

    /** P4-C int mutation-generation exhaustion; it is distinct from the E1 long index. */
    public enum ReconciliationGenerationExhausted
            implements ReconciliationPreparationResult {
        INSTANCE
    }

    public enum ReconciliationStateChanged implements ReconciliationPreparationResult {
        INSTANCE
    }

    public record ReconciliationBuildRejected(
            ReconciliationBuildFailure reason, Optional<String> exceptionClass)
            implements ReconciliationPreparationResult {
        public ReconciliationBuildRejected {
            Objects.requireNonNull(reason, "reason");
            exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass");
            if (exceptionClass.stream().anyMatch(value -> value.isEmpty()
                    || value.length() > MAX_RECONCILIATION_EXCEPTION_CLASS_LENGTH)) {
                throw new IllegalArgumentException(
                        "exceptionClass must be a bounded class name");
            }
            if ((reason == ReconciliationBuildFailure.INTERNAL_RUNTIME_FAILURE)
                    != exceptionClass.isPresent()) {
                throw new IllegalArgumentException(
                        "only runtime reconciliation failures carry an exception class");
            }
        }
    }

    public enum ReconciliationBuildFailure {
        CAPACITY_REJECTED,
        INVARIANT_REJECTED,
        INTERNAL_RUNTIME_FAILURE
    }

    /** Opaque, player-owned replacement token. It exposes no Ready or mutation plan. */
    public static final class PreparedOnlineReconciliation {
        private PlayerSkillAttachmentService owner;
        private PlayerSkillAttachmentReconciliationCapability<?, ?> capabilityIdentity;
        private OnlineReconciliationHandle handle;
        private PlayerSkillAttachmentReady original;
        private PlayerSkillAttachmentReady replacement;
        private P4E2QualificationFacade.PlayerView qualificationPlayerView;
        private boolean consumed;

        private PreparedOnlineReconciliation(
                PlayerSkillAttachmentService owner,
                PlayerSkillAttachmentReconciliationCapability<?, ?> capabilityIdentity,
                OnlineReconciliationHandle handle,
                PlayerSkillAttachmentReady original,
                PlayerSkillAttachmentReady replacement,
                P4E2QualificationFacade.PlayerView qualificationPlayerView) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.capabilityIdentity = Objects.requireNonNull(
                    capabilityIdentity, "capabilityIdentity");
            this.handle = Objects.requireNonNull(handle, "handle");
            this.original = Objects.requireNonNull(original, "original");
            this.replacement = Objects.requireNonNull(replacement, "replacement");
            this.qualificationPlayerView = qualificationPlayerView;
            if (handle.stateIdentity != original || replacement == original) {
                throw new IllegalArgumentException(
                        "prepared reconciliation identity mismatch");
            }
        }

        private void consumeAndClear() {
            consumed = true;
            owner = null;
            capabilityIdentity = null;
            handle = null;
            original = null;
            replacement = null;
            qualificationPlayerView = null;
        }
    }

    public enum ReconciliationCurrentness {
        CURRENT,
        STATE_CHANGED
    }

    public enum ReconciliationPublication {
        APPLIED,
        STATE_CHANGED
    }

    public sealed interface RootAuditAdmissionResult
            permits RootAuditAdmitted, RootAuditRejected, RootAuditOversize {
    }

    /** Single-use service-owned view of admitted immutable root backings. */
    public static final class RootAuditAdmitted implements RootAuditAdmissionResult {
        private final PlayerSkillAttachmentService owner;
        private List<PlayerLatestState> latest;
        private List<EquippedSkillReference> equipped;
        private final int rootCount;
        private boolean consumed;

        private RootAuditAdmitted(
                PlayerSkillAttachmentService owner,
                List<PlayerLatestState> latest,
                List<EquippedSkillReference> equipped) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.latest = Objects.requireNonNull(latest, "latest");
            this.equipped = Objects.requireNonNull(equipped, "equipped");
            var count = equipped.size();
            for (var state : latest) {
                if (state.pointer().isPresent()) {
                    count = Math.addExact(count, 1);
                }
            }
            var maximum = Math.addExact(
                    MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES,
                    MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES);
            if (count < 0 || count > maximum) {
                throw new IllegalStateException("P4E1_ROOT_PROJECTION_COUNT_INVALID");
            }
            this.rootCount = count;
        }

        private void consumeAndClear() {
            consumed = true;
            latest = null;
            equipped = null;
        }
    }

    /** Bounded expected-data rejection; the raw failure remains package-internal. */
    public static final class RootAuditRejected implements RootAuditAdmissionResult {
        private final PlayerSkillAttachmentFailure failure;

        private RootAuditRejected(PlayerSkillAttachmentFailure failure) {
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        PlayerSkillAttachmentFailure failure() {
            return failure;
        }
    }

    public enum RootAuditOversize implements RootAuditAdmissionResult {
        INSTANCE
    }

    /** Callback-only root projection surface; no backing collection is exposed. */
    public interface RootAuditSink {
        void latest(SkillReference reference);

        void equipped(int slot, SkillReference reference);
    }

    public enum OnlineRootAuditState {
        MISSING,
        READY,
        QUARANTINED
    }

    /** Opaque online root/witness handle; construction and backing access remain service-owned. */
    public static final class OnlineRootAuditHandle {
        private final PlayerSkillAttachmentService owner;
        private final UUID playerId;
        private PlayerSkillAttachmentSourceObservation observation;
        private OnlineHandleStage stage = OnlineHandleStage.NEW;

        private OnlineRootAuditHandle(
                PlayerSkillAttachmentService owner,
                PlayerSkillAttachmentSourceObservation observation) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.observation = Objects.requireNonNull(observation, "observation");
            this.playerId = observation.playerId();
        }
    }

    private enum OnlineHandleStage {
        NEW,
        WITNESS_ONLY,
        DISCARDED
    }

    public sealed interface Result<T> permits Available, Unavailable {
    }

    public record Available<T>(T value) implements Result<T> {
        public Available {
            Objects.requireNonNull(value, "value");
        }
    }

    public record Unavailable<T>(UnavailableReason reason) implements Result<T> {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    public enum UnavailableReason {
        PRESERVED_RAW_QUARANTINE,
        OVERSIZE_QUARANTINE
    }

    public record LatestStateView(
            SkillId skillId,
            Optional<SkillReference> pointer,
            int mutationGeneration) {
        public LatestStateView {
            Objects.requireNonNull(skillId, "skillId");
            pointer = Objects.requireNonNull(pointer, "pointer");
            if (mutationGeneration < 0) {
                throw new IllegalArgumentException("mutationGeneration must be non-negative");
            }
            if (!referenceMatchesRoute(pointer, skillId)) {
                throw new IllegalArgumentException("Latest pointer must match the route");
            }
        }

        private static LatestStateView from(PlayerLatestState state) {
            return new LatestStateView(
                    state.skillId(), state.pointer(), state.mutationGeneration());
        }
    }

    public record EditorStateView(
            Optional<SkillId> selectedDraft, OptionalInt selectedNodeIndex) {
        public EditorStateView {
            selectedDraft = Objects.requireNonNull(selectedDraft, "selectedDraft");
            selectedNodeIndex = Objects.requireNonNull(
                    selectedNodeIndex, "selectedNodeIndex");
            if (selectedNodeIndex.isPresent()
                    && (selectedNodeIndex.getAsInt() < 0
                            || selectedNodeIndex.getAsInt()
                                    >= MagicSafetyCeilings.MAX_NODES)) {
                throw new IllegalArgumentException(
                        "selectedNodeIndex is outside the hard node boundary");
            }
        }

        private static EditorStateView empty() {
            return new EditorStateView(Optional.empty(), OptionalInt.empty());
        }

        private static EditorStateView from(PlayerSkillEditorState state) {
            return new EditorStateView(state.selectedDraft(), state.selectedNodeIndex());
        }

        private PlayerSkillEditorState toInternal() {
            return new PlayerSkillEditorState(selectedDraft, selectedNodeIndex);
        }
    }

    public record PlayerSkillRootProjection(List<SkillReference> references) {
        public PlayerSkillRootProjection {
            references = List.copyOf(Objects.requireNonNull(references, "references"));
        }
    }

    public sealed interface MutationOutcome permits Applied, NoOp, MutationRejected {
    }

    public enum Applied implements MutationOutcome {
        INSTANCE
    }

    public enum NoOp implements MutationOutcome {
        INSTANCE
    }

    public record MutationRejected(MutationRejectionCode code)
            implements MutationOutcome {
        public MutationRejected {
            Objects.requireNonNull(code, "code");
        }
    }

    public enum MutationRejectionCode {
        DRAFT_LIMIT_REACHED,
        DRAFT_PERSISTENCE_REJECTED,
        ATTACHMENT_CAPACITY_REJECTED,
        ATTACHMENT_INVARIANT_REJECTED,
        WRONG_SERVER,
        WRONG_PLAYER,
        STATE_CHANGED
    }

    public sealed interface TransitionPreparation permits Prepared, TransitionRejected {
    }

    public record Prepared(PreparedPlayerSkillTransition transition)
            implements TransitionPreparation {
        public Prepared {
            Objects.requireNonNull(transition, "transition");
        }
    }

    public record TransitionRejected(TransitionRejectionCode code)
            implements TransitionPreparation {
        public TransitionRejected {
            Objects.requireNonNull(code, "code");
        }
    }

    public enum TransitionRejectionCode {
        GENERATION_MISMATCH,
        POINTER_MISMATCH,
        TARGET_ROUTE_MISMATCH,
        GENERATION_EXHAUSTED,
        ATTACHMENT_CAPACITY_REJECTED
    }

    public enum TransitionCurrentness {
        CURRENT,
        STATE_CHANGED
    }

    public static final class PreparedPlayerSkillTransition {
        private final MinecraftServer server;
        private final UUID playerId;
        private final OriginalAttachmentState original;
        private final SkillOwnerId owner;
        private final SkillId skillId;
        private final Optional<SkillReference> expectedPointer;
        private final int expectedGeneration;
        private final Optional<SkillReference> targetPointer;
        private final int targetGeneration;
        private final PlayerSkillAttachmentReady replacement;

        private PreparedPlayerSkillTransition(
                MinecraftServer server,
                UUID playerId,
                OriginalAttachmentState original,
                SkillOwnerId owner,
                SkillId skillId,
                Optional<SkillReference> expectedPointer,
                int expectedGeneration,
                Optional<SkillReference> targetPointer,
                int targetGeneration,
                PlayerSkillAttachmentReady replacement) {
            this.server = Objects.requireNonNull(server, "server");
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.original = Objects.requireNonNull(original, "original");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.skillId = Objects.requireNonNull(skillId, "skillId");
            this.expectedPointer = Objects.requireNonNull(
                    expectedPointer, "expectedPointer");
            this.expectedGeneration = expectedGeneration;
            this.targetPointer = Objects.requireNonNull(targetPointer, "targetPointer");
            this.targetGeneration = targetGeneration;
            this.replacement = replacement;
        }

        public SkillOwnerId owner() {
            return owner;
        }

        public SkillId skillId() {
            return skillId;
        }

        public Optional<SkillReference> expectedPointer() {
            return expectedPointer;
        }

        public int expectedGeneration() {
            return expectedGeneration;
        }

        public Optional<SkillReference> targetPointer() {
            return targetPointer;
        }

        public int targetGeneration() {
            return targetGeneration;
        }

        public boolean isNoOp() {
            return replacement == null;
        }

        /** Returns whether this immutable transition was prepared for the exact server identity. */
        public boolean isBoundTo(MinecraftServer server) {
            return this.server == Objects.requireNonNull(server, "server");
        }

        @Override
        public String toString() {
            return "PreparedPlayerSkillTransition[skillId=" + skillId
                    + ", expectedGeneration=" + expectedGeneration
                    + ", targetGeneration=" + targetGeneration
                    + ", noOp=" + isNoOp() + ']';
        }
    }

    private sealed interface OriginalAttachmentState {
        boolean matches(ObservedPlayerSkillAttachment observed);

        enum Missing implements OriginalAttachmentState {
            INSTANCE;

            @Override
            public boolean matches(ObservedPlayerSkillAttachment observed) {
                return observed instanceof ObservedPlayerSkillAttachment.Missing;
            }
        }

        record Present(PlayerSkillAttachmentReady exactReference)
                implements OriginalAttachmentState {
            public Present {
                Objects.requireNonNull(exactReference, "exactReference");
            }

            @Override
            public boolean matches(ObservedPlayerSkillAttachment observed) {
                return observed instanceof ObservedPlayerSkillAttachment.Ready ready
                        && ready.state() == exactReference;
            }
        }
    }

    private record PreparedTransitionValidation(
            MutationRejectionCode changeCode, UnavailableReason unavailableReason) {
        private PreparedTransitionValidation {
            if (changeCode != null && unavailableReason != null) {
                throw new IllegalArgumentException(
                        "Prepared transition validation cannot be changed and unavailable");
            }
        }

        private static PreparedTransitionValidation current() {
            return new PreparedTransitionValidation(null, null);
        }

        private static PreparedTransitionValidation changed(MutationRejectionCode code) {
            return new PreparedTransitionValidation(Objects.requireNonNull(code, "code"), null);
        }

        private static PreparedTransitionValidation quarantined(UnavailableReason reason) {
            return new PreparedTransitionValidation(
                    null, Objects.requireNonNull(reason, "reason"));
        }
    }
}
