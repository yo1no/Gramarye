package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Optional;

final class DocumentStorageWriters {
    private DocumentStorageWriters() {
    }

    static <T> DataResult<T> writeCanonicalDocument(SkillDocument document, DynamicOps<T> ops) {
        return writeDocument(document, ops, false);
    }

    static <T> DataResult<T> writeDocumentForStorage(SkillDocument document, DynamicOps<T> ops) {
        return writeDocument(document, ops, true);
    }

    static <T> DataResult<T> writeCanonicalDraft(SkillDraft draft, DynamicOps<T> ops) {
        return writeDraft(draft, ops, false);
    }

    static <T> DataResult<T> writeDraftForStorage(SkillDraft draft, DynamicOps<T> ops) {
        return writeDraft(draft, ops, true);
    }

    static <T> DataResult<T> writeCanonicalNode(NodeDocument node, DynamicOps<T> ops) {
        return encodeNode(node, ops, false)
                .flatMap(fields -> CanonicalDocumentCodecs.NODE_FIELDS.encodeStart(ops, fields));
    }

    private static <T> DataResult<T> writeDocument(
            SkillDocument document,
            DynamicOps<T> ops,
            boolean storageMode) {
        var appearance = storageMode
                ? AppearanceStorageCodec.encodeForStorage(document.appearance(), ops)
                : AppearanceStorageCodec.encodeCanonical(document.appearance(), ops);
        if (appearance.error().isPresent()) {
            return DataResult.error(() -> appearance.error().orElseThrow().message());
        }

        var nodes = new ArrayList<NodeSerializedFields>(document.nodes().size());
        for (var node : document.nodes()) {
            var encoded = encodeNode(node, ops, storageMode);
            if (encoded.error().isPresent()) {
                return DataResult.error(() -> encoded.error().orElseThrow().message());
            }
            nodes.add(encoded.result().orElseThrow());
        }

        var fields = new DocumentReadFields(
                document.schemaVersion(),
                document.skillId(),
                document.revision(),
                nodes,
                Optional.of(new Dynamic<>(ops, appearance.result().orElseThrow())));
        return finishBounded(CanonicalDocumentCodecs.DOCUMENT_READ_FIELDS.encodeStart(ops, fields), ops);
    }

    private static <T> DataResult<T> writeDraft(
            SkillDraft draft,
            DynamicOps<T> ops,
            boolean storageMode) {
        var appearance = storageMode
                ? AppearanceStorageCodec.encodeForStorage(draft.appearance(), ops)
                : AppearanceStorageCodec.encodeCanonical(draft.appearance(), ops);
        if (appearance.error().isPresent()) {
            return DataResult.error(() -> appearance.error().orElseThrow().message());
        }

        var nodes = new ArrayList<DraftNodeSerializedFields>(draft.nodes().size());
        for (var node : draft.nodes()) {
            var encoded = encodeDraftNode(node, ops, storageMode);
            if (encoded.error().isPresent()) {
                return DataResult.error(() -> encoded.error().orElseThrow().message());
            }
            nodes.add(encoded.result().orElseThrow());
        }

        var fields = new DraftReadFields(
                draft.draftSchemaVersion(),
                draft.skillId(),
                draft.baseRevision(),
                nodes,
                Optional.of(new Dynamic<>(ops, appearance.result().orElseThrow())));
        return finishBounded(CanonicalDocumentCodecs.DRAFT_READ_FIELDS.encodeStart(ops, fields), ops);
    }

    private static <T> DataResult<NodeSerializedFields> encodeNode(
            NodeDocument node,
            DynamicOps<T> ops,
            boolean storageMode) {
        if (storageMode) {
            return AppearanceStorageCodec.encodeForStorage(node.appearanceOverride(), ops)
                    .map(appearance -> new NodeSerializedFields(
                            node.trigger(),
                            node.action(),
                            appearance.map(value -> new Dynamic<>(ops, value))));
        }
        if (node.appearanceOverride() instanceof AppearanceOverrideDocument.None) {
            return DataResult.success(new NodeSerializedFields(
                    node.trigger(), node.action(), Optional.empty()));
        }
        return AppearanceStorageCodec.encodeCanonical(node.appearanceOverride(), ops)
                .map(appearance -> new NodeSerializedFields(
                        node.trigger(),
                        node.action(),
                        Optional.of(new Dynamic<>(ops, appearance))));
    }

    private static <T> DataResult<DraftNodeSerializedFields> encodeDraftNode(
            DraftNode node,
            DynamicOps<T> ops,
            boolean storageMode) {
        if (storageMode) {
            return AppearanceStorageCodec.encodeForStorage(node.appearanceOverride(), ops)
                    .map(appearance -> new DraftNodeSerializedFields(
                            node.trigger(),
                            node.action(),
                            appearance.map(value -> new Dynamic<>(ops, value))));
        }
        if (node.appearanceOverride() instanceof AppearanceOverrideDocument.None) {
            return DataResult.success(new DraftNodeSerializedFields(
                    node.trigger(), node.action(), Optional.empty()));
        }
        return AppearanceStorageCodec.encodeCanonical(node.appearanceOverride(), ops)
                .map(appearance -> new DraftNodeSerializedFields(
                        node.trigger(),
                        node.action(),
                        Optional.of(new Dynamic<>(ops, appearance))));
    }

    private static <T> DataResult<T> finishBounded(DataResult<T> encoded, DynamicOps<T> ops) {
        var complete = CanonicalDocumentCodecs.withoutPartial(encoded);
        if (complete.error().isPresent()) {
            return complete;
        }
        var value = complete.result().orElseThrow();
        var bounds = DynamicTreeSupport.checkBounds(
                new Dynamic<>(ops, value),
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                Long.MAX_VALUE);
        return bounds == DynamicTreeSupport.BoundsResult.WITHIN_LIMITS
                ? DataResult.success(value)
                : DataResult.error(() -> "Writer output exceeds global hard depth or uses unsupported ops: "
                        + bounds);
    }
}
