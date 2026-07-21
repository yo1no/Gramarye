package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.OptionalInt;

final class TolerantDocumentReaders {
    private TolerantDocumentReaders() {
    }

    static DataResult<SkillDocumentReadResult> readDocument(Dynamic<?> input) {
        var global = checkGlobalDepth(input);
        if (global.error().isPresent()) {
            return DataResult.error(() -> global.error().orElseThrow().message());
        }
        var fieldsResult = CanonicalDocumentCodecs.withoutPartial(
                CanonicalDocumentCodecs.DOCUMENT_READ_FIELDS.parse(input));
        if (fieldsResult.error().isPresent()) {
            return DataResult.error(() -> fieldsResult.error().orElseThrow().message());
        }
        var fields = fieldsResult.result().orElseThrow();
        var facts = new ReadFactCollector();
        var nodes = new ArrayList<NodeDocument>(fields.nodes().size());
        for (var index = 0; index < fields.nodes().size(); index++) {
            var nodeFields = fields.nodes().get(index);
            var legacyNullOverride = hasLegacyNullNodeOverride(input, index);
            var override = nodeFields.appearanceOverride().isEmpty()
                    ? legacyNullOverride
                            ? normalizeNullOverride(
                                    new ReadSite(
                                            ReadLocationKind.SKILL_NODE_APPEARANCE_OVERRIDE,
                                            OptionalInt.of(index)),
                                    facts)
                            : DataResult.success(AppearanceOverrideDocument.none())
                    : AppearanceStorageCodec.readOverride(
                            nodeFields.appearanceOverride().orElseThrow(),
                            new ReadSite(
                                    ReadLocationKind.SKILL_NODE_APPEARANCE_OVERRIDE,
                                    OptionalInt.of(index)),
                            facts);
            if (override.error().isPresent()) {
                return DataResult.error(() -> override.error().orElseThrow().message());
            }
            nodes.add(new NodeDocument(
                    nodeFields.trigger(), nodeFields.action(), override.result().orElseThrow()));
        }
        var appearance = fields.appearance().isEmpty()
                ? hasLegacyNullTopAppearance(input)
                        ? normalizeNullAppearance(
                                new ReadSite(ReadLocationKind.SKILL_APPEARANCE, OptionalInt.empty()),
                                facts)
                        : DataResult.success(AppearanceDocument.defaultAppearance())
                : AppearanceStorageCodec.readTop(
                        fields.appearance().orElseThrow(),
                        new ReadSite(ReadLocationKind.SKILL_APPEARANCE, OptionalInt.empty()),
                        facts);
        if (appearance.error().isPresent()) {
            return DataResult.error(() -> appearance.error().orElseThrow().message());
        }
        var document = new SkillDocument(
                fields.schemaVersion(),
                fields.skillId(),
                fields.revision(),
                nodes,
                appearance.result().orElseThrow());
        return DataResult.success(new SkillDocumentReadResult(document, facts.documentReport()));
    }

    static DataResult<SkillDraftReadResult> readDraft(Dynamic<?> input) {
        var global = checkGlobalDepth(input);
        if (global.error().isPresent()) {
            return DataResult.error(() -> global.error().orElseThrow().message());
        }
        var fieldsResult = CanonicalDocumentCodecs.withoutPartial(
                CanonicalDocumentCodecs.DRAFT_READ_FIELDS.parse(input));
        if (fieldsResult.error().isPresent()) {
            return DataResult.error(() -> fieldsResult.error().orElseThrow().message());
        }
        var fields = fieldsResult.result().orElseThrow();
        var facts = new ReadFactCollector();
        var nodes = new ArrayList<DraftNode>(fields.nodes().size());
        for (var index = 0; index < fields.nodes().size(); index++) {
            var nodeFields = fields.nodes().get(index);
            var legacyNullOverride = hasLegacyNullNodeOverride(input, index);
            var override = nodeFields.appearanceOverride().isEmpty()
                    ? legacyNullOverride
                            ? normalizeNullOverride(
                                    new ReadSite(
                                            ReadLocationKind.DRAFT_NODE_APPEARANCE_OVERRIDE,
                                            OptionalInt.of(index)),
                                    facts)
                            : DataResult.success(AppearanceOverrideDocument.none())
                    : AppearanceStorageCodec.readOverride(
                            nodeFields.appearanceOverride().orElseThrow(),
                            new ReadSite(
                                    ReadLocationKind.DRAFT_NODE_APPEARANCE_OVERRIDE,
                                    OptionalInt.of(index)),
                            facts);
            if (override.error().isPresent()) {
                return DataResult.error(() -> override.error().orElseThrow().message());
            }
            nodes.add(new DraftNode(
                    nodeFields.trigger(), nodeFields.action(), override.result().orElseThrow()));
        }
        var appearance = fields.appearance().isEmpty()
                ? hasLegacyNullTopAppearance(input)
                        ? normalizeNullAppearance(
                                new ReadSite(ReadLocationKind.DRAFT_APPEARANCE, OptionalInt.empty()),
                                facts)
                        : DataResult.success(AppearanceDocument.defaultAppearance())
                : AppearanceStorageCodec.readTop(
                        fields.appearance().orElseThrow(),
                        new ReadSite(ReadLocationKind.DRAFT_APPEARANCE, OptionalInt.empty()),
                        facts);
        if (appearance.error().isPresent()) {
            return DataResult.error(() -> appearance.error().orElseThrow().message());
        }
        var draft = new SkillDraft(
                fields.draftSchemaVersion(),
                fields.skillId(),
                fields.baseRevision(),
                nodes,
                appearance.result().orElseThrow());
        return DataResult.success(new SkillDraftReadResult(draft, facts.draftReport()));
    }

    private static DataResult<Dynamic<?>> checkGlobalDepth(Dynamic<?> input) {
        var bounds = DynamicTreeSupport.checkBounds(
                input,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                Long.MAX_VALUE);
        return bounds == DynamicTreeSupport.BoundsResult.WITHIN_LIMITS
                ? DataResult.success(input)
                : DataResult.error(() -> "Skill tree exceeds global hard depth or uses unsupported ops: "
                        + bounds);
    }

    private static DataResult<AppearanceDocument> normalizeNullAppearance(
            ReadSite site,
            ReadFactCollector facts) {
        facts.add(site.fact(ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED, null));
        return DataResult.success(AppearanceDocument.defaultAppearance());
    }

    private static DataResult<AppearanceOverrideDocument> normalizeNullOverride(
            ReadSite site,
            ReadFactCollector facts) {
        facts.add(site.fact(ReadFactCode.LEGACY_NULL_OVERRIDE_NORMALIZED, null));
        return DataResult.success(AppearanceOverrideDocument.none());
    }

    private static boolean hasLegacyNullTopAppearance(Dynamic<?> input) {
        return input.getValue() instanceof JsonObject root
                && root.has("appearance")
                && root.get("appearance").isJsonNull();
    }

    private static boolean hasLegacyNullNodeOverride(Dynamic<?> input, int index) {
        if (!(input.getValue() instanceof JsonObject root)
                || !(root.get("nodes") instanceof JsonArray nodes)
                || index >= nodes.size()
                || !(nodes.get(index) instanceof JsonObject node)) {
            return false;
        }
        return node.has("appearance_override") && node.get("appearance_override").isJsonNull();
    }
}
