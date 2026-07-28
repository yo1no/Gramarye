package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Complete, no-partial admission and canonical carrier construction for current P4-C1 state. */
final class PlayerSkillAttachmentPersistenceBridge {
    private static final Comparator<SkillId> SKILL_ID_ORDER =
            Comparator.comparing(SkillId::value);

    private final PlayerSkillAttachmentMigrator migrator;

    PlayerSkillAttachmentPersistenceBridge() {
        this(new PlayerSkillAttachmentMigrator(PlayerSkillAttachmentMigrationPlans.production()));
    }

    PlayerSkillAttachmentPersistenceBridge(PlayerSkillAttachmentMigrator migrator) {
        this.migrator = Objects.requireNonNull(migrator, "migrator");
    }

    Result load(CompoundTag input, Optional<HolderLookup.Provider> provider) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(provider, "provider");
        var migrated = migrator.migrate(input);
        if (migrated instanceof PlayerSkillAttachmentMigrationResult.Rejected rejected) {
            return new Rejected(mapMigrationFailure(rejected.failure()));
        }
        try {
            return new Loaded(decodeCurrent(
                    (PlayerSkillAttachmentMigrationResult.Migrated) migrated, provider));
        } catch (DataFailure failure) {
            return new Rejected(failure.failure);
        }
    }

    static PlayerSkillAttachmentReady freshEmptyReady() {
        var rebuilt = rebuildReady(
                List.of(), List.of(), List.of(), PlayerSkillEditorState.empty());
        if (rebuilt instanceof PlayerSkillAttachmentBuildResult.Built built) {
            return built.ready();
        }
        throw new IllegalStateException("Canonical empty player skill Attachment was rejected");
    }

    static boolean draftCountWithinLimit(int count) {
        return count >= 0 && count <= MagicSafetyCeilings.MAX_PLAYER_DRAFTS;
    }

    static boolean canAddDraftRoute(int currentCount) {
        return currentCount >= 0 && currentCount < MagicSafetyCeilings.MAX_PLAYER_DRAFTS;
    }

    static boolean equippedSlotWithinLimit(int slot) {
        return slot >= 0 && slot < MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES;
    }

    private PlayerSkillAttachmentReady decodeCurrent(
            PlayerSkillAttachmentMigrationResult.Migrated migrated,
            Optional<HolderLookup.Provider> provider) {
        var outer = migrated.tokenizedCurrentOuter();
        require(outer.getAllKeys().equals(PlayerSkillAttachmentSchema.OUTER_FIELDS),
                malformed(PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA));
        require(outer.get(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION) instanceof IntTag version
                        && version.getAsInt() == PlayerSkillAttachmentSchema.CURRENT_VERSION,
                PlayerSkillAttachmentFailure.simple(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_SCHEMA_UNSUPPORTED,
                        PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA));
        var draftsTag = requireList(outer, PlayerSkillAttachmentSchema.DRAFTS);
        var latestTag = requireList(outer, PlayerSkillAttachmentSchema.LATEST_STATES);
        var equippedTag = requireList(outer, PlayerSkillAttachmentSchema.EQUIPPED_SLOTS);
        require(outer.get(PlayerSkillAttachmentSchema.EDITOR) instanceof CompoundTag,
                malformed(PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA));

        require(latestTag.size() <= MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES,
                PlayerSkillAttachmentFailure.capacity(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                        PlayerSkillAttachmentFailure.Stage.LATEST_COUNT,
                        latestTag.size(),
                        MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES));
        require(equippedTag.size() <= MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES,
                PlayerSkillAttachmentFailure.capacity(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                        PlayerSkillAttachmentFailure.Stage.EQUIPPED_COUNT,
                        equippedTag.size(),
                        MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES));

        requireCompoundElements(draftsTag, PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION);
        requireCompoundElements(latestTag, PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION);
        requireCompoundElements(equippedTag, PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION);

        var drafts = decodeDrafts(draftsTag, migrated.draftTokens(), provider);
        var latest = decodeLatest(latestTag);
        var equipped = decodeEquipped(equippedTag);
        var editor = decodeEditor((CompoundTag) outer.get(PlayerSkillAttachmentSchema.EDITOR));
        return built(rebuildReady(drafts, latest, equipped, editor));
    }

    private List<PlayerDraftEntry> decodeDrafts(
            ListTag input,
            List<PlayerSkillAttachmentMigrationResult.OpaqueDraftToken> tokens,
            Optional<HolderLookup.Provider> provider) {
        require(input.size() == tokens.size(), PlayerSkillAttachmentFailure.simple(
                PlayerSkillAttachmentFailure.Code.OPAQUE_DRAFT_TOKEN_INVARIANT_VIOLATION,
                PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION));
        var result = new ArrayList<PlayerDraftEntry>(input.size());
        var routes = new HashSet<SkillId>();
        for (var index = 0; index < input.size(); index++) {
            var entry = (CompoundTag) input.get(index);
            require(entry.getAllKeys().equals(PlayerSkillAttachmentSchema.DRAFT_FIELDS),
                    malformed(PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION));
            var route = PlayerSkillAttachmentCodecs.decodeRoute(entry.get(PlayerSkillAttachmentSchema.SKILL_ID))
                    .orElseThrow(() -> data(malformed(PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION)));
            if (!routes.add(route)) {
                throw data(PlayerSkillAttachmentFailure.route(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_DRAFT_ROUTE,
                        PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION,
                        route));
            }
            var encodingTag = entry.get(PlayerSkillAttachmentSchema.DRAFT_ENCODING);
            require(encodingTag instanceof StringTag,
                    malformed(PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION));
            var encoding = (StringTag) encodingTag;
            require(entry.get(PlayerSkillAttachmentSchema.DRAFT_BYTES) instanceof StringTag sentinel
                            && sentinel.getAsString().equals(PlayerSkillAttachmentSchema.TOKEN_PREFIX + index),
                    PlayerSkillAttachmentFailure.simple(
                            PlayerSkillAttachmentFailure.Code.OPAQUE_DRAFT_TOKEN_INVARIANT_VIOLATION,
                            PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION));
            var token = tokens.get(index);
            require(token.id() == index
                            && token.draftIndex() == index
                            && token.draftEncoding().equals(encoding.getAsString()),
                    PlayerSkillAttachmentFailure.simple(
                            PlayerSkillAttachmentFailure.Code.OPAQUE_DRAFT_TOKEN_INVARIANT_VIOLATION,
                            PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION));

            var captured = token.capturePersisted();
            if (captured instanceof SkillDraftPersistenceFacade.CaptureRejected rejected) {
                throw data(mapDraftFailure(rejected.failure(), PlayerSkillAttachmentFailure.Stage.DRAFT_CAPTURE));
            }
            var encoded = ((SkillDraftPersistenceFacade.Captured) captured).draft();
            var loaded = SkillDraftPersistenceFacade.loadAlwaysMigrating(encoded, provider);
            if (loaded instanceof SkillDraftPersistenceFacade.LoadRejected rejected) {
                throw data(mapDraftFailure(rejected.failure(), PlayerSkillAttachmentFailure.Stage.DRAFT_LOAD));
            }
            var draft = ((SkillDraftPersistenceFacade.Loaded) loaded).draft();
            if (!route.equals(draft.skillId())) {
                throw data(PlayerSkillAttachmentFailure.route(
                        PlayerSkillAttachmentFailure.Code.DRAFT_ROUTE_MISMATCH,
                        PlayerSkillAttachmentFailure.Stage.DRAFT_VALIDATION,
                        route));
            }
            var canonical = SkillDraftPersistenceFacade.encodeCurrent(draft);
            if (canonical instanceof SkillDraftPersistenceFacade.EncodeRejected rejected) {
                throw data(mapDraftFailure(rejected.failure(), PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            }
            result.add(new PlayerDraftEntry(
                    route, draft, ((SkillDraftPersistenceFacade.Encoded) canonical).draft()));
        }
        return result;
    }

    private static List<PlayerLatestState> decodeLatest(ListTag input) {
        var result = new ArrayList<PlayerLatestState>(input.size());
        var routes = new HashSet<SkillId>();
        for (var index = 0; index < input.size(); index++) {
            var entry = (CompoundTag) input.get(index);
            require(entry.getAllKeys().equals(PlayerSkillAttachmentSchema.LATEST_REQUIRED_FIELDS)
                            || entry.getAllKeys().equals(PlayerSkillAttachmentSchema.LATEST_POINTER_FIELDS),
                    malformed(PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION));
            var route = PlayerSkillAttachmentCodecs.decodeRoute(entry.get(PlayerSkillAttachmentSchema.SKILL_ID))
                    .orElseThrow(() -> data(malformed(PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION)));
            if (!routes.add(route)) {
                throw data(PlayerSkillAttachmentFailure.route(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_LATEST_ROUTE,
                        PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION,
                        route));
            }
            var generationTag = entry.get(PlayerSkillAttachmentSchema.MUTATION_GENERATION);
            require(generationTag instanceof IntTag,
                    malformed(PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION));
            var generation = (IntTag) generationTag;
            require(generation.getAsInt() >= 0, PlayerSkillAttachmentFailure.at(
                    PlayerSkillAttachmentFailure.Code.GENERATION_INVALID,
                    PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION,
                    index));
            Optional<SkillReference> pointer = Optional.empty();
            if (entry.contains(PlayerSkillAttachmentSchema.POINTER)) {
                pointer = Optional.of(PlayerSkillAttachmentCodecs.decodeReference(
                                entry.get(PlayerSkillAttachmentSchema.POINTER))
                        .orElseThrow(() -> data(malformed(
                                PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION))));
                if (!pointer.orElseThrow().skillId().equals(route)) {
                    throw data(PlayerSkillAttachmentFailure.route(
                            PlayerSkillAttachmentFailure.Code.LATEST_POINTER_ROUTE_MISMATCH,
                            PlayerSkillAttachmentFailure.Stage.LATEST_VALIDATION,
                            route));
                }
            }
            result.add(new PlayerLatestState(route, pointer, generation.getAsInt()));
        }
        return result;
    }

    private static List<EquippedSkillReference> decodeEquipped(ListTag input) {
        var result = new ArrayList<EquippedSkillReference>(input.size());
        var slots = new HashSet<Integer>();
        for (var index = 0; index < input.size(); index++) {
            var entry = (CompoundTag) input.get(index);
            require(entry.getAllKeys().equals(PlayerSkillAttachmentSchema.EQUIPPED_FIELDS),
                    malformed(PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION));
            var rawSlot = entry.get(PlayerSkillAttachmentSchema.SLOT);
            require(rawSlot instanceof IntTag,
                    malformed(PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION));
            var slotTag = (IntTag) rawSlot;
            var slot = slotTag.getAsInt();
            require(equippedSlotWithinLimit(slot),
                    PlayerSkillAttachmentFailure.at(
                            PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                            PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION,
                            slot));
            if (!slots.add(slot)) {
                throw data(PlayerSkillAttachmentFailure.at(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_EQUIPPED_SLOT,
                        PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION,
                        slot));
            }
            var reference = PlayerSkillAttachmentCodecs.decodeReference(
                            entry.get(PlayerSkillAttachmentSchema.REFERENCE))
                    .orElseThrow(() -> data(malformed(
                            PlayerSkillAttachmentFailure.Stage.EQUIPPED_VALIDATION)));
            result.add(new EquippedSkillReference(slot, reference));
        }
        return result;
    }

    private static PlayerSkillEditorState decodeEditor(CompoundTag input) {
        require(PlayerSkillAttachmentSchema.EDITOR_FIELDS.containsAll(input.getAllKeys()),
                PlayerSkillAttachmentFailure.simple(
                        PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                        PlayerSkillAttachmentFailure.Stage.EDITOR_VALIDATION));
        Optional<SkillId> selectedDraft = Optional.empty();
        if (input.contains(PlayerSkillAttachmentSchema.SELECTED_DRAFT)) {
            selectedDraft = Optional.of(PlayerSkillAttachmentCodecs.decodeRoute(
                            input.get(PlayerSkillAttachmentSchema.SELECTED_DRAFT))
                    .orElseThrow(() -> data(PlayerSkillAttachmentFailure.simple(
                            PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                            PlayerSkillAttachmentFailure.Stage.EDITOR_VALIDATION))));
        }
        var selectedNode = OptionalInt.empty();
        if (input.contains(PlayerSkillAttachmentSchema.SELECTED_NODE_INDEX)) {
            var rawNode = input.get(PlayerSkillAttachmentSchema.SELECTED_NODE_INDEX);
            require(rawNode instanceof IntTag,
                    PlayerSkillAttachmentFailure.simple(
                            PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                            PlayerSkillAttachmentFailure.Stage.EDITOR_VALIDATION));
            var nodeTag = (IntTag) rawNode;
            var node = nodeTag.getAsInt();
            require(node >= 0 && node < MagicSafetyCeilings.MAX_NODES,
                    PlayerSkillAttachmentFailure.at(
                            PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                            PlayerSkillAttachmentFailure.Stage.EDITOR_VALIDATION,
                            node));
            selectedNode = OptionalInt.of(node);
        }
        return new PlayerSkillEditorState(selectedDraft, selectedNode);
    }

    static PlayerSkillAttachmentBuildResult rebuildReady(
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latest,
            List<EquippedSkillReference> equipped,
            PlayerSkillEditorState editor) {
        try {
            validateReadyInputs(drafts, latest, equipped, editor);
            var sortedDrafts = drafts.stream()
                    .sorted(Comparator.comparing(PlayerDraftEntry::skillId, SKILL_ID_ORDER))
                    .toList();
            var sortedLatest = latest.stream()
                    .sorted(Comparator.comparing(PlayerLatestState::skillId, SKILL_ID_ORDER))
                    .toList();
            var sortedEquipped = equipped.stream()
                    .sorted(Comparator.comparingInt(EquippedSkillReference::slot))
                    .toList();
            var tag = encodeCurrent(sortedDrafts, sortedLatest, sortedEquipped, editor);
            var measured = AttachmentTagSize.measure(tag);
            if (measured instanceof AttachmentTagSizeResult.Exceeded exceeded) {
                return new PlayerSkillAttachmentBuildResult.Rejected(
                        PlayerSkillAttachmentFailure.capacity(
                                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENCODED_CAPACITY_EXCEEDED,
                                PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                                exceeded.observedAtLeast(),
                                exceeded.maximum()));
            }
            var count = ((AttachmentTagSizeResult.WithinLimit) measured).exactByteCount();
            var carrier = EncodedPlayerSkillAttachment.takeOwnership(
                    tag, count, sortedDrafts.size(), sortedLatest.size(), sortedEquipped.size());
            return new PlayerSkillAttachmentBuildResult.Built(
                    new PlayerSkillAttachmentReady(
                            sortedDrafts, sortedLatest, sortedEquipped, editor, carrier));
        } catch (DataFailure failure) {
            return new PlayerSkillAttachmentBuildResult.Rejected(failure.failure);
        } catch (IOException exception) {
            return new PlayerSkillAttachmentBuildResult.Rejected(
                    PlayerSkillAttachmentFailure.simple(
                            PlayerSkillAttachmentFailure.Code.INTERNAL_CODEC_EXCEPTION,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        } catch (RuntimeException exception) {
            return new PlayerSkillAttachmentBuildResult.Rejected(
                    PlayerSkillAttachmentFailure.exception(
                            PlayerSkillAttachmentFailure.Code.INTERNAL_CODEC_EXCEPTION,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                            exception));
        }
    }

    private static void validateReadyInputs(
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latest,
            List<EquippedSkillReference> equipped,
            PlayerSkillEditorState editor) {
        require(drafts != null, malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        require(latest != null, malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        require(equipped != null, malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        require(editor != null, PlayerSkillAttachmentFailure.simple(
                PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        require(draftCountWithinLimit(drafts.size()), PlayerSkillAttachmentFailure.capacity(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                PlayerSkillAttachmentFailure.Stage.DRAFT_COUNT,
                drafts.size(),
                MagicSafetyCeilings.MAX_PLAYER_DRAFTS));
        require(latest.size() <= MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES,
                PlayerSkillAttachmentFailure.capacity(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                        PlayerSkillAttachmentFailure.Stage.LATEST_COUNT,
                        latest.size(),
                        MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES));
        require(equipped.size() <= MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES,
                PlayerSkillAttachmentFailure.capacity(
                        PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                        PlayerSkillAttachmentFailure.Stage.EQUIPPED_COUNT,
                        equipped.size(),
                        MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES));
        validateDraftEntries(drafts);
        validateLatestStates(latest);
        validateEquippedReferences(equipped);
        validateEditor(editor);
    }

    private static void validateDraftEntries(List<PlayerDraftEntry> drafts) {
        var routes = new HashSet<SkillId>();
        for (var index = 0; index < drafts.size(); index++) {
            var entry = drafts.get(index);
            require(entry != null, malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            require(entry.skillId() != null
                            && entry.draft() != null
                            && entry.encodedDraft() != null,
                    malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            if (!routes.add(entry.skillId())) {
                throw data(PlayerSkillAttachmentFailure.route(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_DRAFT_ROUTE,
                        PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                        entry.skillId()));
            }
            require(entry.skillId().equals(entry.draft().skillId()),
                    PlayerSkillAttachmentFailure.route(
                            PlayerSkillAttachmentFailure.Code.DRAFT_ROUTE_MISMATCH,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                            entry.skillId()));
            var encoded = SkillDraftPersistenceFacade.encodeCurrent(entry.draft());
            if (encoded instanceof SkillDraftPersistenceFacade.EncodeRejected rejected) {
                throw data(mapDraftFailure(
                        rejected.failure(), PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            }
            require(entry.encodedDraft().equals(
                            ((SkillDraftPersistenceFacade.Encoded) encoded).draft()),
                    PlayerSkillAttachmentFailure.route(
                            PlayerSkillAttachmentFailure.Code.DRAFT_CARRIER_MISMATCH,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                            entry.skillId()));
        }
    }

    private static void validateLatestStates(List<PlayerLatestState> latest) {
        var routes = new HashSet<SkillId>();
        for (var index = 0; index < latest.size(); index++) {
            var state = latest.get(index);
            require(state != null
                            && state.skillId() != null
                            && state.pointer() != null,
                    malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            if (!routes.add(state.skillId())) {
                throw data(PlayerSkillAttachmentFailure.route(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_LATEST_ROUTE,
                        PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                        state.skillId()));
            }
            require(state.mutationGeneration() >= 0, PlayerSkillAttachmentFailure.at(
                    PlayerSkillAttachmentFailure.Code.GENERATION_INVALID,
                    PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                    index));
            require(state.pointer().isEmpty()
                            || state.pointer().orElseThrow().skillId().equals(state.skillId()),
                    PlayerSkillAttachmentFailure.route(
                            PlayerSkillAttachmentFailure.Code.LATEST_POINTER_ROUTE_MISMATCH,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                            state.skillId()));
        }
    }

    private static void validateEquippedReferences(List<EquippedSkillReference> equipped) {
        var slots = new HashSet<Integer>();
        for (var index = 0; index < equipped.size(); index++) {
            var reference = equipped.get(index);
            require(reference != null && reference.reference() != null,
                    malformed(PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
            require(equippedSlotWithinLimit(reference.slot()),
                    PlayerSkillAttachmentFailure.at(
                            PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                            PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                            reference.slot()));
            if (!slots.add(reference.slot())) {
                throw data(PlayerSkillAttachmentFailure.at(
                        PlayerSkillAttachmentFailure.Code.DUPLICATE_EQUIPPED_SLOT,
                        PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                        reference.slot()));
            }
        }
    }

    private static void validateEditor(PlayerSkillEditorState editor) {
        require(editor.selectedDraft() != null && editor.selectedNodeIndex() != null,
                PlayerSkillAttachmentFailure.simple(
                        PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                        PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD));
        require(editor.selectedNodeIndex().isEmpty()
                        || editor.selectedNodeIndex().getAsInt() >= 0
                                && editor.selectedNodeIndex().getAsInt()
                                        < MagicSafetyCeilings.MAX_NODES,
                PlayerSkillAttachmentFailure.at(
                        PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                        PlayerSkillAttachmentFailure.Stage.CARRIER_BUILD,
                        editor.selectedNodeIndex().orElse(-1)));
    }

    private static CompoundTag encodeCurrent(
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latest,
            List<EquippedSkillReference> equipped,
            PlayerSkillEditorState editor) {
        var outer = new CompoundTag();
        outer.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION,
                PlayerSkillAttachmentSchema.CURRENT_VERSION);
        var draftList = new ListTag();
        for (var draft : drafts) {
            var entry = new CompoundTag();
            entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                    PlayerSkillAttachmentCodecs.encodeRoute(draft.skillId()));
            entry.putString(PlayerSkillAttachmentSchema.DRAFT_ENCODING,
                    draft.encodedDraft().draftEncoding());
            entry.putByteArray(PlayerSkillAttachmentSchema.DRAFT_BYTES,
                    draft.encodedDraft().copyBytes());
            draftList.add(entry);
        }
        outer.put(PlayerSkillAttachmentSchema.DRAFTS, draftList);
        var latestList = new ListTag();
        for (var state : latest) {
            var entry = new CompoundTag();
            entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                    PlayerSkillAttachmentCodecs.encodeRoute(state.skillId()));
            entry.putInt(PlayerSkillAttachmentSchema.MUTATION_GENERATION,
                    state.mutationGeneration());
            state.pointer().ifPresent(reference -> entry.put(
                    PlayerSkillAttachmentSchema.POINTER,
                    PlayerSkillAttachmentCodecs.encodeReference(reference)));
            latestList.add(entry);
        }
        outer.put(PlayerSkillAttachmentSchema.LATEST_STATES, latestList);
        var equippedList = new ListTag();
        for (var equippedReference : equipped) {
            var entry = new CompoundTag();
            entry.putInt(PlayerSkillAttachmentSchema.SLOT, equippedReference.slot());
            entry.put(PlayerSkillAttachmentSchema.REFERENCE,
                    PlayerSkillAttachmentCodecs.encodeReference(equippedReference.reference()));
            equippedList.add(entry);
        }
        outer.put(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS, equippedList);
        var editorTag = new CompoundTag();
        editor.selectedDraft().ifPresent(skillId -> editorTag.put(
                PlayerSkillAttachmentSchema.SELECTED_DRAFT,
                PlayerSkillAttachmentCodecs.encodeRoute(skillId)));
        editor.selectedNodeIndex().ifPresent(node ->
                editorTag.putInt(PlayerSkillAttachmentSchema.SELECTED_NODE_INDEX, node));
        outer.put(PlayerSkillAttachmentSchema.EDITOR, editorTag);
        return outer;
    }

    private static ListTag requireList(CompoundTag outer, String field) {
        require(outer.get(field) instanceof ListTag,
                malformed(PlayerSkillAttachmentFailure.Stage.OUTER_SCHEMA));
        return (ListTag) outer.get(field);
    }

    private static void requireCompoundElements(
            ListTag list, PlayerSkillAttachmentFailure.Stage stage) {
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, malformed(stage));
        for (var index = 0; index < list.size(); index++) {
            require(list.get(index) instanceof CompoundTag, malformed(stage));
        }
    }

    private static PlayerSkillAttachmentFailure mapMigrationFailure(
            PlayerSkillAttachmentMigrationFailure failure) {
        return switch (failure.code()) {
            case SCHEMA_UNSUPPORTED, MISSING_MIGRATION_EDGE -> PlayerSkillAttachmentFailure.simple(
                    PlayerSkillAttachmentFailure.Code.ATTACHMENT_SCHEMA_UNSUPPORTED,
                    PlayerSkillAttachmentFailure.Stage.OUTER_MIGRATION);
            case OPAQUE_TOKEN_INVARIANT_VIOLATION -> PlayerSkillAttachmentFailure.simple(
                    PlayerSkillAttachmentFailure.Code.OPAQUE_DRAFT_TOKEN_INVARIANT_VIOLATION,
                    PlayerSkillAttachmentFailure.Stage.OUTER_MIGRATION);
            case STEP_FAILED, PARTIAL_MIGRATION -> new PlayerSkillAttachmentFailure(
                    PlayerSkillAttachmentFailure.Code.ATTACHMENT_MIGRATION_FAILED,
                    PlayerSkillAttachmentFailure.Stage.OUTER_MIGRATION,
                    -1,
                    -1,
                    Optional.empty(),
                    failure.schemaVersion(),
                    failure.exceptionClass());
            case ENVELOPE_MALFORMED -> malformed(PlayerSkillAttachmentFailure.Stage.OUTER_MIGRATION);
        };
    }

    private static PlayerSkillAttachmentFailure mapDraftFailure(
            SkillDraftPersistenceFacade.Failure failure,
            PlayerSkillAttachmentFailure.Stage stage) {
        var code = switch (failure.code()) {
            case DRAFT_ENTRY_CAPACITY_EXCEEDED ->
                    PlayerSkillAttachmentFailure.Code.DRAFT_ENTRY_CAPACITY_EXCEEDED;
            case DRAFT_PHYSICAL_MIGRATION_FAILED ->
                    PlayerSkillAttachmentFailure.Code.DRAFT_PHYSICAL_MIGRATION_FAILED;
            case DRAFT_LOGICAL_MIGRATION_FAILED ->
                    PlayerSkillAttachmentFailure.Code.DRAFT_LOGICAL_MIGRATION_FAILED;
            case DRAFT_ROUTE_MISMATCH -> PlayerSkillAttachmentFailure.Code.DRAFT_ROUTE_MISMATCH;
            case OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION ->
                    PlayerSkillAttachmentFailure.Code.OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION;
            case INTERNAL_CODEC_EXCEPTION -> PlayerSkillAttachmentFailure.Code.INTERNAL_CODEC_EXCEPTION;
            default -> PlayerSkillAttachmentFailure.Code.DRAFT_DECODE_FAILED;
        };
        if (failure instanceof SkillDraftPersistenceFacade.CapacityFailure capacity) {
            return PlayerSkillAttachmentFailure.capacity(
                    code, stage, capacity.observedAtLeast(), capacity.maximum());
        }
        if (failure instanceof SkillDraftPersistenceFacade.CodecFailure codec) {
            return new PlayerSkillAttachmentFailure(
                    code, stage, -1, -1, Optional.empty(), -1, codec.exceptionClassName());
        }
        return PlayerSkillAttachmentFailure.simple(code, stage);
    }

    private static PlayerSkillAttachmentFailure malformed(PlayerSkillAttachmentFailure.Stage stage) {
        return PlayerSkillAttachmentFailure.simple(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED, stage);
    }

    private static void require(boolean condition, PlayerSkillAttachmentFailure failure) {
        if (!condition) {
            throw data(failure);
        }
    }

    private static DataFailure data(PlayerSkillAttachmentFailure failure) {
        return new DataFailure(failure);
    }

    private static PlayerSkillAttachmentReady built(PlayerSkillAttachmentBuildResult result) {
        if (result instanceof PlayerSkillAttachmentBuildResult.Built built) {
            return built.ready();
        }
        throw data(((PlayerSkillAttachmentBuildResult.Rejected) result).failure());
    }

    sealed interface Result permits Loaded, Rejected {
    }

    record Loaded(PlayerSkillAttachmentReady ready) implements Result {
        Loaded {
            Objects.requireNonNull(ready, "ready");
        }
    }

    record Rejected(PlayerSkillAttachmentFailure failure) implements Result {
        Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    private static final class DataFailure extends RuntimeException {
        private final PlayerSkillAttachmentFailure failure;

        private DataFailure(PlayerSkillAttachmentFailure failure) {
            super((String) null);
            this.failure = Objects.requireNonNull(failure, "failure");
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
