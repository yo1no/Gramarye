package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Builds and verifies the raw-free P3 logical document view used by skill migration. */
final class LogicalSkillDocumentConformanceView {
    static final String TOKEN_FIELD = "__gramarye_opaque_raw_token_v0";

    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "skill_id", "revision", "nodes", "appearance");
    private static final Set<String> NODE_FIELDS = Set.of("trigger", "action");
    private static final Set<String> NODE_WITH_OVERRIDE_FIELDS = Set.of(
            "trigger", "action", "appearance_override");
    private static final Set<String> DEFINITION_FIELDS = Set.of(
            "type", "schema_version", "payload");
    private static final Set<String> TOKEN_FIELDS = Set.of(TOKEN_FIELD);
    private static final Set<String> RAW_ENVELOPE_FIELDS = Set.of(
            "family", "registry_context", "compressed_maps", "data");

    private LogicalSkillDocumentConformanceView() {
    }

    static BuildResult build(PhysicalSkillDocument physical) {
        Objects.requireNonNull(physical, "physical");
        var encoded = PhysicalSkillDocumentNbt.encode(physical);
        if (encoded.failureValue().isPresent()) {
            return BuildResult.failed();
        }
        return extractPhysical(encoded.successValue().orElseThrow());
    }

    /**
     * Extracts opaque raw slots before any current logical-shape decode. Unknown outer fields remain
     * visible to skill migration, while every physical raw envelope is removed to the side table.
     */
    static BuildResult extractPhysical(CompoundTag physicalRoot) {
        Objects.requireNonNull(physicalRoot, "physicalRoot");
        try {
            var root = physicalRoot.copy();
            if (!(root.get("schema_version") instanceof IntTag schemaTag)
                    || schemaTag.getAsInt() < 0
                    || !(root.get("nodes") instanceof ListTag physicalNodes)
                    || physicalNodes.isEmpty()
                    || physicalNodes.size() > MagicSafetyCeilings.MAX_NODES
                    || !(root.get("appearance") instanceof CompoundTag physicalAppearance)) {
                return BuildResult.failed();
            }

            var entries = new ArrayList<OpaqueRawTreeEntry>();
            var logicalNodes = new ListTag();
            for (var nodeIndex = 0; nodeIndex < physicalNodes.size(); nodeIndex++) {
                if (!(physicalNodes.get(nodeIndex) instanceof CompoundTag physicalNode)
                        || !(physicalNode.get("trigger") instanceof CompoundTag trigger)
                        || !(physicalNode.get("action") instanceof CompoundTag action)) {
                    return BuildResult.failed();
                }
                var logicalNode = physicalNode.copy();
                var tokenizedTrigger = tokenizeDefinition(
                        trigger, entries, new OpaqueRawTreeLocation.TriggerPayload(nodeIndex));
                var tokenizedAction = tokenizeDefinition(
                        action, entries, new OpaqueRawTreeLocation.ActionPayload(nodeIndex));
                if (tokenizedTrigger.isEmpty() || tokenizedAction.isEmpty()) {
                    return BuildResult.failed();
                }
                logicalNode.put("trigger", tokenizedTrigger.orElseThrow());
                logicalNode.put("action", tokenizedAction.orElseThrow());

                if (physicalNode.contains("appearance_override")) {
                    if (!(physicalNode.get("appearance_override") instanceof CompoundTag override)) {
                        return BuildResult.failed();
                    }
                    var tokenizedOverride = tokenizeAppearance(
                            override,
                            entries,
                            new OpaqueRawTreeLocation.AppearanceOverride(nodeIndex),
                            false);
                    if (tokenizedOverride.isEmpty()) {
                        return BuildResult.failed();
                    }
                    tokenizedOverride.orElseThrow().ifPresent(
                            value -> logicalNode.put("appearance_override", value));
                    if (tokenizedOverride.orElseThrow().isEmpty()) {
                        logicalNode.remove("appearance_override");
                    }
                }
                logicalNodes.add(logicalNode);
            }
            root.put("nodes", logicalNodes);

            var tokenizedTop = tokenizeAppearance(
                    physicalAppearance,
                    entries,
                    OpaqueRawTreeLocation.TopAppearance.INSTANCE,
                    true);
            if (tokenizedTop.isEmpty() || tokenizedTop.orElseThrow().isEmpty()) {
                return BuildResult.failed();
            }
            root.put("appearance", tokenizedTop.orElseThrow().orElseThrow());
            if (containsPhysicalRawEnvelope(root)) {
                return BuildResult.failed();
            }
            return new BuildResult.Success(root, OpaqueRawTreeTable.copyOf(entries));
        } catch (RuntimeException exception) {
            return BuildResult.failed();
        }
    }

    static ReinsertionResult reinsert(CompoundTag migrated, OpaqueRawTreeTable table) {
        Objects.requireNonNull(migrated, "migrated");
        Objects.requireNonNull(table, "table");
        try {
            if (!hasExactFields(migrated, ROOT_FIELDS)
                    || !(migrated.get("schema_version") instanceof IntTag schemaTag)
                    || schemaTag.getAsInt() != SkillDocument.CURRENT_SCHEMA_VERSION
                    || !(migrated.get("skill_id") instanceof StringTag skillIdTag)
                    || !(migrated.get("revision") instanceof IntTag revisionTag)
                    || revisionTag.getAsInt() < 0
                    || !(migrated.get("nodes") instanceof ListTag nodesTag)
                    || nodesTag.isEmpty()
                    || nodesTag.size() > MagicSafetyCeilings.MAX_NODES
                    || !(migrated.get("appearance") instanceof CompoundTag appearanceTag)) {
                return ReinsertionResult.malformed();
            }
            if (!hasExactlyOneOccurrenceOfEveryToken(migrated, table)) {
                return ReinsertionResult.tokenInvariant();
            }

            var skillId = CanonicalDocumentCodecs.PERSISTED_SKILL_ID.parse(NbtOps.INSTANCE, skillIdTag);
            if (skillId.error().isPresent() || skillId.result().isEmpty()) {
                return ReinsertionResult.malformed();
            }

            var nodes = new ArrayList<PhysicalNodeDocument>(Math.min(nodesTag.size(), 16));
            for (var nodeIndex = 0; nodeIndex < nodesTag.size(); nodeIndex++) {
                if (!(nodesTag.get(nodeIndex) instanceof CompoundTag nodeTag)
                        || !(hasExactFields(nodeTag, NODE_FIELDS)
                                || hasExactFields(nodeTag, NODE_WITH_OVERRIDE_FIELDS))
                        || !(nodeTag.get("trigger") instanceof CompoundTag triggerTag)
                        || !(nodeTag.get("action") instanceof CompoundTag actionTag)) {
                    return ReinsertionResult.malformed();
                }
                var trigger = reinsertDefinition(
                        triggerTag,
                        table,
                        new OpaqueRawTreeLocation.TriggerPayload(nodeIndex));
                var action = reinsertDefinition(
                        actionTag,
                        table,
                        new OpaqueRawTreeLocation.ActionPayload(nodeIndex));
                if (trigger.failureKind().isPresent() || action.failureKind().isPresent()) {
                    return ReinsertionResult.tokenInvariant();
                }

                var override = reinsertOverride(nodeTag, table, nodeIndex);
                if (override.failureKind().isPresent()) {
                    return new ReinsertionResult.Failure(override.failureKind().orElseThrow());
                }
                nodes.add(new PhysicalNodeDocument(
                        trigger.value().orElseThrow(),
                        action.value().orElseThrow(),
                        override.value().orElseThrow()));
            }

            var appearance = reinsertTopAppearance(appearanceTag, table);
            if (appearance.failureKind().isPresent()) {
                return new ReinsertionResult.Failure(appearance.failureKind().orElseThrow());
            }
            return new ReinsertionResult.Success(new PhysicalSkillDocument(
                    schemaTag.getAsInt(),
                    skillId.result().orElseThrow(),
                    new SkillRevision(revisionTag.getAsInt()),
                    nodes,
                    appearance.value().orElseThrow()));
        } catch (RuntimeException exception) {
            return ReinsertionResult.malformed();
        }
    }

    private static Optional<CompoundTag> tokenizeDefinition(
            CompoundTag physical,
            List<OpaqueRawTreeEntry> entries,
            OpaqueRawTreeLocation location) {
        if (!(physical.get("type") instanceof StringTag typeTag)
                || !(physical.get("schema_version") instanceof IntTag schemaTag)
                || schemaTag.getAsInt() < 0
                || !(physical.get("payload") instanceof CompoundTag payload)) {
            return Optional.empty();
        }
        var type = ResourceLocation.CODEC.parse(NbtOps.INSTANCE, typeTag);
        var raw = RawTreeEnvelope.decodePhysical(
                payload, persistenceLocation(location));
        if (type.error().isPresent() || type.result().isEmpty()
                || raw.failureValue().isPresent()) {
            return Optional.empty();
        }
        var entry = OpaqueRawTreeEntry.definition(
                entries.size(),
                location,
                type.result().orElseThrow(),
                schemaTag.getAsInt(),
                raw.successValue().orElseThrow());
        entries.add(entry);
        var logical = physical.copy();
        logical.put("payload", sentinel(entry.tokenId()));
        return Optional.of(logical);
    }

    private static Optional<Optional<CompoundTag>> tokenizeAppearance(
            CompoundTag physical,
            List<OpaqueRawTreeEntry> entries,
            OpaqueRawTreeLocation location,
            boolean top) {
        if (!(physical.get("state") instanceof StringTag state)) {
            return Optional.empty();
        }
        return switch (state.getAsString()) {
            case "default" -> top && physical.getAllKeys().equals(Set.of("state"))
                    ? Optional.of(Optional.of(new CompoundTag()))
                    : Optional.empty();
            case "none" -> !top && physical.getAllKeys().equals(Set.of("state"))
                    ? Optional.of(Optional.empty())
                    : Optional.empty();
            case "decoded" -> physical.getAllKeys().equals(Set.of("state", "value"))
                            && physical.get("value") instanceof CompoundTag value
                    ? Optional.of(Optional.of(value.copy()))
                    : Optional.empty();
            case "unparsed" -> tokenizeRawAppearance(physical, entries, location);
            default -> Optional.empty();
        };
    }

    private static Optional<Optional<CompoundTag>> tokenizeRawAppearance(
            CompoundTag physical,
            List<OpaqueRawTreeEntry> entries,
            OpaqueRawTreeLocation location) {
        if (!physical.getAllKeys().equals(Set.of("state", "raw"))
                || !(physical.get("raw") instanceof CompoundTag rawPhysical)) {
            return Optional.empty();
        }
        var raw = RawTreeEnvelope.decodePhysical(rawPhysical, persistenceLocation(location));
        if (raw.failureValue().isPresent()) {
            return Optional.empty();
        }
        var entry = OpaqueRawTreeEntry.appearance(
                entries.size(), location, raw.successValue().orElseThrow());
        entries.add(entry);
        return Optional.of(Optional.of(sentinel(entry.tokenId())));
    }

    private static ParsedValue<PhysicalDefinitionEnvelope> reinsertDefinition(
            CompoundTag definition,
            OpaqueRawTreeTable table,
            OpaqueRawTreeLocation location) {
        var expected = table.findAt(location);
        if (expected.isEmpty()
                || !hasExactFields(definition, DEFINITION_FIELDS)
                || !(definition.get("type") instanceof StringTag typeTag)
                || !(definition.get("schema_version") instanceof IntTag schemaTag)
                || !(definition.get("payload") instanceof CompoundTag payload)) {
            return ParsedValue.tokenInvariant();
        }
        var type = ResourceLocation.CODEC.parse(NbtOps.INSTANCE, typeTag);
        var tokenId = tokenId(payload);
        var entry = expected.orElseThrow();
        if (type.error().isPresent()
                || type.result().isEmpty()
                || tokenId.isEmpty()
                || tokenId.getAsInt() != entry.tokenId()
                || !entry.definitionTypeId().orElseThrow().equals(type.result().orElseThrow())
                || entry.definitionSchemaVersion().orElseThrow() != schemaTag.getAsInt()) {
            return ParsedValue.tokenInvariant();
        }
        return ParsedValue.success(new PhysicalDefinitionEnvelope(
                type.result().orElseThrow(), schemaTag.getAsInt(), entry.rawTree()));
    }

    private static ParsedValue<PhysicalTopAppearance> reinsertTopAppearance(
            CompoundTag appearance,
            OpaqueRawTreeTable table) {
        if (appearance.contains(TOKEN_FIELD)) {
            var entry = table.findAt(OpaqueRawTreeLocation.TopAppearance.INSTANCE);
            var tokenId = tokenId(appearance);
            if (entry.isEmpty() || tokenId.isEmpty()
                    || tokenId.getAsInt() != entry.orElseThrow().tokenId()) {
                return ParsedValue.tokenInvariant();
            }
            return ParsedValue.success(new PhysicalTopAppearance.Unparsed(
                    entry.orElseThrow().rawTree()));
        }
        var parsed = AppearanceStorageCodec.parseStrictTop(new Dynamic<>(NbtOps.INSTANCE, appearance));
        if (parsed.error().isPresent() || parsed.result().isEmpty()) {
            return ParsedValue.malformed();
        }
        var value = parsed.result().orElseThrow();
        if (value instanceof AppearanceDocument.Default) {
            return ParsedValue.success(PhysicalTopAppearance.Default.INSTANCE);
        }
        if (value instanceof AppearanceDocument.Decoded decoded) {
            return ParsedValue.success(new PhysicalTopAppearance.Decoded(decoded.definition()));
        }
        return ParsedValue.malformed();
    }

    private static ParsedValue<PhysicalAppearanceOverride> reinsertOverride(
            CompoundTag node,
            OpaqueRawTreeTable table,
            int nodeIndex) {
        var location = new OpaqueRawTreeLocation.AppearanceOverride(nodeIndex);
        if (!node.contains("appearance_override")) {
            return table.findAt(location).isPresent()
                    ? ParsedValue.tokenInvariant()
                    : ParsedValue.success(PhysicalAppearanceOverride.None.INSTANCE);
        }
        if (!(node.get("appearance_override") instanceof CompoundTag appearance)) {
            return ParsedValue.malformed();
        }
        if (appearance.contains(TOKEN_FIELD)) {
            var entry = table.findAt(location);
            var tokenId = tokenId(appearance);
            if (entry.isEmpty() || tokenId.isEmpty()
                    || tokenId.getAsInt() != entry.orElseThrow().tokenId()) {
                return ParsedValue.tokenInvariant();
            }
            return ParsedValue.success(new PhysicalAppearanceOverride.Unparsed(
                    entry.orElseThrow().rawTree()));
        }
        if (table.findAt(location).isPresent()) {
            return ParsedValue.tokenInvariant();
        }
        var parsed = AppearanceStorageCodec.parseStrictOverride(
                new Dynamic<>(NbtOps.INSTANCE, appearance));
        if (parsed.error().isPresent()
                || !(parsed.result().orElse(null) instanceof AppearanceOverrideDocument.Decoded decoded)) {
            return ParsedValue.malformed();
        }
        return ParsedValue.success(new PhysicalAppearanceOverride.Decoded(decoded.override()));
    }

    private static boolean hasExactlyOneOccurrenceOfEveryToken(
            CompoundTag logical,
            OpaqueRawTreeTable table) {
        var counts = new int[table.size()];
        if (!countSentinels(logical, counts)) {
            return false;
        }
        for (var count : counts) {
            if (count != 1) {
                return false;
            }
        }
        return true;
    }

    private static boolean countSentinels(Tag value, int[] counts) {
        if (value instanceof CompoundTag compound) {
            if (compound.contains(TOKEN_FIELD)) {
                var tokenId = tokenId(compound);
                if (tokenId.isEmpty() || tokenId.getAsInt() >= counts.length) {
                    return false;
                }
                counts[tokenId.getAsInt()]++;
                return counts[tokenId.getAsInt()] == 1;
            }
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (child == null || !countSentinels(child, counts)) {
                    return false;
                }
            }
        } else if (value instanceof ListTag list) {
            for (var child : list) {
                if (!countSentinels(child, counts)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsPhysicalRawEnvelope(Tag value) {
        if (value instanceof CompoundTag compound) {
            if (compound.getAllKeys().equals(RAW_ENVELOPE_FIELDS)) {
                return true;
            }
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (child != null && containsPhysicalRawEnvelope(child)) {
                    return true;
                }
            }
        } else if (value instanceof ListTag list) {
            for (var child : list) {
                if (containsPhysicalRawEnvelope(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static SkillDocumentPersistenceLocation persistenceLocation(
            OpaqueRawTreeLocation location) {
        if (location instanceof OpaqueRawTreeLocation.TriggerPayload trigger) {
            return new SkillDocumentPersistenceLocation.TriggerPayload(trigger.nodeIndex());
        }
        if (location instanceof OpaqueRawTreeLocation.ActionPayload action) {
            return new SkillDocumentPersistenceLocation.ActionPayload(action.nodeIndex());
        }
        if (location instanceof OpaqueRawTreeLocation.AppearanceOverride override) {
            return new SkillDocumentPersistenceLocation.AppearanceOverride(override.nodeIndex());
        }
        return SkillDocumentPersistenceLocation.TopAppearance.INSTANCE;
    }

    private static CompoundTag sentinel(int tokenId) {
        var sentinel = new CompoundTag();
        sentinel.putInt(TOKEN_FIELD, tokenId);
        return sentinel;
    }

    private static OptionalInt tokenId(CompoundTag sentinel) {
        return hasExactFields(sentinel, TOKEN_FIELDS)
                        && sentinel.get(TOKEN_FIELD) instanceof IntTag token
                        && token.getAsInt() >= 0
                ? OptionalInt.of(token.getAsInt())
                : OptionalInt.empty();
    }

    private static boolean hasExactFields(CompoundTag tag, Set<String> fields) {
        return tag.getAllKeys().equals(fields);
    }

    sealed interface BuildResult permits BuildResult.Success, BuildResult.Failure {
        static Failure failed() {
            return Failure.INSTANCE;
        }

        record Success(CompoundTag logicalTree, OpaqueRawTreeTable table) implements BuildResult {
            public Success {
                logicalTree = Objects.requireNonNull(logicalTree, "logicalTree").copy();
                Objects.requireNonNull(table, "table");
            }

            @Override
            public CompoundTag logicalTree() {
                return logicalTree.copy();
            }
        }

        enum Failure implements BuildResult {
            INSTANCE
        }
    }

    sealed interface ReinsertionResult
            permits ReinsertionResult.Success, ReinsertionResult.Failure {
        static Failure malformed() {
            return new Failure(FailureKind.MALFORMED_LOGICAL_VIEW);
        }

        static Failure tokenInvariant() {
            return new Failure(FailureKind.TOKEN_INVARIANT);
        }

        record Success(PhysicalSkillDocument document) implements ReinsertionResult {
            public Success {
                Objects.requireNonNull(document, "document");
            }
        }

        record Failure(FailureKind kind) implements ReinsertionResult {
            public Failure {
                Objects.requireNonNull(kind, "kind");
            }
        }
    }

    enum FailureKind {
        MALFORMED_LOGICAL_VIEW,
        TOKEN_INVARIANT
    }

    private record ParsedValue<T>(Optional<T> value, Optional<FailureKind> failureKind) {
        private ParsedValue {
            value = Objects.requireNonNull(value, "value");
            failureKind = Objects.requireNonNull(failureKind, "failureKind");
            if (value.isPresent() == failureKind.isPresent()) {
                throw new IllegalArgumentException("parsed value must contain exactly one outcome");
            }
        }

        static <T> ParsedValue<T> success(T value) {
            return new ParsedValue<>(Optional.of(Objects.requireNonNull(value, "value")), Optional.empty());
        }

        static <T> ParsedValue<T> malformed() {
            return new ParsedValue<>(Optional.empty(), Optional.of(FailureKind.MALFORMED_LOGICAL_VIEW));
        }

        static <T> ParsedValue<T> tokenInvariant() {
            return new ParsedValue<>(Optional.empty(), Optional.of(FailureKind.TOKEN_INVARIANT));
        }
    }
}
