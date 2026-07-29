package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/** Server-thread controlled port for one player's permanent skill Attachment. */
public final class PlayerSkillAttachmentService {
    PlayerSkillAttachmentService() {
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
            case ObservedPlayerSkillAttachment.Ready ready -> {
                var references = new ArrayList<SkillReference>(
                        ready.state().latestStates().size()
                                + ready.state().equipped().size());
                ready.state().latestStates().stream()
                        .flatMap(state -> state.pointer().stream())
                        .forEach(references::add);
                ready.state().equipped().stream()
                        .map(EquippedSkillReference::reference)
                        .forEach(references::add);
                yield new Available<>(new PlayerSkillRootProjection(references));
            }
            case ObservedPlayerSkillAttachment.Quarantined quarantined ->
                    new Unavailable<>(quarantined.reason());
        };
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
        player.setData(PlayerSkillAttachments.type(), replacement);
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
