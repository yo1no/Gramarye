package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict current Draft NBT, including a duplicate-field scan before Compound materialization. */
final class PhysicalSkillDraftNbt {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "draft_schema_version", "skill_id", "nodes", "appearance");
    private static final Set<String> ROOT_WITH_BASE_FIELDS = Set.of(
            "draft_schema_version", "skill_id", "base_revision", "nodes", "appearance");
    private static final Set<String> NODE_FIELDS = Set.of("trigger", "action");
    private static final Set<String> NODE_WITH_OVERRIDE_FIELDS = Set.of(
            "trigger", "action", "appearance_override");
    private static final Set<String> STATE_FIELDS = Set.of("state");
    private static final Set<String> PRESENT_SLOT_FIELDS = Set.of("state", "definition");
    private static final Set<String> DEFINITION_FIELDS = Set.of("type", "schema_version", "payload");
    private static final Set<String> VALUE_STATE_FIELDS = Set.of("state", "value");
    private static final Set<String> RAW_STATE_FIELDS = Set.of("state", "raw");

    private PhysicalSkillDraftNbt() {
    }

    static CompoundTag encode(PhysicalSkillDraft draft) throws DraftFormatException {
        var root = new CompoundTag();
        root.putInt("draft_schema_version", draft.draftSchemaVersion());
        root.put("skill_id", encodeSkillId(draft.skillId()));
        draft.baseRevision().ifPresent(revision -> root.putInt("base_revision", revision.value()));

        var nodes = new ListTag();
        for (var node : draft.nodes()) {
            var encoded = new CompoundTag();
            encoded.put("trigger", encodeTrigger(node.trigger()));
            encoded.put("action", encodeAction(node.action()));
            var appearance = encodeOverride(node.appearanceOverride());
            appearance.ifPresent(tag -> encoded.put("appearance_override", tag));
            nodes.add(encoded);
        }
        root.put("nodes", nodes);
        root.put("appearance", encodeTopAppearance(draft.appearance()));
        return root;
    }

    static PhysicalSkillDraft decode(CompoundTag root) throws DraftFormatException {
        var keys = root.getAllKeys();
        if (!(keys.equals(ROOT_FIELDS) || keys.equals(ROOT_WITH_BASE_FIELDS))
                || !(root.get("draft_schema_version") instanceof IntTag schemaTag)
                || !(root.get("skill_id") instanceof StringTag skillIdTag)
                || !(root.get("nodes") instanceof ListTag nodesTag)
                || !(root.get("appearance") instanceof CompoundTag appearanceTag)
                || schemaTag.getAsInt() < 0
                || nodesTag.size() > MagicSafetyCeilings.MAX_NODES
                || (!nodesTag.isEmpty() && nodesTag.getElementType() != Tag.TAG_COMPOUND)) {
            throw new DraftFormatException();
        }

        Optional<SkillRevision> baseRevision = Optional.empty();
        if (root.contains("base_revision")) {
            if (!(root.get("base_revision") instanceof IntTag baseTag) || baseTag.getAsInt() < 0) {
                throw new DraftFormatException();
            }
            baseRevision = Optional.of(new SkillRevision(baseTag.getAsInt()));
        }

        var skillId = CanonicalDocumentCodecs.PERSISTED_SKILL_ID.parse(NbtOps.INSTANCE, skillIdTag);
        if (skillId.error().isPresent() || skillId.result().isEmpty()) {
            throw new DraftFormatException();
        }
        var nodes = new ArrayList<PhysicalDraftNode>(nodesTag.size());
        for (var index = 0; index < nodesTag.size(); index++) {
            if (!(nodesTag.get(index) instanceof CompoundTag nodeTag)) {
                throw new DraftFormatException();
            }
            nodes.add(decodeNode(nodeTag, index));
        }
        return new PhysicalSkillDraft(
                schemaTag.getAsInt(),
                skillId.result().orElseThrow(),
                baseRevision,
                nodes,
                decodeTopAppearance(appearanceTag));
    }

    static CompoundTag decodeEncoded(ImmutableEncodedBytes bytes)
            throws IOException, DraftFormatException {
        scanForDuplicateFields(bytes.copyBytes());
        var decoded = StrictNbtTreeCodec.decode(
                bytes, SkillDraftPersistenceFacade.EncodedSkillDraft.maximumEncodedBytes());
        if (!(decoded instanceof CompoundTag compound)) {
            throw new DraftFormatException();
        }
        return compound;
    }

    private static PhysicalDraftNode decodeNode(CompoundTag node, int nodeIndex)
            throws DraftFormatException {
        if (!(node.getAllKeys().equals(NODE_FIELDS)
                        || node.getAllKeys().equals(NODE_WITH_OVERRIDE_FIELDS))
                || !(node.get("trigger") instanceof CompoundTag triggerTag)
                || !(node.get("action") instanceof CompoundTag actionTag)) {
            throw new DraftFormatException();
        }
        PhysicalAppearanceOverride appearance = PhysicalAppearanceOverride.None.INSTANCE;
        if (node.contains("appearance_override")) {
            if (!(node.get("appearance_override") instanceof CompoundTag appearanceTag)) {
                throw new DraftFormatException();
            }
            appearance = decodeOverride(appearanceTag, nodeIndex);
        }
        return new PhysicalDraftNode(
                decodeTrigger(triggerTag, nodeIndex),
                decodeAction(actionTag, nodeIndex),
                appearance);
    }

    private static CompoundTag encodeTrigger(PhysicalDraftTriggerSlot slot)
            throws DraftFormatException {
        if (slot instanceof PhysicalDraftTriggerSlot.Missing) {
            return missingSlot();
        }
        return presentSlot(((PhysicalDraftTriggerSlot.Present) slot).definition());
    }

    private static CompoundTag encodeAction(PhysicalDraftActionSlot slot)
            throws DraftFormatException {
        if (slot instanceof PhysicalDraftActionSlot.Missing) {
            return missingSlot();
        }
        return presentSlot(((PhysicalDraftActionSlot.Present) slot).definition());
    }

    private static CompoundTag missingSlot() {
        var slot = new CompoundTag();
        slot.putString("state", "missing");
        return slot;
    }

    private static CompoundTag presentSlot(PhysicalDefinitionEnvelope definition)
            throws DraftFormatException {
        var slot = new CompoundTag();
        slot.putString("state", "present");
        slot.put("definition", encodeDefinition(definition));
        return slot;
    }

    private static PhysicalDraftTriggerSlot decodeTrigger(CompoundTag slot, int nodeIndex)
            throws DraftFormatException {
        var state = slotState(slot);
        if (state.equals("missing")) {
            requireMissingSlot(slot);
            return PhysicalDraftTriggerSlot.Missing.INSTANCE;
        }
        if (!state.equals("present")
                || !slot.getAllKeys().equals(PRESENT_SLOT_FIELDS)
                || !(slot.get("definition") instanceof CompoundTag definition)) {
            throw new DraftFormatException();
        }
        return new PhysicalDraftTriggerSlot.Present(decodeDefinition(
                definition,
                new SkillDocumentPersistenceLocation.TriggerPayload(nodeIndex)));
    }

    private static PhysicalDraftActionSlot decodeAction(CompoundTag slot, int nodeIndex)
            throws DraftFormatException {
        var state = slotState(slot);
        if (state.equals("missing")) {
            requireMissingSlot(slot);
            return PhysicalDraftActionSlot.Missing.INSTANCE;
        }
        if (!state.equals("present")
                || !slot.getAllKeys().equals(PRESENT_SLOT_FIELDS)
                || !(slot.get("definition") instanceof CompoundTag definition)) {
            throw new DraftFormatException();
        }
        return new PhysicalDraftActionSlot.Present(decodeDefinition(
                definition,
                new SkillDocumentPersistenceLocation.ActionPayload(nodeIndex)));
    }

    private static String slotState(CompoundTag slot) throws DraftFormatException {
        if (!(slot.get("state") instanceof StringTag state)) {
            throw new DraftFormatException();
        }
        return state.getAsString();
    }

    private static void requireMissingSlot(CompoundTag slot) throws DraftFormatException {
        if (!slot.getAllKeys().equals(STATE_FIELDS)) {
            throw new DraftFormatException();
        }
    }

    private static CompoundTag encodeDefinition(PhysicalDefinitionEnvelope definition) {
        var encoded = new CompoundTag();
        encoded.putString("type", definition.typeId().toString());
        encoded.putInt("schema_version", definition.schemaVersion());
        encoded.put("payload", definition.payload().encodePhysical());
        return encoded;
    }

    private static PhysicalDefinitionEnvelope decodeDefinition(
            CompoundTag definition,
            SkillDocumentPersistenceLocation location) throws DraftFormatException {
        if (!definition.getAllKeys().equals(DEFINITION_FIELDS)
                || !(definition.get("type") instanceof StringTag typeTag)
                || !(definition.get("schema_version") instanceof IntTag schemaTag)
                || !(definition.get("payload") instanceof CompoundTag payloadTag)
                || schemaTag.getAsInt() < 0) {
            throw new DraftFormatException();
        }
        var type = ResourceLocation.CODEC.parse(NbtOps.INSTANCE, typeTag);
        if (type.error().isPresent() || type.result().isEmpty()) {
            throw new DraftFormatException();
        }
        var raw = RawTreeEnvelope.decodePhysical(payloadTag, location);
        if (raw.failureValue().isPresent()) {
            throw new DraftFormatException();
        }
        return new PhysicalDefinitionEnvelope(
                type.result().orElseThrow(),
                schemaTag.getAsInt(),
                raw.successValue().orElseThrow());
    }

    private static CompoundTag encodeTopAppearance(PhysicalTopAppearance appearance)
            throws DraftFormatException {
        var encoded = new CompoundTag();
        encoded.putString("state", appearance.stateName());
        if (appearance instanceof PhysicalTopAppearance.Decoded decoded) {
            var value = AppearanceStorageCodec.encodeCanonical(
                    new AppearanceDocument.Decoded(decoded.definition()), NbtOps.INSTANCE);
            if (value.error().isPresent()
                    || !(value.result().orElse(null) instanceof CompoundTag tag)) {
                throw new DraftFormatException();
            }
            encoded.put("value", tag);
        } else if (appearance instanceof PhysicalTopAppearance.Unparsed unparsed) {
            encoded.put("raw", unparsed.raw().encodePhysical());
        }
        return encoded;
    }

    private static PhysicalTopAppearance decodeTopAppearance(CompoundTag appearance)
            throws DraftFormatException {
        if (!(appearance.get("state") instanceof StringTag state)) {
            throw new DraftFormatException();
        }
        return switch (state.getAsString()) {
            case "default" -> {
                if (!appearance.getAllKeys().equals(STATE_FIELDS)) {
                    throw new DraftFormatException();
                }
                yield PhysicalTopAppearance.Default.INSTANCE;
            }
            case "decoded" -> {
                if (!appearance.getAllKeys().equals(VALUE_STATE_FIELDS)
                        || !(appearance.get("value") instanceof CompoundTag value)) {
                    throw new DraftFormatException();
                }
                var decoded = AppearanceStorageCodec.parseStrictTop(
                        new Dynamic<>(NbtOps.INSTANCE, value));
                if (decoded.error().isPresent()
                        || !(decoded.result().orElse(null) instanceof AppearanceDocument.Decoded typed)) {
                    throw new DraftFormatException();
                }
                yield new PhysicalTopAppearance.Decoded(typed.definition());
            }
            case "unparsed" -> {
                if (!appearance.getAllKeys().equals(RAW_STATE_FIELDS)
                        || !(appearance.get("raw") instanceof CompoundTag raw)) {
                    throw new DraftFormatException();
                }
                var decoded = RawTreeEnvelope.decodePhysical(
                        raw, SkillDocumentPersistenceLocation.TopAppearance.INSTANCE);
                if (decoded.failureValue().isPresent()) {
                    throw new DraftFormatException();
                }
                yield new PhysicalTopAppearance.Unparsed(decoded.successValue().orElseThrow());
            }
            default -> throw new DraftFormatException();
        };
    }

    private static Optional<CompoundTag> encodeOverride(PhysicalAppearanceOverride appearance)
            throws DraftFormatException {
        if (appearance instanceof PhysicalAppearanceOverride.None) {
            return Optional.empty();
        }
        var encoded = new CompoundTag();
        encoded.putString("state", appearance.stateName());
        if (appearance instanceof PhysicalAppearanceOverride.Decoded decoded) {
            var value = AppearanceStorageCodec.encodeCanonical(
                    new AppearanceOverrideDocument.Decoded(decoded.override()), NbtOps.INSTANCE);
            if (value.error().isPresent()
                    || !(value.result().orElse(null) instanceof CompoundTag tag)) {
                throw new DraftFormatException();
            }
            encoded.put("value", tag);
        } else {
            encoded.put("raw", ((PhysicalAppearanceOverride.Unparsed) appearance).raw().encodePhysical());
        }
        return Optional.of(encoded);
    }

    private static PhysicalAppearanceOverride decodeOverride(CompoundTag appearance, int nodeIndex)
            throws DraftFormatException {
        if (!(appearance.get("state") instanceof StringTag state)) {
            throw new DraftFormatException();
        }
        return switch (state.getAsString()) {
            case "decoded" -> {
                if (!appearance.getAllKeys().equals(VALUE_STATE_FIELDS)
                        || !(appearance.get("value") instanceof CompoundTag value)) {
                    throw new DraftFormatException();
                }
                var decoded = AppearanceStorageCodec.parseStrictOverride(
                        new Dynamic<>(NbtOps.INSTANCE, value));
                if (decoded.error().isPresent()
                        || !(decoded.result().orElse(null) instanceof AppearanceOverrideDocument.Decoded typed)) {
                    throw new DraftFormatException();
                }
                yield new PhysicalAppearanceOverride.Decoded(typed.override());
            }
            case "unparsed" -> {
                if (!appearance.getAllKeys().equals(RAW_STATE_FIELDS)
                        || !(appearance.get("raw") instanceof CompoundTag raw)) {
                    throw new DraftFormatException();
                }
                var decoded = RawTreeEnvelope.decodePhysical(
                        raw, new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex));
                if (decoded.failureValue().isPresent()) {
                    throw new DraftFormatException();
                }
                yield new PhysicalAppearanceOverride.Unparsed(decoded.successValue().orElseThrow());
            }
            default -> throw new DraftFormatException();
        };
    }

    private static StringTag encodeSkillId(com.yo1no.gramarye.magic.api.id.SkillId skillId)
            throws DraftFormatException {
        var encoded = CanonicalDocumentCodecs.PERSISTED_SKILL_ID.encodeStart(NbtOps.INSTANCE, skillId);
        if (encoded.error().isPresent()
                || !(encoded.result().orElse(null) instanceof StringTag value)) {
            throw new DraftFormatException();
        }
        return value;
    }

    private static void scanForDuplicateFields(byte[] bytes) throws DraftFormatException {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var scanner = new DuplicateFieldScanner(input);
            scanner.scanRoot();
            if (input.read() != -1) {
                throw new DraftFormatException();
            }
        } catch (DraftFormatException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new DraftFormatException();
        }
    }

    private static final class DuplicateFieldScanner {
        private final DataInputStream input;
        private int nodes;

        private DuplicateFieldScanner(DataInputStream input) {
            this.input = input;
        }

        private void scanRoot() throws IOException, DraftFormatException {
            var type = input.readUnsignedByte();
            if (type != Tag.TAG_COMPOUND) {
                throw new DraftFormatException();
            }
            scanPayload(type, 1);
        }

        private void scanPayload(int type, int depth) throws IOException, DraftFormatException {
            if (depth > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH
                    || ++nodes > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES) {
                throw new DraftFormatException();
            }
            switch (type) {
                case Tag.TAG_END -> {
                }
                case Tag.TAG_BYTE -> input.readByte();
                case Tag.TAG_SHORT -> input.readShort();
                case Tag.TAG_INT, Tag.TAG_FLOAT -> input.readInt();
                case Tag.TAG_LONG, Tag.TAG_DOUBLE -> input.readLong();
                case Tag.TAG_BYTE_ARRAY -> skipArray(1);
                case Tag.TAG_STRING -> input.readUTF();
                case Tag.TAG_LIST -> scanList(depth);
                case Tag.TAG_COMPOUND -> scanCompound(depth);
                case Tag.TAG_INT_ARRAY -> skipArray(Integer.BYTES);
                case Tag.TAG_LONG_ARRAY -> skipArray(Long.BYTES);
                default -> throw new DraftFormatException();
            }
        }

        private void scanCompound(int depth) throws IOException, DraftFormatException {
            var names = new HashSet<String>();
            while (true) {
                var type = input.readUnsignedByte();
                if (type == Tag.TAG_END) {
                    return;
                }
                var name = input.readUTF();
                if (!names.add(name)) {
                    throw new DraftFormatException();
                }
                scanPayload(type, depth + 1);
            }
        }

        private void scanList(int depth) throws IOException, DraftFormatException {
            var elementType = input.readUnsignedByte();
            var length = input.readInt();
            if (length < 0 || (elementType == Tag.TAG_END && length != 0)) {
                throw new DraftFormatException();
            }
            for (var index = 0; index < length; index++) {
                scanPayload(elementType, depth + 1);
            }
        }

        private void skipArray(int width) throws IOException, DraftFormatException {
            var length = input.readInt();
            if (length < 0) {
                throw new DraftFormatException();
            }
            final long byteCount;
            try {
                byteCount = Math.multiplyExact((long) length, width);
            } catch (ArithmeticException exception) {
                throw new DraftFormatException();
            }
            try {
                input.skipNBytes(byteCount);
            } catch (EOFException exception) {
                throw new DraftFormatException();
            }
        }
    }

    static final class DraftFormatException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
