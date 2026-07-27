package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;

/** Package-internal mixed-family Draft bridge; it never routes through SkillDocument persistence. */
final class SkillDraftPersistenceBridge {
    private SkillDraftPersistenceBridge() {
    }

    static SkillDraftPersistenceFacade.EncodeResult encodeCurrent(SkillDraft draft) {
        Objects.requireNonNull(draft, "draft");
        try {
            if (draft.draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) {
                return encodeRejected(SkillDraftPersistenceFacade.FailureCode.ENCODE_REJECTED);
            }
            var physical = capture(draft);
            var root = PhysicalSkillDraftNbt.encode(physical);
            var encoded = StrictNbtTreeCodec.encode(
                    root,
                    SkillDraftPersistenceFacade.EncodedSkillDraft.maximumEncodedBytes());
            return new SkillDraftPersistenceFacade.Encoded(
                    SkillDraftPersistenceFacade.EncodedSkillDraft.takeCurrentOwnership(
                            encoded.copyBytes()));
        } catch (BoundedByteEncoding.CapacityExceeded exception) {
            return new SkillDraftPersistenceFacade.EncodeRejected(
                    new SkillDraftPersistenceFacade.CapacityFailure(
                            SkillDraftPersistenceFacade.FailureCode.DRAFT_ENTRY_CAPACITY_EXCEEDED,
                            exception.observedAtLeast(),
                            exception.maximum()));
        } catch (PhysicalSkillDraftNbt.DraftFormatException
                | DraftBridgeException
                | IOException exception) {
            return encodeRejected(SkillDraftPersistenceFacade.FailureCode.ENCODE_REJECTED);
        } catch (RuntimeException exception) {
            return new SkillDraftPersistenceFacade.EncodeRejected(
                    SkillDraftPersistenceFacade.CodecFailure.from(
                            SkillDraftPersistenceFacade.FailureCode.INTERNAL_CODEC_EXCEPTION,
                            exception));
        }
    }

    static SkillDraftPersistenceFacade.LoadResult loadAlwaysMigrating(
            SkillDraftPersistenceFacade.EncodedSkillDraft encoded,
            Optional<HolderLookup.Provider> provider) {
        return loadAlwaysMigrating(
                encoded,
                provider,
                DraftPersistenceMigration.Plans.production(),
                SkillDraftLogicalMigration.Plans.production());
    }

    static SkillDraftPersistenceFacade.LoadResult loadAlwaysMigrating(
            SkillDraftPersistenceFacade.EncodedSkillDraft encoded,
            Optional<HolderLookup.Provider> provider,
            DraftPersistenceMigration.Plan physicalPlan,
            SkillDraftLogicalMigration.Plan logicalPlan) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(physicalPlan, "physicalPlan");
        Objects.requireNonNull(logicalPlan, "logicalPlan");
        try {
            var migratedPhysical = DraftPersistenceMigration.migrate(encoded, physicalPlan);
            if (migratedPhysical instanceof DraftPersistenceMigration.Failure) {
                return loadRejected(
                        SkillDraftPersistenceFacade.FailureCode.DRAFT_PHYSICAL_MIGRATION_FAILED);
            }
            if (migratedPhysical instanceof DraftPersistenceMigration.ExceptionFailure failure) {
                return new SkillDraftPersistenceFacade.LoadRejected(
                        new SkillDraftPersistenceFacade.CodecFailure(
                                SkillDraftPersistenceFacade.FailureCode.DRAFT_PHYSICAL_MIGRATION_FAILED,
                                boundedClassName(failure.exceptionClassName())));
            }
            var physicalSuccess = (DraftPersistenceMigration.Success) migratedPhysical;
            var root = PhysicalSkillDraftNbt.decodeEncoded(
                    physicalSuccess.draft().copyInternalBytes());
            var physical = PhysicalSkillDraftNbt.decode(root);

            var built = LogicalSkillDraftConformanceView.build(physical);
            if (!(built instanceof LogicalSkillDraftConformanceView.Built view)) {
                return loadRejected(
                        SkillDraftPersistenceFacade.FailureCode.OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION);
            }
            var migratedLogical = SkillDraftLogicalMigration.migrate(
                    view.logicalTree(), logicalPlan);
            if (migratedLogical instanceof SkillDraftLogicalMigration.Failure) {
                return loadRejected(
                        SkillDraftPersistenceFacade.FailureCode.DRAFT_LOGICAL_MIGRATION_FAILED);
            }
            if (migratedLogical instanceof SkillDraftLogicalMigration.ExceptionFailure failure) {
                return new SkillDraftPersistenceFacade.LoadRejected(
                        new SkillDraftPersistenceFacade.CodecFailure(
                                SkillDraftPersistenceFacade.FailureCode.DRAFT_LOGICAL_MIGRATION_FAILED,
                                boundedClassName(failure.exceptionClassName())));
            }
            var logicalSuccess = (SkillDraftLogicalMigration.Success) migratedLogical;
            var reinserted = LogicalSkillDraftConformanceView.reinsert(
                    logicalSuccess.draft(), view.table());
            if (!(reinserted instanceof LogicalSkillDraftConformanceView.Reinserted current)) {
                return loadRejected(
                        SkillDraftPersistenceFacade.FailureCode.OPAQUE_DRAFT_RAW_INVARIANT_VIOLATION);
            }
            var draft = hydrate(current.draft(), provider);
            return new SkillDraftPersistenceFacade.Loaded(
                    draft,
                    physicalSuccess.migrated(),
                    logicalSuccess.migrated());
        } catch (PhysicalSkillDraftNbt.DraftFormatException
                | DraftBridgeException
                | IOException exception) {
            return loadRejected(SkillDraftPersistenceFacade.FailureCode.DRAFT_DECODE_FAILED);
        } catch (RuntimeException exception) {
            return new SkillDraftPersistenceFacade.LoadRejected(
                    SkillDraftPersistenceFacade.CodecFailure.from(
                            SkillDraftPersistenceFacade.FailureCode.INTERNAL_CODEC_EXCEPTION,
                            exception));
        }
    }

    private static PhysicalSkillDraft capture(SkillDraft draft) throws DraftBridgeException {
        var nodes = new ArrayList<PhysicalDraftNode>(draft.nodes().size());
        for (var index = 0; index < draft.nodes().size(); index++) {
            var node = draft.nodes().get(index);
            nodes.add(new PhysicalDraftNode(
                    captureTrigger(node.trigger(), index),
                    captureAction(node.action(), index),
                    captureOverride(node.appearanceOverride(), index)));
        }
        return new PhysicalSkillDraft(
                draft.draftSchemaVersion(),
                draft.skillId(),
                draft.baseRevision(),
                nodes,
                captureTopAppearance(draft.appearance()));
    }

    private static PhysicalDraftTriggerSlot captureTrigger(DraftTriggerSlot slot, int nodeIndex)
            throws DraftBridgeException {
        if (slot instanceof DraftTriggerSlot.Missing) {
            return PhysicalDraftTriggerSlot.Missing.INSTANCE;
        }
        return new PhysicalDraftTriggerSlot.Present(captureDefinition(
                ((DraftTriggerSlot.Present) slot).definition(),
                new SkillDocumentPersistenceLocation.TriggerPayload(nodeIndex)));
    }

    private static PhysicalDraftActionSlot captureAction(DraftActionSlot slot, int nodeIndex)
            throws DraftBridgeException {
        if (slot instanceof DraftActionSlot.Missing) {
            return PhysicalDraftActionSlot.Missing.INSTANCE;
        }
        return new PhysicalDraftActionSlot.Present(captureDefinition(
                ((DraftActionSlot.Present) slot).definition(),
                new SkillDocumentPersistenceLocation.ActionPayload(nodeIndex)));
    }

    private static PhysicalDefinitionEnvelope captureDefinition(
            DefinitionEnvelope definition,
            SkillDocumentPersistenceLocation location) throws DraftBridgeException {
        var captured = RawTreeEnvelope.capture(definition.copyRawPayload(), location);
        if (captured.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        return new PhysicalDefinitionEnvelope(
                definition.typeId(),
                definition.schemaVersion(),
                captured.successValue().orElseThrow());
    }

    private static PhysicalTopAppearance captureTopAppearance(AppearanceDocument appearance)
            throws DraftBridgeException {
        if (appearance instanceof AppearanceDocument.Default
                || appearance instanceof AppearanceDocument.Rejected) {
            return PhysicalTopAppearance.Default.INSTANCE;
        }
        if (appearance instanceof AppearanceDocument.Decoded decoded) {
            return new PhysicalTopAppearance.Decoded(decoded.definition());
        }
        var raw = ((AppearanceDocument.Unparsed) appearance).copyRawAppearance();
        var captured = RawTreeEnvelope.capture(
                raw, SkillDocumentPersistenceLocation.TopAppearance.INSTANCE);
        if (captured.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        return new PhysicalTopAppearance.Unparsed(captured.successValue().orElseThrow());
    }

    private static PhysicalAppearanceOverride captureOverride(
            AppearanceOverrideDocument appearance,
            int nodeIndex) throws DraftBridgeException {
        if (appearance instanceof AppearanceOverrideDocument.None
                || appearance instanceof AppearanceOverrideDocument.Rejected) {
            return PhysicalAppearanceOverride.None.INSTANCE;
        }
        if (appearance instanceof AppearanceOverrideDocument.Decoded decoded) {
            return new PhysicalAppearanceOverride.Decoded(decoded.override());
        }
        var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
        var captured = RawTreeEnvelope.capture(
                ((AppearanceOverrideDocument.Unparsed) appearance).copyRawAppearance(), location);
        if (captured.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        return new PhysicalAppearanceOverride.Unparsed(captured.successValue().orElseThrow());
    }

    private static SkillDraft hydrate(
            PhysicalSkillDraft physical,
            Optional<HolderLookup.Provider> provider) throws DraftBridgeException {
        if (physical.draftSchemaVersion() != SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION) {
            throw new DraftBridgeException();
        }
        var nodes = new ArrayList<DraftNode>(physical.nodes().size());
        for (var index = 0; index < physical.nodes().size(); index++) {
            var node = physical.nodes().get(index);
            nodes.add(new DraftNode(
                    hydrateTrigger(node.trigger(), provider, index),
                    hydrateAction(node.action(), provider, index),
                    hydrateOverride(node.appearanceOverride(), provider, index)));
        }
        return new SkillDraft(
                physical.draftSchemaVersion(),
                physical.skillId(),
                physical.baseRevision(),
                nodes,
                hydrateTopAppearance(physical.appearance(), provider));
    }

    private static DraftTriggerSlot hydrateTrigger(
            PhysicalDraftTriggerSlot slot,
            Optional<HolderLookup.Provider> provider,
            int nodeIndex) throws DraftBridgeException {
        if (slot instanceof PhysicalDraftTriggerSlot.Missing) {
            return DraftTriggerSlot.missing();
        }
        return DraftTriggerSlot.present(hydrateDefinition(
                ((PhysicalDraftTriggerSlot.Present) slot).definition(),
                provider,
                new SkillDocumentPersistenceLocation.TriggerPayload(nodeIndex)));
    }

    private static DraftActionSlot hydrateAction(
            PhysicalDraftActionSlot slot,
            Optional<HolderLookup.Provider> provider,
            int nodeIndex) throws DraftBridgeException {
        if (slot instanceof PhysicalDraftActionSlot.Missing) {
            return DraftActionSlot.missing();
        }
        return DraftActionSlot.present(hydrateDefinition(
                ((PhysicalDraftActionSlot.Present) slot).definition(),
                provider,
                new SkillDocumentPersistenceLocation.ActionPayload(nodeIndex)));
    }

    private static DefinitionEnvelope hydrateDefinition(
            PhysicalDefinitionEnvelope definition,
            Optional<HolderLookup.Provider> provider,
            SkillDocumentPersistenceLocation location) throws DraftBridgeException {
        var hydrated = definition.payload().hydrate(provider, location);
        if (hydrated.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        return new DefinitionEnvelope(
                definition.typeId(),
                definition.schemaVersion(),
                hydrated.successValue().orElseThrow());
    }

    private static AppearanceDocument hydrateTopAppearance(
            PhysicalTopAppearance appearance,
            Optional<HolderLookup.Provider> provider) throws DraftBridgeException {
        if (appearance instanceof PhysicalTopAppearance.Default) {
            return AppearanceDocument.defaultAppearance();
        }
        if (appearance instanceof PhysicalTopAppearance.Decoded decoded) {
            return new AppearanceDocument.Decoded(decoded.definition());
        }
        var hydrated = ((PhysicalTopAppearance.Unparsed) appearance).raw().hydrate(
                provider, SkillDocumentPersistenceLocation.TopAppearance.INSTANCE);
        if (hydrated.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        var snapshot = AppearanceRawSnapshot.capture(hydrated.successValue().orElseThrow());
        if (snapshot.error().isPresent()) {
            throw new DraftBridgeException();
        }
        return new AppearanceDocument.Unparsed(snapshot.result().orElseThrow());
    }

    private static AppearanceOverrideDocument hydrateOverride(
            PhysicalAppearanceOverride appearance,
            Optional<HolderLookup.Provider> provider,
            int nodeIndex) throws DraftBridgeException {
        if (appearance instanceof PhysicalAppearanceOverride.None) {
            return AppearanceOverrideDocument.none();
        }
        if (appearance instanceof PhysicalAppearanceOverride.Decoded decoded) {
            return new AppearanceOverrideDocument.Decoded(decoded.override());
        }
        var location = new SkillDocumentPersistenceLocation.AppearanceOverride(nodeIndex);
        var hydrated = ((PhysicalAppearanceOverride.Unparsed) appearance).raw().hydrate(
                provider, location);
        if (hydrated.failureValue().isPresent()) {
            throw new DraftBridgeException();
        }
        var snapshot = AppearanceRawSnapshot.capture(hydrated.successValue().orElseThrow());
        if (snapshot.error().isPresent()) {
            throw new DraftBridgeException();
        }
        return new AppearanceOverrideDocument.Unparsed(snapshot.result().orElseThrow());
    }

    private static SkillDraftPersistenceFacade.EncodeRejected encodeRejected(
            SkillDraftPersistenceFacade.FailureCode code) {
        return new SkillDraftPersistenceFacade.EncodeRejected(
                new SkillDraftPersistenceFacade.SimpleFailure(code));
    }

    private static SkillDraftPersistenceFacade.LoadRejected loadRejected(
            SkillDraftPersistenceFacade.FailureCode code) {
        return new SkillDraftPersistenceFacade.LoadRejected(
                new SkillDraftPersistenceFacade.SimpleFailure(code));
    }

    private static String boundedClassName(String name) {
        return name.length() <= 192 ? name : name.substring(0, 192);
    }

    private static final class DraftBridgeException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
