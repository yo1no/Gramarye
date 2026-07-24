package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;

/** Package-internal current-schema bridge; the future general load path must migrate first. */
final class SkillDocumentPersistenceBridge {
    private static final int TOP_APPEARANCE_DEPTH = 2;
    private static final int DEFINITION_PAYLOAD_DEPTH = 5;
    private static final int APPEARANCE_OVERRIDE_DEPTH = 4;

    private SkillDocumentPersistenceBridge() {
    }

    static SkillDocumentPersistenceResult<ImmutableEncodedBytes> encodeCurrent(
            SkillDocument document) {
        Objects.requireNonNull(document, "document");
        try {
            var schemaFailure = currentSchemaFailure(document.schemaVersion());
            if (schemaFailure != null) {
                return failure(schemaFailure);
            }
            if (document.nodes().isEmpty()) {
                return failure(new SkillDocumentPersistenceFailure.EncodeFailed(rootLocation()));
            }

            var mapping = mapForEncoding(document);
            if (mapping.failureValue().isPresent()) {
                return failure(mapping.failureValue().orElseThrow());
            }
            var mapped = mapping.successValue().orElseThrow();
            var boundsFailure = documentBoundsFailure(mapped.logicalTree());
            if (boundsFailure != null) {
                return failure(boundsFailure);
            }

            var physical = PhysicalSkillDocumentNbt.encode(mapped.physicalDocument());
            if (physical.failureValue().isPresent()) {
                return failure(physical.failureValue().orElseThrow());
            }
            try {
                return SkillDocumentPersistenceResult.success(StrictNbtTreeCodec.encode(
                        physical.successValue().orElseThrow(),
                        MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES));
            } catch (BoundedByteEncoding.CapacityExceeded exception) {
                return failure(new SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded(
                        rootLocation(), exception.observedAtLeast(), exception.maximum()));
            } catch (IOException exception) {
                return failure(new SkillDocumentPersistenceFailure.EncodeFailed(rootLocation()));
            }
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    rootLocation(), exception));
        }
    }

    static SkillDocumentPersistenceResult<SkillDocument> hydrateCurrentForInternalUse(
            ImmutableEncodedBytes encodedDocument,
            Optional<HolderLookup.Provider> provider) {
        Objects.requireNonNull(encodedDocument, "encodedDocument");
        Objects.requireNonNull(provider, "provider");
        try {
            CompoundTag root;
            try {
                var decoded = StrictNbtTreeCodec.decode(
                        encodedDocument, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES);
                if (!(decoded instanceof CompoundTag compound)) {
                    return failure(new SkillDocumentPersistenceFailure.MalformedPhysicalDocument(
                            rootLocation()));
                }
                root = compound;
            } catch (BoundedByteEncoding.CapacityExceeded exception) {
                return failure(new SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded(
                        rootLocation(), exception.observedAtLeast(), exception.maximum()));
            } catch (IOException exception) {
                return failure(new SkillDocumentPersistenceFailure.MalformedPhysicalDocument(
                        rootLocation()));
            }

            var physical = PhysicalSkillDocumentNbt.decode(root);
            if (physical.failureValue().isPresent()) {
                return failure(physical.failureValue().orElseThrow());
            }
            var physicalDocument = physical.successValue().orElseThrow();
            var schemaFailure = currentSchemaFailure(physicalDocument.schemaVersion());
            if (schemaFailure != null) {
                return failure(schemaFailure);
            }

            var hydration = hydrateDocument(physicalDocument, provider);
            if (hydration.failureValue().isPresent()) {
                return failure(hydration.failureValue().orElseThrow());
            }
            var hydrated = hydration.successValue().orElseThrow();
            var boundsFailure = documentBoundsFailure(hydrated.logicalTree());
            return boundsFailure == null
                    ? SkillDocumentPersistenceResult.success(hydrated.document())
                    : failure(boundsFailure);
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    rootLocation(), exception));
        }
    }

    private static SkillDocumentPersistenceResult<EncodingMapping> mapForEncoding(
            SkillDocument document) {
        var physicalNodes = new ArrayList<PhysicalNodeDocument>(document.nodes().size());
        var logicalNodes = new ListTag();
        var rawTrees = new ArrayList<Dynamic<?>>();
        var rawDepths = new ArrayList<Integer>();

        for (var index = 0; index < document.nodes().size(); index++) {
            var node = document.nodes().get(index);
            var triggerLocation = new SkillDocumentPersistenceLocation.TriggerPayload(index);
            var actionLocation = new SkillDocumentPersistenceLocation.ActionPayload(index);
            var trigger = captureDefinition(node.trigger(), triggerLocation);
            if (trigger.failureValue().isPresent()) {
                return failure(trigger.failureValue().orElseThrow());
            }
            var action = captureDefinition(node.action(), actionLocation);
            if (action.failureValue().isPresent()) {
                return failure(action.failureValue().orElseThrow());
            }
            var appearance = captureOverride(node.appearanceOverride(), index);
            if (appearance.failureValue().isPresent()) {
                return failure(appearance.failureValue().orElseThrow());
            }

            physicalNodes.add(new PhysicalNodeDocument(
                    trigger.successValue().orElseThrow().physical(),
                    action.successValue().orElseThrow().physical(),
                    appearance.successValue().orElseThrow().physical()));
            logicalNodes.add(logicalNode(
                    node,
                    appearance.successValue().orElseThrow().canonicalTree()));
            addRaw(rawTrees, rawDepths, trigger.successValue().orElseThrow().rawTree(),
                    DEFINITION_PAYLOAD_DEPTH);
            addRaw(rawTrees, rawDepths, action.successValue().orElseThrow().rawTree(),
                    DEFINITION_PAYLOAD_DEPTH);
            appearance.successValue().orElseThrow().rawTree().ifPresent(raw ->
                    addRaw(rawTrees, rawDepths, raw, APPEARANCE_OVERRIDE_DEPTH));
        }

        var appearance = captureTopAppearance(document.appearance());
        if (appearance.failureValue().isPresent()) {
            return failure(appearance.failureValue().orElseThrow());
        }
        var top = appearance.successValue().orElseThrow();
        top.rawTree().ifPresent(raw -> addRaw(rawTrees, rawDepths, raw, TOP_APPEARANCE_DEPTH));

        var logicalRoot = logicalRoot(document, logicalNodes, top.canonicalTree());
        return SkillDocumentPersistenceResult.success(new EncodingMapping(
                new PhysicalSkillDocument(
                        document.schemaVersion(),
                        document.skillId(),
                        document.revision(),
                        physicalNodes,
                        top.physical()),
                new LogicalTree(new Dynamic<>(NbtOps.INSTANCE, logicalRoot), rawTrees, rawDepths)));
    }

    private static SkillDocumentPersistenceResult<HydrationMapping> hydrateDocument(
            PhysicalSkillDocument physical,
            Optional<HolderLookup.Provider> provider) {
        var nodes = new ArrayList<NodeDocument>(physical.nodes().size());
        var logicalNodes = new ListTag();
        var rawTrees = new ArrayList<Dynamic<?>>();
        var rawDepths = new ArrayList<Integer>();

        for (var index = 0; index < physical.nodes().size(); index++) {
            var physicalNode = physical.nodes().get(index);
            var triggerLocation = new SkillDocumentPersistenceLocation.TriggerPayload(index);
            var actionLocation = new SkillDocumentPersistenceLocation.ActionPayload(index);
            var trigger = hydrateDefinition(physicalNode.trigger(), provider, triggerLocation);
            if (trigger.failureValue().isPresent()) {
                return failure(trigger.failureValue().orElseThrow());
            }
            var action = hydrateDefinition(physicalNode.action(), provider, actionLocation);
            if (action.failureValue().isPresent()) {
                return failure(action.failureValue().orElseThrow());
            }
            var appearance = hydrateOverride(physicalNode.appearanceOverride(), provider, index);
            if (appearance.failureValue().isPresent()) {
                return failure(appearance.failureValue().orElseThrow());
            }

            var node = new NodeDocument(
                    trigger.successValue().orElseThrow().definition(),
                    action.successValue().orElseThrow().definition(),
                    appearance.successValue().orElseThrow().document());
            nodes.add(node);
            logicalNodes.add(logicalNode(
                    node,
                    appearance.successValue().orElseThrow().canonicalTree()));
            addRaw(rawTrees, rawDepths, trigger.successValue().orElseThrow().rawTree(),
                    DEFINITION_PAYLOAD_DEPTH);
            addRaw(rawTrees, rawDepths, action.successValue().orElseThrow().rawTree(),
                    DEFINITION_PAYLOAD_DEPTH);
            appearance.successValue().orElseThrow().rawTree().ifPresent(raw ->
                    addRaw(rawTrees, rawDepths, raw, APPEARANCE_OVERRIDE_DEPTH));
        }

        var appearance = hydrateTopAppearance(physical.appearance(), provider);
        if (appearance.failureValue().isPresent()) {
            return failure(appearance.failureValue().orElseThrow());
        }
        var top = appearance.successValue().orElseThrow();
        top.rawTree().ifPresent(raw -> addRaw(rawTrees, rawDepths, raw, TOP_APPEARANCE_DEPTH));
        var document = new SkillDocument(
                physical.schemaVersion(), physical.skillId(), physical.revision(), nodes, top.document());
        return SkillDocumentPersistenceResult.success(new HydrationMapping(
                document,
                new LogicalTree(
                        new Dynamic<>(NbtOps.INSTANCE, logicalRoot(document, logicalNodes, top.canonicalTree())),
                        rawTrees,
                        rawDepths)));
    }

    private static SkillDocumentPersistenceResult<CapturedDefinition> captureDefinition(
            DefinitionEnvelope definition,
            SkillDocumentPersistenceLocation location) {
        var raw = definition.copyRawPayload();
        var captured = RawTreeEnvelope.capture(raw, location);
        if (captured.failureValue().isPresent()) {
            return failure(captured.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new CapturedDefinition(
                new PhysicalDefinitionEnvelope(
                        definition.typeId(),
                        definition.schemaVersion(),
                        captured.successValue().orElseThrow()),
                raw));
    }

    private static SkillDocumentPersistenceResult<HydratedDefinition> hydrateDefinition(
            PhysicalDefinitionEnvelope physical,
            Optional<HolderLookup.Provider> provider,
            SkillDocumentPersistenceLocation location) {
        var hydrated = physical.payload().hydrate(provider, location);
        if (hydrated.failureValue().isPresent()) {
            return failure(hydrated.failureValue().orElseThrow());
        }
        var raw = hydrated.successValue().orElseThrow();
        return SkillDocumentPersistenceResult.success(new HydratedDefinition(
                new DefinitionEnvelope(physical.typeId(), physical.schemaVersion(), raw), raw));
    }

    private static SkillDocumentPersistenceResult<CapturedTopAppearance> captureTopAppearance(
            AppearanceDocument appearance) {
        if (appearance instanceof AppearanceDocument.Default
                || appearance instanceof AppearanceDocument.Rejected) {
            return SkillDocumentPersistenceResult.success(new CapturedTopAppearance(
                    PhysicalTopAppearance.Default.INSTANCE,
                    Optional.of(new CompoundTag()),
                    Optional.empty()));
        }
        if (appearance instanceof AppearanceDocument.Decoded decoded) {
            var canonical = canonicalTop(decoded);
            if (canonical.failureValue().isPresent()) {
                return failure(canonical.failureValue().orElseThrow());
            }
            return SkillDocumentPersistenceResult.success(new CapturedTopAppearance(
                    new PhysicalTopAppearance.Decoded(decoded.definition()),
                    canonical.successValue(),
                    Optional.empty()));
        }

        var unparsed = (AppearanceDocument.Unparsed) appearance;
        var raw = unparsed.copyRawAppearance();
        var location = topLocation();
        var boundsFailure = appearanceBoundsFailure(raw, location);
        if (boundsFailure != null) {
            return failure(boundsFailure);
        }
        var captured = RawTreeEnvelope.capture(raw, location);
        if (captured.failureValue().isPresent()) {
            return failure(captured.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new CapturedTopAppearance(
                new PhysicalTopAppearance.Unparsed(captured.successValue().orElseThrow()),
                Optional.empty(),
                Optional.of(raw)));
    }

    private static SkillDocumentPersistenceResult<CapturedOverride> captureOverride(
            AppearanceOverrideDocument appearance,
            int nodeIndex) {
        if (appearance instanceof AppearanceOverrideDocument.None
                || appearance instanceof AppearanceOverrideDocument.Rejected) {
            return SkillDocumentPersistenceResult.success(new CapturedOverride(
                    PhysicalAppearanceOverride.None.INSTANCE,
                    Optional.empty(),
                    Optional.empty()));
        }
        if (appearance instanceof AppearanceOverrideDocument.Decoded decoded) {
            var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
            var canonical = canonicalOverride(decoded, location);
            if (canonical.failureValue().isPresent()) {
                return failure(canonical.failureValue().orElseThrow());
            }
            return SkillDocumentPersistenceResult.success(new CapturedOverride(
                    new PhysicalAppearanceOverride.Decoded(decoded.override()),
                    canonical.successValue(),
                    Optional.empty()));
        }

        var unparsed = (AppearanceOverrideDocument.Unparsed) appearance;
        var raw = unparsed.copyRawAppearance();
        var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
        var boundsFailure = appearanceBoundsFailure(raw, location);
        if (boundsFailure != null) {
            return failure(boundsFailure);
        }
        var captured = RawTreeEnvelope.capture(raw, location);
        if (captured.failureValue().isPresent()) {
            return failure(captured.failureValue().orElseThrow());
        }
        return SkillDocumentPersistenceResult.success(new CapturedOverride(
                new PhysicalAppearanceOverride.Unparsed(captured.successValue().orElseThrow()),
                Optional.empty(),
                Optional.of(raw)));
    }

    private static SkillDocumentPersistenceResult<HydratedTopAppearance> hydrateTopAppearance(
            PhysicalTopAppearance appearance,
            Optional<HolderLookup.Provider> provider) {
        if (appearance instanceof PhysicalTopAppearance.Default) {
            return SkillDocumentPersistenceResult.success(new HydratedTopAppearance(
                    AppearanceDocument.defaultAppearance(),
                    Optional.of(new CompoundTag()),
                    Optional.empty()));
        }
        if (appearance instanceof PhysicalTopAppearance.Decoded decoded) {
            var domain = new AppearanceDocument.Decoded(decoded.definition());
            var canonical = canonicalTop(domain);
            if (canonical.failureValue().isPresent()) {
                return failure(canonical.failureValue().orElseThrow());
            }
            return SkillDocumentPersistenceResult.success(new HydratedTopAppearance(
                    domain, canonical.successValue(), Optional.empty()));
        }

        var location = topLocation();
        var raw = ((PhysicalTopAppearance.Unparsed) appearance).raw().hydrate(provider, location);
        if (raw.failureValue().isPresent()) {
            return failure(raw.failureValue().orElseThrow());
        }
        var dynamic = raw.successValue().orElseThrow();
        var boundsFailure = appearanceBoundsFailure(dynamic, location);
        if (boundsFailure != null) {
            return failure(boundsFailure);
        }
        var snapshot = AppearanceRawSnapshot.capture(dynamic);
        if (snapshot.error().isPresent()) {
            return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
        }
        return SkillDocumentPersistenceResult.success(new HydratedTopAppearance(
                new AppearanceDocument.Unparsed(snapshot.result().orElseThrow()),
                Optional.empty(),
                Optional.of(dynamic)));
    }

    private static SkillDocumentPersistenceResult<HydratedOverride> hydrateOverride(
            PhysicalAppearanceOverride appearance,
            Optional<HolderLookup.Provider> provider,
            int nodeIndex) {
        if (appearance instanceof PhysicalAppearanceOverride.None) {
            return SkillDocumentPersistenceResult.success(new HydratedOverride(
                    AppearanceOverrideDocument.none(), Optional.empty(), Optional.empty()));
        }
        var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
        if (appearance instanceof PhysicalAppearanceOverride.Decoded decoded) {
            var domain = new AppearanceOverrideDocument.Decoded(decoded.override());
            var canonical = canonicalOverride(domain, location);
            if (canonical.failureValue().isPresent()) {
                return failure(canonical.failureValue().orElseThrow());
            }
            return SkillDocumentPersistenceResult.success(new HydratedOverride(
                    domain, canonical.successValue(), Optional.empty()));
        }

        var raw = ((PhysicalAppearanceOverride.Unparsed) appearance).raw().hydrate(provider, location);
        if (raw.failureValue().isPresent()) {
            return failure(raw.failureValue().orElseThrow());
        }
        var dynamic = raw.successValue().orElseThrow();
        var boundsFailure = appearanceBoundsFailure(dynamic, location);
        if (boundsFailure != null) {
            return failure(boundsFailure);
        }
        var snapshot = AppearanceRawSnapshot.capture(dynamic);
        if (snapshot.error().isPresent()) {
            return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
        }
        return SkillDocumentPersistenceResult.success(new HydratedOverride(
                new AppearanceOverrideDocument.Unparsed(snapshot.result().orElseThrow()),
                Optional.empty(),
                Optional.of(dynamic)));
    }

    private static SkillDocumentPersistenceResult<CompoundTag> canonicalTop(
            AppearanceDocument.Decoded appearance) {
        var encoded = AppearanceStorageCodec.encodeCanonical(appearance, NbtOps.INSTANCE);
        if (encoded.error().isPresent() || !(encoded.result().orElse(null) instanceof CompoundTag tag)) {
            return failure(new SkillDocumentPersistenceFailure.EncodeFailed(topLocation()));
        }
        return SkillDocumentPersistenceResult.success(tag);
    }

    private static SkillDocumentPersistenceResult<CompoundTag> canonicalOverride(
            AppearanceOverrideDocument.Decoded appearance,
            SkillDocumentPersistenceLocation location) {
        var encoded = AppearanceStorageCodec.encodeCanonical(appearance, NbtOps.INSTANCE);
        if (encoded.error().isPresent() || !(encoded.result().orElse(null) instanceof CompoundTag tag)) {
            return failure(new SkillDocumentPersistenceFailure.EncodeFailed(location));
        }
        return SkillDocumentPersistenceResult.success(tag);
    }

    private static CompoundTag logicalRoot(
            SkillDocument document,
            ListTag logicalNodes,
            Optional<CompoundTag> canonicalTopAppearance) {
        var root = new CompoundTag();
        root.putInt("schema_version", document.schemaVersion());
        var skillId = CanonicalDocumentCodecs.PERSISTED_SKILL_ID
                .encodeStart(NbtOps.INSTANCE, document.skillId());
        root.put("skill_id", skillId.result().filter(StringTag.class::isInstance)
                .map(StringTag.class::cast).orElseThrow());
        root.putInt("revision", document.revision().value());
        root.put("nodes", logicalNodes);
        canonicalTopAppearance.ifPresent(value -> root.put("appearance", value));
        return root;
    }

    private static CompoundTag logicalNode(
            NodeDocument node,
            Optional<CompoundTag> canonicalAppearanceOverride) {
        var logical = new CompoundTag();
        logical.put("trigger", logicalDefinition(node.trigger()));
        logical.put("action", logicalDefinition(node.action()));
        canonicalAppearanceOverride.ifPresent(value -> logical.put("appearance_override", value));
        return logical;
    }

    private static CompoundTag logicalDefinition(DefinitionEnvelope definition) {
        var logical = new CompoundTag();
        logical.putString("type", definition.typeId().toString());
        logical.putInt("schema_version", definition.schemaVersion());
        return logical;
    }

    private static SkillDocumentPersistenceFailure appearanceBoundsFailure(
            Dynamic<?> dynamic,
            SkillDocumentPersistenceLocation location) {
        return switch (AppearanceStorageCodec.relativeBounds(dynamic)) {
            case WITHIN_LIMITS -> null;
            case DEPTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.DEPTH);
            case NODE_COUNT_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT);
            case KEY_LENGTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.KEY_LENGTH);
            case UNSUPPORTED -> new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location);
        };
    }

    private static SkillDocumentPersistenceFailure documentBoundsFailure(LogicalTree logicalTree) {
        return switch (DynamicTreeBounds.checkComposite(
                logicalTree.outerTree(),
                logicalTree.rawTrees(),
                logicalTree.rawDepths(),
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES)) {
            case WITHIN_LIMITS -> null;
            case DEPTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    rootLocation(), SkillDocumentPersistenceFailure.DocumentBoundKind.DEPTH);
            case NODE_COUNT_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    rootLocation(), SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT);
            case KEY_LENGTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    rootLocation(), SkillDocumentPersistenceFailure.DocumentBoundKind.KEY_LENGTH);
            case UNSUPPORTED -> new SkillDocumentPersistenceFailure.UnsupportedRawFamily(rootLocation());
        };
    }

    private static SkillDocumentPersistenceFailure.UnsupportedDocumentSchema currentSchemaFailure(
            int actual) {
        return actual == SkillDocument.CURRENT_SCHEMA_VERSION
                ? null
                : new SkillDocumentPersistenceFailure.UnsupportedDocumentSchema(
                        rootLocation(), actual, SkillDocument.CURRENT_SCHEMA_VERSION);
    }

    private static void addRaw(
            List<Dynamic<?>> trees,
            List<Integer> depths,
            Dynamic<?> raw,
            int depth) {
        trees.add(raw);
        depths.add(depth);
    }

    private static SkillDocumentPersistenceLocation.DocumentRoot rootLocation() {
        return SkillDocumentPersistenceLocation.DocumentRoot.INSTANCE;
    }

    private static SkillDocumentPersistenceLocation.TopAppearance topLocation() {
        return SkillDocumentPersistenceLocation.TopAppearance.INSTANCE;
    }

    private static <T> SkillDocumentPersistenceResult<T> failure(
            SkillDocumentPersistenceFailure failure) {
        return SkillDocumentPersistenceResult.failure(failure);
    }

    private record LogicalTree(
            Dynamic<?> outerTree,
            List<Dynamic<?>> rawTrees,
            List<Integer> rawDepths) {
        private LogicalTree {
            Objects.requireNonNull(outerTree, "outerTree");
            rawTrees = List.copyOf(rawTrees);
            rawDepths = List.copyOf(rawDepths);
        }
    }

    private record EncodingMapping(PhysicalSkillDocument physicalDocument, LogicalTree logicalTree) {
    }

    private record HydrationMapping(SkillDocument document, LogicalTree logicalTree) {
    }

    private record CapturedDefinition(PhysicalDefinitionEnvelope physical, Dynamic<?> rawTree) {
    }

    private record HydratedDefinition(DefinitionEnvelope definition, Dynamic<?> rawTree) {
    }

    private record CapturedTopAppearance(
            PhysicalTopAppearance physical,
            Optional<CompoundTag> canonicalTree,
            Optional<Dynamic<?>> rawTree) {
    }

    private record CapturedOverride(
            PhysicalAppearanceOverride physical,
            Optional<CompoundTag> canonicalTree,
            Optional<Dynamic<?>> rawTree) {
    }

    private record HydratedTopAppearance(
            AppearanceDocument document,
            Optional<CompoundTag> canonicalTree,
            Optional<Dynamic<?>> rawTree) {
    }

    private record HydratedOverride(
            AppearanceOverrideDocument document,
            Optional<CompoundTag> canonicalTree,
            Optional<Dynamic<?>> rawTree) {
    }
}
