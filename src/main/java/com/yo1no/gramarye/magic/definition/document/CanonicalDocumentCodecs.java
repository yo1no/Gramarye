package com.yo1no.gramarye.magic.definition.document;

import com.mojang.datafixers.util.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class CanonicalDocumentCodecs {
    static final Codec<SkillId> PERSISTED_SKILL_ID = Codec.STRING.comapFlatMap(
            CanonicalDocumentCodecs::decodeSkillId,
            skillId -> skillId.value().toString());
    static final Codec<DraftTriggerSlot> DRAFT_TRIGGER_SLOT = draftTriggerSlotCodec();
    static final Codec<DraftActionSlot> DRAFT_ACTION_SLOT = draftActionSlotCodec();

    static final Codec<NodeSerializedFields> NODE_FIELDS = RecordCodecBuilder.create(instance -> instance.group(
                    DefinitionEnvelope.CODEC.fieldOf("trigger").forGetter(NodeSerializedFields::trigger),
                    DefinitionEnvelope.CODEC.fieldOf("action").forGetter(NodeSerializedFields::action),
                    Codec.PASSTHROUGH.optionalFieldOf("appearance_override")
                            .forGetter(NodeSerializedFields::appearanceOverride))
            .apply(instance, NodeSerializedFields::new));

    static final Codec<DraftNodeSerializedFields> DRAFT_NODE_FIELDS = RecordCodecBuilder.create(instance -> instance.group(
                    DRAFT_TRIGGER_SLOT.fieldOf("trigger").forGetter(DraftNodeSerializedFields::trigger),
                    DRAFT_ACTION_SLOT.fieldOf("action").forGetter(DraftNodeSerializedFields::action),
                    Codec.PASSTHROUGH.optionalFieldOf("appearance_override")
                            .forGetter(DraftNodeSerializedFields::appearanceOverride))
            .apply(instance, DraftNodeSerializedFields::new));

    static final Codec<DocumentReadFields> DOCUMENT_READ_FIELDS = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("schema_version")
                            .forGetter(DocumentReadFields::schemaVersion),
                    PERSISTED_SKILL_ID.fieldOf("skill_id").forGetter(DocumentReadFields::skillId),
                    SkillRevision.CODEC.fieldOf("revision").forGetter(DocumentReadFields::revision),
                    NODE_FIELDS.listOf(0, MagicSafetyCeilings.MAX_NODES)
                            .fieldOf("nodes")
                            .forGetter(DocumentReadFields::nodes),
                    Codec.PASSTHROUGH.optionalFieldOf("appearance")
                            .forGetter(DocumentReadFields::appearance))
            .apply(instance, DocumentReadFields::new));

    static final Codec<DraftReadFields> DRAFT_READ_FIELDS = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("draft_schema_version")
                            .forGetter(DraftReadFields::draftSchemaVersion),
                    PERSISTED_SKILL_ID.fieldOf("skill_id").forGetter(DraftReadFields::skillId),
                    SkillRevision.CODEC.optionalFieldOf("base_revision")
                            .forGetter(DraftReadFields::baseRevision),
                    DRAFT_NODE_FIELDS.listOf(0, MagicSafetyCeilings.MAX_NODES)
                            .fieldOf("nodes")
                            .forGetter(DraftReadFields::nodes),
                    Codec.PASSTHROUGH.optionalFieldOf("appearance")
                            .forGetter(DraftReadFields::appearance))
            .apply(instance, DraftReadFields::new));

    static final Codec<NodeDocument> NODE = new Codec<>() {
        @Override
        public <T> DataResult<Pair<NodeDocument, T>> decode(DynamicOps<T> ops, T input) {
            if (hasNullOverride(input)) {
                return DataResult.error(() -> "Canonical appearance_override must be omitted, not null");
            }
            return withoutPartial(NODE_FIELDS.decode(ops, input))
                    .flatMap(pair -> decodeStrictNode(pair.getFirst())
                            .map(node -> Pair.of(node, pair.getSecond())));
        }

        @Override
        public <T> DataResult<T> encode(NodeDocument input, DynamicOps<T> ops, T prefix) {
            return DocumentStorageWriters.writeCanonicalNode(input, ops)
                    .flatMap(value -> withoutPartial(ops.mergeToPrimitive(prefix, value)));
        }
    };

    static final Codec<SkillDocument> SKILL_DOCUMENT = new GloballyBoundedCodec<>(
            DOCUMENT_READ_FIELDS.flatXmap(
                    CanonicalDocumentCodecs::decodeStrictDocument,
                    document -> DataResult.error(() -> "SkillDocument encoding requires target DynamicOps")),
            DocumentStorageWriters::writeCanonicalDocument,
            CanonicalDocumentCodecs::canonicalDocumentShape);

    static final Codec<SkillDraft> SKILL_DRAFT = new GloballyBoundedCodec<>(
            DRAFT_READ_FIELDS.flatXmap(
                    CanonicalDocumentCodecs::decodeStrictDraft,
                    draft -> DataResult.error(() -> "SkillDraft encoding requires target DynamicOps")),
            DocumentStorageWriters::writeCanonicalDraft,
            CanonicalDocumentCodecs::canonicalDraftShape);

    private CanonicalDocumentCodecs() {
    }

    private static DataResult<SkillId> decodeSkillId(String value) {
        try {
            var parsed = UUID.fromString(value);
            return parsed.toString().equals(value)
                    ? DataResult.success(new SkillId(parsed))
                    : DataResult.error(() -> "skill_id must use canonical lowercase UUID text");
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "skill_id must be a canonical UUID string");
        }
    }

    private static DataResult<Boolean> canonicalDocumentShape(Object value) {
        if (hasNullAppearance(value)) {
            return DataResult.error(() -> "Canonical appearance must not be null");
        }
        return hasNullOverrideInNodes(value)
                ? DataResult.error(() -> "Canonical appearance_override must be omitted, not null")
                : DataResult.success(true);
    }

    private static DataResult<Boolean> canonicalDraftShape(Object value) {
        if (hasNullAppearance(value)) {
            return DataResult.error(() -> "Canonical appearance must not be null");
        }
        if (value instanceof JsonObject root && root.has("base_revision")
                && root.get("base_revision").isJsonNull()) {
            return DataResult.error(() -> "Canonical base_revision must be omitted, not null");
        }
        return hasNullOverrideInNodes(value)
                ? DataResult.error(() -> "Canonical appearance_override must be omitted, not null")
                : DataResult.success(true);
    }

    private static boolean hasNullAppearance(Object value) {
        return value instanceof JsonObject root
                && root.has("appearance")
                && root.get("appearance").isJsonNull();
    }

    private static boolean hasNullOverrideInNodes(Object value) {
        if (!(value instanceof JsonObject root) || !(root.get("nodes") instanceof JsonArray nodes)) {
            return false;
        }
        for (JsonElement node : nodes) {
            if (hasNullOverride(node)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNullOverride(Object value) {
        return value instanceof JsonObject node
                && node.has("appearance_override")
                && node.get("appearance_override").isJsonNull();
    }

    private static DataResult<NodeDocument> decodeStrictNode(NodeSerializedFields fields) {
        if (fields.appearanceOverride().isEmpty()) {
            return DataResult.success(new NodeDocument(
                    fields.trigger(),
                    fields.action(),
                    AppearanceOverrideDocument.none()));
        }
        return AppearanceStorageCodec.parseStrictOverride(fields.appearanceOverride().orElseThrow())
                .map(override -> new NodeDocument(fields.trigger(), fields.action(), override));
    }

    private static DataResult<SkillDocument> decodeStrictDocument(DocumentReadFields fields) {
        if (fields.appearance().isEmpty()) {
            return DataResult.error(() -> "Canonical SkillDocument requires appearance");
        }
        var nodes = new ArrayList<NodeDocument>(fields.nodes().size());
        for (var fieldsNode : fields.nodes()) {
            var node = decodeStrictNode(fieldsNode);
            if (node.error().isPresent()) {
                return DataResult.error(() -> node.error().orElseThrow().message());
            }
            nodes.add(node.result().orElseThrow());
        }
        return AppearanceStorageCodec.parseStrictTop(fields.appearance().orElseThrow())
                .map(appearance -> new SkillDocument(
                        fields.schemaVersion(), fields.skillId(), fields.revision(), nodes, appearance));
    }

    private static DataResult<SkillDraft> decodeStrictDraft(DraftReadFields fields) {
        if (fields.appearance().isEmpty()) {
            return DataResult.error(() -> "Canonical SkillDraft requires appearance");
        }
        var nodes = new ArrayList<DraftNode>(fields.nodes().size());
        for (var fieldsNode : fields.nodes()) {
            var override = fieldsNode.appearanceOverride().isEmpty()
                    ? DataResult.success(AppearanceOverrideDocument.none())
                    : AppearanceStorageCodec.parseStrictOverride(fieldsNode.appearanceOverride().orElseThrow());
            if (override.error().isPresent()) {
                return DataResult.error(() -> override.error().orElseThrow().message());
            }
            nodes.add(new DraftNode(
                    fieldsNode.trigger(), fieldsNode.action(), override.result().orElseThrow()));
        }
        return AppearanceStorageCodec.parseStrictTop(fields.appearance().orElseThrow())
                .map(appearance -> new SkillDraft(
                        fields.draftSchemaVersion(), fields.skillId(), fields.baseRevision(), nodes, appearance));
    }

    private static Codec<DraftTriggerSlot> draftTriggerSlotCodec() {
        return noPartial(DraftSlotFields.CODEC.flatXmap(fields -> switch (fields.state()) {
            case "missing" -> fields.definition().isEmpty()
                    ? DataResult.success(DraftTriggerSlot.missing())
                    : DataResult.error(() -> "Missing trigger slot must not contain definition");
            case "present" -> fields.definition()
                    .<DataResult<DraftTriggerSlot>>map(definition -> DataResult.success(
                            DraftTriggerSlot.present(definition)))
                    .orElseGet(() -> DataResult.error(() -> "Present trigger slot requires definition"));
            default -> DataResult.error(() -> "Unknown trigger slot state");
        }, slot -> DataResult.success(slot instanceof DraftTriggerSlot.Missing
                ? new DraftSlotFields("missing", Optional.empty())
                : new DraftSlotFields(
                        "present", Optional.of(((DraftTriggerSlot.Present) slot).definition())))));
    }

    private static Codec<DraftActionSlot> draftActionSlotCodec() {
        return noPartial(DraftSlotFields.CODEC.flatXmap(fields -> switch (fields.state()) {
            case "missing" -> fields.definition().isEmpty()
                    ? DataResult.success(DraftActionSlot.missing())
                    : DataResult.error(() -> "Missing action slot must not contain definition");
            case "present" -> fields.definition()
                    .<DataResult<DraftActionSlot>>map(definition -> DataResult.success(
                            DraftActionSlot.present(definition)))
                    .orElseGet(() -> DataResult.error(() -> "Present action slot requires definition"));
            default -> DataResult.error(() -> "Unknown action slot state");
        }, slot -> DataResult.success(slot instanceof DraftActionSlot.Missing
                ? new DraftSlotFields("missing", Optional.empty())
                : new DraftSlotFields(
                        "present", Optional.of(((DraftActionSlot.Present) slot).definition())))));
    }

    static <A> Codec<A> noPartial(Codec<A> delegate) {
        return Codec.of(
                new com.mojang.serialization.Encoder<>() {
                    @Override
                    public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                        return withoutPartial(delegate.encode(input, ops, prefix));
                    }
                },
                new com.mojang.serialization.Decoder<>() {
                    @Override
                    public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                        return withoutPartial(delegate.decode(ops, input));
                    }
                });
    }

    static <A> DataResult<A> withoutPartial(DataResult<A> result) {
        return result.error().isPresent()
                ? DataResult.error(() -> DynamicTreeSupport.boundedDiagnostic(
                        result.error().orElseThrow().message()))
                : DataResult.success(result.result().orElseThrow());
    }

    private static final class GloballyBoundedCodec<A> implements Codec<A> {
        private final Codec<A> decoder;
        private final DocumentEncoder<A> encoder;
        private final CanonicalInputGuard inputGuard;

        private GloballyBoundedCodec(
                Codec<A> decoder,
                DocumentEncoder<A> encoder,
                CanonicalInputGuard inputGuard) {
            this.decoder = decoder;
            this.encoder = encoder;
            this.inputGuard = inputGuard;
        }

        @Override
        public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
            var bounds = DynamicTreeSupport.checkBounds(
                    new Dynamic<>(ops, input),
                    MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                    Long.MAX_VALUE);
            if (bounds != DynamicTreeSupport.BoundsResult.WITHIN_LIMITS) {
                return DataResult.error(() -> "Skill tree exceeds global hard depth or uses unsupported ops: "
                        + bounds);
            }
            var guarded = inputGuard.check(input);
            if (guarded.error().isPresent()) {
                return DataResult.error(() -> guarded.error().orElseThrow().message());
            }
            return withoutPartial(decoder.decode(ops, input));
        }

        @Override
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            var encoded = encoder.encode(input, ops);
            if (encoded.error().isPresent()) {
                return DataResult.error(() -> encoded.error().orElseThrow().message());
            }
            return withoutPartial(ops.mergeToPrimitive(prefix, encoded.result().orElseThrow()));
        }
    }

    @FunctionalInterface
    private interface DocumentEncoder<A> {
        <T> DataResult<T> encode(A input, DynamicOps<T> ops);
    }

    @FunctionalInterface
    private interface CanonicalInputGuard {
        DataResult<Boolean> check(Object input);
    }
}

record NodeSerializedFields(
        DefinitionEnvelope trigger,
        DefinitionEnvelope action,
        Optional<Dynamic<?>> appearanceOverride) {
}

record DraftNodeSerializedFields(
        DraftTriggerSlot trigger,
        DraftActionSlot action,
        Optional<Dynamic<?>> appearanceOverride) {
}

record DocumentReadFields(
        int schemaVersion,
        SkillId skillId,
        SkillRevision revision,
        List<NodeSerializedFields> nodes,
        Optional<Dynamic<?>> appearance) {
}

record DraftReadFields(
        int draftSchemaVersion,
        SkillId skillId,
        Optional<SkillRevision> baseRevision,
        List<DraftNodeSerializedFields> nodes,
        Optional<Dynamic<?>> appearance) {
}

record DraftSlotFields(String state, Optional<DefinitionEnvelope> definition) {
    static final Codec<DraftSlotFields> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("state").forGetter(DraftSlotFields::state),
                    DefinitionEnvelope.CODEC.optionalFieldOf("definition")
                            .forGetter(DraftSlotFields::definition))
            .apply(instance, DraftSlotFields::new));
}
