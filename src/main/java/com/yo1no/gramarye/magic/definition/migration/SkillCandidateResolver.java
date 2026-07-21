package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.codec.ActionDefinitionCodec;
import com.yo1no.gramarye.magic.definition.codec.TriggerDefinitionCodec;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadFailure;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadFailureCode;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReader;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.ArrayList;
import java.util.Objects;

/** Immutable dependency-injected document-to-candidate resolution orchestrator. */
public final class SkillCandidateResolver {
    private final TriggerTypeLookup triggerLookup;
    private final ActionTypeLookup actionLookup;
    private final SkillMigrationPlan skillMigrationPlan;

    public SkillCandidateResolver(
            TriggerTypeLookup triggerLookup,
            ActionTypeLookup actionLookup,
            SkillMigrationPlan skillMigrationPlan) {
        this.triggerLookup = Objects.requireNonNull(triggerLookup, "triggerLookup");
        this.actionLookup = Objects.requireNonNull(actionLookup, "actionLookup");
        this.skillMigrationPlan = Objects.requireNonNull(skillMigrationPlan, "skillMigrationPlan");
    }

    /** Resolves an already-read current-schema document without rerunning migration or Reader. */
    public ResolvedSkillCandidate resolve(
            SkillDocument document,
            SkillDocumentReadReport readReport) {
        return resolve(document, readReport, new PipelineFactCollector());
    }

    /** Runs raw capture, skill migration, tolerant Reader and definition resolution in order. */
    public SkillResolutionResult resolveFromRaw(Dynamic<?> rawDocument) {
        return resolveFromRawTo(rawDocument, SkillDocument.CURRENT_SCHEMA_VERSION);
    }

    SkillResolutionResult resolveFromRawTo(
            Dynamic<?> rawDocument,
            int currentSkillSchemaVersion) {
        var captured = RawSkillDocumentSnapshot.capture(rawDocument);
        if (captured instanceof RawSkillDocumentSnapshot.CaptureResult.Failure failed) {
            return new SkillResolutionResult.RawInputRejected(failed.failure());
        }

        var original = ((RawSkillDocumentSnapshot.CaptureResult.Success) captured).snapshot();
        var migrated = SkillDocumentMigrator.migrateTo(
                original, skillMigrationPlan, currentSkillSchemaVersion);
        if (migrated instanceof SkillMigrationResult.Failure failed) {
            return new SkillResolutionResult.SkillMigrationFailed(failed);
        }

        var migrationSuccess = (SkillMigrationResult.Success) migrated;
        var readResult = SkillDocumentReader.read(
                migrationSuccess.migratedSnapshot().copyRawDocument());
        if (readResult.error().isPresent() || readResult.result().isEmpty()) {
            var failure = SkillDocumentReadFailure.fromReadResult(readResult)
                    .orElseGet(() -> new SkillDocumentReadFailure(
                            SkillDocumentReadFailureCode.READER_REJECTED_INPUT));
            return new SkillResolutionResult.ReadFailed(failure);
        }

        var readSuccess = readResult.result().orElseThrow();
        var facts = new PipelineFactCollector(migrationSuccess.factReport());
        return new SkillResolutionResult.Success(
                resolve(readSuccess.document(), readSuccess.report(), facts));
    }

    ResolvedSkillCandidate resolve(
            SkillDocument document,
            SkillDocumentReadReport readReport,
            PipelineFactReport prefixFacts) {
        return resolve(document, readReport, new PipelineFactCollector(prefixFacts));
    }

    private ResolvedSkillCandidate resolve(
            SkillDocument document,
            SkillDocumentReadReport readReport,
            PipelineFactCollector facts) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(readReport, "readReport");
        var nodes = new ArrayList<ResolvedNodeCandidate>(document.nodes().size());
        for (var nodeIndex = 0; nodeIndex < document.nodes().size(); nodeIndex++) {
            var node = document.nodes().get(nodeIndex);
            nodes.add(new ResolvedNodeCandidate(
                    nodeIndex,
                    resolveTrigger(node.trigger(), nodeIndex, facts),
                    resolveAction(node.action(), nodeIndex, facts),
                    node.appearanceOverride()));
        }
        return new ResolvedSkillCandidate(
                new SkillReference(document.skillId(), document.revision()),
                nodes,
                document.appearance(),
                readReport,
                facts.report());
    }

    private TriggerResolution resolveTrigger(
            DefinitionEnvelope source,
            int nodeIndex,
            PipelineFactCollector facts) {
        var descriptor = triggerLookup.find(source.typeId());
        if (descriptor.isEmpty()) {
            return new TriggerResolution.Unknown(new UnknownTriggerDefinition(
                    source,
                    DefinitionFailure.of(
                            DefinitionFailure.Code.UNKNOWN_TYPE,
                            "Unknown trigger type: " + source.typeId())));
        }
        return resolveTriggerWithDescriptor(
                source, descriptor.orElseThrow(), nodeIndex, facts);
    }

    private <P extends TriggerPayload> TriggerResolution resolveTriggerWithDescriptor(
            DefinitionEnvelope source,
            TriggerType<P> descriptor,
            int nodeIndex,
            PipelineFactCollector facts) {
        var migrated = PayloadMigrator.migrate(
                source,
                descriptor.currentPayloadSchemaVersion(),
                Objects.requireNonNull(descriptor.payloadMigrationPlan(), "payloadMigrationPlan"),
                nodeIndex,
                facts);
        if (migrated instanceof PayloadMigrator.Result.Failure failed) {
            return new TriggerResolution.MigrationFailed(source, descriptor, failed.failure());
        }

        var transientEnvelope = ((PayloadMigrator.Result.Success) migrated).transientEnvelope();
        var decoded = TriggerDefinitionCodec.decodeWithDescriptor(transientEnvelope, descriptor);
        if (decoded instanceof ResolvedTriggerDefinition<?> resolved) {
            return new TriggerResolution.Resolved<>(source, resolved);
        }
        var unknown = (UnknownTriggerDefinition) decoded;
        return new TriggerResolution.DecodeFailed(source, descriptor, unknown.failure());
    }

    private ActionResolution resolveAction(
            DefinitionEnvelope source,
            int nodeIndex,
            PipelineFactCollector facts) {
        var descriptor = actionLookup.find(source.typeId());
        if (descriptor.isEmpty()) {
            return new ActionResolution.Unknown(new UnknownActionDefinition(
                    source,
                    DefinitionFailure.of(
                            DefinitionFailure.Code.UNKNOWN_TYPE,
                            "Unknown action type: " + source.typeId())));
        }
        return resolveActionWithDescriptor(
                source, descriptor.orElseThrow(), nodeIndex, facts);
    }

    private <P extends ActionPayload> ActionResolution resolveActionWithDescriptor(
            DefinitionEnvelope source,
            ActionType<P> descriptor,
            int nodeIndex,
            PipelineFactCollector facts) {
        var migrated = PayloadMigrator.migrate(
                source,
                descriptor.currentPayloadSchemaVersion(),
                Objects.requireNonNull(descriptor.payloadMigrationPlan(), "payloadMigrationPlan"),
                nodeIndex,
                facts);
        if (migrated instanceof PayloadMigrator.Result.Failure failed) {
            return new ActionResolution.MigrationFailed(source, descriptor, failed.failure());
        }

        var transientEnvelope = ((PayloadMigrator.Result.Success) migrated).transientEnvelope();
        var decoded = ActionDefinitionCodec.decodeWithDescriptor(transientEnvelope, descriptor);
        if (decoded instanceof ResolvedActionDefinition<?> resolved) {
            return new ActionResolution.Resolved<>(source, resolved);
        }
        var unknown = (UnknownActionDefinition) decoded;
        return new ActionResolution.DecodeFailed(source, descriptor, unknown.failure());
    }
}
