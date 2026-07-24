package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Exact current-schema NBT mapping for the package-internal physical document DTO. */
final class PhysicalSkillDocumentNbt {
    private static final Set<String> DOCUMENT_FIELDS = Set.of(
            "schema_version", "skill_id", "revision", "nodes", "appearance");
    private static final Set<String> NODE_FIELDS = Set.of("trigger", "action");
    private static final Set<String> NODE_WITH_OVERRIDE_FIELDS = Set.of(
            "trigger", "action", "appearance_override");
    private static final Set<String> DEFINITION_FIELDS = Set.of("type", "schema_version", "payload");
    private static final Set<String> STATE_FIELDS = Set.of("state");
    private static final Set<String> VALUE_STATE_FIELDS = Set.of("state", "value");
    private static final Set<String> RAW_STATE_FIELDS = Set.of("state", "raw");

    private PhysicalSkillDocumentNbt() {
    }

    static SkillDocumentPersistenceResult<CompoundTag> encode(PhysicalSkillDocument document) {
        try {
            var root = new CompoundTag();
            root.putInt("schema_version", document.schemaVersion());
            var skillId = CanonicalDocumentCodecs.PERSISTED_SKILL_ID
                    .encodeStart(NbtOps.INSTANCE, document.skillId());
            if (skillId.error().isPresent() || !(skillId.result().orElse(null) instanceof StringTag encodedId)) {
                return failure(new SkillDocumentPersistenceFailure.EncodeFailed(rootLocation()));
            }
            root.put("skill_id", encodedId);
            root.putInt("revision", document.revision().value());

            var nodes = new ListTag();
            for (var index = 0; index < document.nodes().size(); index++) {
                var encodedNode = encodeNode(document.nodes().get(index), index);
                if (encodedNode.failureValue().isPresent()) {
                    return failure(encodedNode.failureValue().orElseThrow());
                }
                nodes.add(encodedNode.successValue().orElseThrow());
            }
            root.put("nodes", nodes);

            var appearance = encodeTopAppearance(document.appearance());
            if (appearance.failureValue().isPresent()) {
                return failure(appearance.failureValue().orElseThrow());
            }
            root.put("appearance", appearance.successValue().orElseThrow());
            return SkillDocumentPersistenceResult.success(root);
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    rootLocation(), exception));
        }
    }

    static SkillDocumentPersistenceResult<PhysicalSkillDocument> decode(CompoundTag root) {
        try {
            if (!hasExactFields(root, DOCUMENT_FIELDS)
                    || !(root.get("schema_version") instanceof IntTag schemaTag)
                    || !(root.get("skill_id") instanceof StringTag skillIdTag)
                    || !(root.get("revision") instanceof IntTag revisionTag)
                    || !(root.get("nodes") instanceof ListTag nodesTag)
                    || !(root.get("appearance") instanceof CompoundTag appearanceTag)) {
                return malformed(rootLocation());
            }
            if (schemaTag.getAsInt() < 0 || revisionTag.getAsInt() < 0
                    || nodesTag.isEmpty()
                    || nodesTag.size() > MagicSafetyCeilings.MAX_NODES) {
                return malformed(rootLocation());
            }
            var skillId = CanonicalDocumentCodecs.PERSISTED_SKILL_ID.parse(NbtOps.INSTANCE, skillIdTag);
            if (skillId.error().isPresent() || skillId.result().isEmpty()) {
                return malformed(rootLocation());
            }

            var nodes = new ArrayList<PhysicalNodeDocument>(nodesTag.size());
            for (var index = 0; index < nodesTag.size(); index++) {
                if (!(nodesTag.get(index) instanceof CompoundTag nodeTag)) {
                    return malformed(rootLocation());
                }
                var node = decodeNode(nodeTag, index);
                if (node.failureValue().isPresent()) {
                    return failure(node.failureValue().orElseThrow());
                }
                nodes.add(node.successValue().orElseThrow());
            }

            var appearance = decodeTopAppearance(appearanceTag);
            if (appearance.failureValue().isPresent()) {
                return failure(appearance.failureValue().orElseThrow());
            }
            return SkillDocumentPersistenceResult.success(new PhysicalSkillDocument(
                    schemaTag.getAsInt(),
                    skillId.result().orElseThrow(),
                    new SkillRevision(revisionTag.getAsInt()),
                    nodes,
                    appearance.successValue().orElseThrow()));
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    rootLocation(), exception));
        }
    }

    private static SkillDocumentPersistenceResult<CompoundTag> encodeNode(
            PhysicalNodeDocument node,
            int nodeIndex) {
        var trigger = encodeDefinition(node.trigger());
        if (trigger.failureValue().isPresent()) {
            return failure(trigger.failureValue().orElseThrow());
        }
        var action = encodeDefinition(node.action());
        if (action.failureValue().isPresent()) {
            return failure(action.failureValue().orElseThrow());
        }
        var encoded = new CompoundTag();
        encoded.put("trigger", trigger.successValue().orElseThrow());
        encoded.put("action", action.successValue().orElseThrow());
        var override = encodeOverride(node.appearanceOverride(), nodeIndex);
        if (override.failureValue().isPresent()) {
            return failure(override.failureValue().orElseThrow());
        }
        override.successValue().orElseThrow().ifPresent(value -> encoded.put("appearance_override", value));
        return SkillDocumentPersistenceResult.success(encoded);
    }

    private static SkillDocumentPersistenceResult<PhysicalNodeDocument> decodeNode(
            CompoundTag node,
            int nodeIndex) {
        if (!(hasExactFields(node, NODE_FIELDS) || hasExactFields(node, NODE_WITH_OVERRIDE_FIELDS))
                || !(node.get("trigger") instanceof CompoundTag triggerTag)
                || !(node.get("action") instanceof CompoundTag actionTag)) {
            return malformed(rootLocation());
        }
        var triggerLocation = new SkillDocumentPersistenceLocation.TriggerPayload(nodeIndex);
        var actionLocation = new SkillDocumentPersistenceLocation.ActionPayload(nodeIndex);
        var trigger = decodeDefinition(triggerTag, triggerLocation);
        if (trigger.failureValue().isPresent()) {
            return failure(trigger.failureValue().orElseThrow());
        }
        var action = decodeDefinition(actionTag, actionLocation);
        if (action.failureValue().isPresent()) {
            return failure(action.failureValue().orElseThrow());
        }

        PhysicalAppearanceOverride appearanceOverride = PhysicalAppearanceOverride.None.INSTANCE;
        if (node.contains("appearance_override")) {
            if (!(node.get("appearance_override") instanceof CompoundTag overrideTag)) {
                return malformed(new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex));
            }
            var decoded = decodeOverride(overrideTag, nodeIndex);
            if (decoded.failureValue().isPresent()) {
                return failure(decoded.failureValue().orElseThrow());
            }
            appearanceOverride = decoded.successValue().orElseThrow();
        }
        return SkillDocumentPersistenceResult.success(new PhysicalNodeDocument(
                trigger.successValue().orElseThrow(),
                action.successValue().orElseThrow(),
                appearanceOverride));
    }

    private static SkillDocumentPersistenceResult<CompoundTag> encodeDefinition(
            PhysicalDefinitionEnvelope definition) {
        var encoded = new CompoundTag();
        encoded.putString("type", definition.typeId().toString());
        encoded.putInt("schema_version", definition.schemaVersion());
        encoded.put("payload", definition.payload().encodePhysical());
        return SkillDocumentPersistenceResult.success(encoded);
    }

    private static SkillDocumentPersistenceResult<PhysicalDefinitionEnvelope> decodeDefinition(
            CompoundTag definition,
            SkillDocumentPersistenceLocation location) {
        if (!hasExactFields(definition, DEFINITION_FIELDS)
                || !(definition.get("type") instanceof StringTag typeTag)
                || !(definition.get("schema_version") instanceof IntTag schemaTag)
                || !(definition.get("payload") instanceof CompoundTag payloadTag)
                || schemaTag.getAsInt() < 0) {
            return malformed(location);
        }
        var type = ResourceLocation.CODEC.parse(NbtOps.INSTANCE, typeTag);
        if (type.error().isPresent() || type.result().isEmpty()) {
            return malformed(location);
        }
        var raw = RawTreeEnvelope.decodePhysical(payloadTag, location);
        if (raw.failureValue().isPresent()) {
            return failure(raw.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new PhysicalDefinitionEnvelope(
                type.result().orElseThrow(),
                schemaTag.getAsInt(),
                raw.successValue().orElseThrow()));
    }

    private static SkillDocumentPersistenceResult<CompoundTag> encodeTopAppearance(
            PhysicalTopAppearance appearance) {
        var encoded = new CompoundTag();
        encoded.putString("state", appearance.stateName());
        if (appearance instanceof PhysicalTopAppearance.Decoded decoded) {
            var value = AppearanceStorageCodec.encodeCanonical(
                    new AppearanceDocument.Decoded(decoded.definition()), NbtOps.INSTANCE);
            if (value.error().isPresent() || !(value.result().orElse(null) instanceof CompoundTag tag)) {
                return failure(new SkillDocumentPersistenceFailure.EncodeFailed(topLocation()));
            }
            encoded.put("value", tag);
        } else if (appearance instanceof PhysicalTopAppearance.Unparsed unparsed) {
            encoded.put("raw", unparsed.raw().encodePhysical());
        }
        return SkillDocumentPersistenceResult.success(encoded);
    }

    private static SkillDocumentPersistenceResult<PhysicalTopAppearance> decodeTopAppearance(
            CompoundTag appearance) {
        if (!(appearance.get("state") instanceof StringTag stateTag)) {
            return malformed(topLocation());
        }
        return switch (stateTag.getAsString()) {
            case "default" -> hasExactFields(appearance, STATE_FIELDS)
                    ? SkillDocumentPersistenceResult.success(PhysicalTopAppearance.Default.INSTANCE)
                    : malformed(topLocation());
            case "decoded" -> decodeTopAppearanceValue(appearance);
            case "unparsed" -> decodeTopAppearanceRaw(appearance);
            default -> malformed(topLocation());
        };
    }

    private static SkillDocumentPersistenceResult<PhysicalTopAppearance> decodeTopAppearanceValue(
            CompoundTag appearance) {
        if (!hasExactFields(appearance, VALUE_STATE_FIELDS)
                || !(appearance.get("value") instanceof CompoundTag value)) {
            return malformed(topLocation());
        }
        var parsed = AppearanceStorageCodec.parseStrictTop(new Dynamic<>(NbtOps.INSTANCE, value));
        if (parsed.error().isPresent()
                || !(parsed.result().orElse(null) instanceof AppearanceDocument.Decoded decoded)) {
            return malformed(topLocation());
        }
        return SkillDocumentPersistenceResult.success(new PhysicalTopAppearance.Decoded(decoded.definition()));
    }

    private static SkillDocumentPersistenceResult<PhysicalTopAppearance> decodeTopAppearanceRaw(
            CompoundTag appearance) {
        if (!hasExactFields(appearance, RAW_STATE_FIELDS)
                || !(appearance.get("raw") instanceof CompoundTag rawTag)) {
            return malformed(topLocation());
        }
        var raw = RawTreeEnvelope.decodePhysical(rawTag, topLocation());
        if (raw.failureValue().isPresent()) {
            return failure(raw.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new PhysicalTopAppearance.Unparsed(
                raw.successValue().orElseThrow()));
    }

    private static SkillDocumentPersistenceResult<Optional<CompoundTag>> encodeOverride(
            PhysicalAppearanceOverride appearance,
            int nodeIndex) {
        if (appearance instanceof PhysicalAppearanceOverride.None) {
            return SkillDocumentPersistenceResult.success(Optional.empty());
        }
        var encoded = new CompoundTag();
        encoded.putString("state", appearance.stateName());
        if (appearance instanceof PhysicalAppearanceOverride.Decoded decoded) {
            var value = AppearanceStorageCodec.encodeCanonical(
                    new AppearanceOverrideDocument.Decoded(decoded.override()), NbtOps.INSTANCE);
            if (value.error().isPresent() || !(value.result().orElse(null) instanceof CompoundTag tag)) {
                return failure(new SkillDocumentPersistenceFailure.EncodeFailed(
                        new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex)));
            }
            encoded.put("value", tag);
        } else {
            var unparsed = (PhysicalAppearanceOverride.Unparsed) appearance;
            encoded.put("raw", unparsed.raw().encodePhysical());
        }
        return SkillDocumentPersistenceResult.success(Optional.of(encoded));
    }

    private static SkillDocumentPersistenceResult<PhysicalAppearanceOverride> decodeOverride(
            CompoundTag appearance,
            int nodeIndex) {
        var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
        if (!(appearance.get("state") instanceof StringTag stateTag)) {
            return malformed(location);
        }
        return switch (stateTag.getAsString()) {
            case "none" -> hasExactFields(appearance, STATE_FIELDS)
                    ? SkillDocumentPersistenceResult.success(PhysicalAppearanceOverride.None.INSTANCE)
                    : malformed(location);
            case "decoded" -> decodeOverrideValue(appearance, location);
            case "unparsed" -> decodeOverrideRaw(appearance, location);
            default -> malformed(location);
        };
    }

    private static SkillDocumentPersistenceResult<PhysicalAppearanceOverride> decodeOverrideValue(
            CompoundTag appearance,
            SkillDocumentPersistenceLocation location) {
        if (!hasExactFields(appearance, VALUE_STATE_FIELDS)
                || !(appearance.get("value") instanceof CompoundTag value)) {
            return malformed(location);
        }
        var parsed = AppearanceStorageCodec.parseStrictOverride(new Dynamic<>(NbtOps.INSTANCE, value));
        if (parsed.error().isPresent()
                || !(parsed.result().orElse(null) instanceof AppearanceOverrideDocument.Decoded decoded)) {
            return malformed(location);
        }
        return SkillDocumentPersistenceResult.success(new PhysicalAppearanceOverride.Decoded(decoded.override()));
    }

    private static SkillDocumentPersistenceResult<PhysicalAppearanceOverride> decodeOverrideRaw(
            CompoundTag appearance,
            SkillDocumentPersistenceLocation location) {
        if (!hasExactFields(appearance, RAW_STATE_FIELDS)
                || !(appearance.get("raw") instanceof CompoundTag rawTag)) {
            return malformed(location);
        }
        var raw = RawTreeEnvelope.decodePhysical(rawTag, location);
        if (raw.failureValue().isPresent()) {
            return failure(raw.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new PhysicalAppearanceOverride.Unparsed(
                raw.successValue().orElseThrow()));
    }

    private static boolean hasExactFields(CompoundTag tag, Set<String> expected) {
        return tag.getAllKeys().equals(expected);
    }

    private static SkillDocumentPersistenceLocation.DocumentRoot rootLocation() {
        return SkillDocumentPersistenceLocation.DocumentRoot.INSTANCE;
    }

    private static SkillDocumentPersistenceLocation.TopAppearance topLocation() {
        return SkillDocumentPersistenceLocation.TopAppearance.INSTANCE;
    }

    private static <T> SkillDocumentPersistenceResult<T> malformed(
            SkillDocumentPersistenceLocation location) {
        return failure(new SkillDocumentPersistenceFailure.MalformedPhysicalDocument(location));
    }

    private static <T> SkillDocumentPersistenceResult<T> failure(
            SkillDocumentPersistenceFailure failure) {
        return SkillDocumentPersistenceResult.failure(failure);
    }
}
