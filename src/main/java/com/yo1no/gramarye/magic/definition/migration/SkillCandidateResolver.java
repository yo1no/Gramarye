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
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.ArrayList;
import java.util.Objects;

/** Immutable dependency-injected document-to-candidate resolution orchestrator. */
public final class SkillCandidateResolver {
    private final TriggerTypeLookup triggerLookup;
    private final ActionTypeLookup actionLookup;
    private final SkillMigrationPlan skillMigrationPlan;

    public SkillCandidateResolver(
            TriggerTypeLookup triggerLookup,
            ActionTypeLookup actionLookup) {
        this(triggerLookup, actionLookup, SkillMigrationPlans.production());
    }

    SkillCandidateResolver(
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

    /**
     * P10 ordered ingress: completes lookup, schema/migration, decode and semantic validation
     * for the first two ordered nodes before whole-skill eligibility. Structurally admitted extra
     * nodes never allocate typed projections; their original count rejects the whole candidate.
     * Unexpected failures propagate unchanged; legacy entries retain their containment policy.
     */
    public OrderedValidationResult resolveAndValidate(
            SkillDocument document,
            SkillDocumentReadReport readReport,
            ValidationContext context,
            ProfileAvailabilityView profiles) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(readReport, "readReport");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(profiles, "profiles");
        var originalNodeCount = document.nodes().size();
        var typedNodeCount = Math.min(originalNodeCount, 2);
        var facts = new PipelineFactCollector();
        var nodes = new ArrayList<ResolvedNodeCandidate>(typedNodeCount);
        var collector = new ValidationCollector();
        var firstFailure = FirstFailure.NONE;
        for (var nodeIndex = 0; nodeIndex < typedNodeCount; nodeIndex++) {
            var node = document.nodes().get(nodeIndex);
            var trigger = resolveTrigger(node.trigger(), nodeIndex, facts, true);
            var triggerFatal = SkillValidationAnalyzer.validateOrderedEnvelope(
                    trigger, context, nodeIndex, collector);
            firstFailure = first(firstFailure, classify(trigger));
            firstFailure = first(firstFailure, triggerFatal ? FirstFailure.SEMANTIC_INVALID : FirstFailure.NONE);

            var action = resolveAction(node.action(), nodeIndex, facts, true);
            var actionFatal = SkillValidationAnalyzer.validateOrderedEnvelope(
                    action, context, nodeIndex, collector);
            firstFailure = first(firstFailure, classify(action));
            firstFailure = first(firstFailure, actionFatal ? FirstFailure.SEMANTIC_INVALID : FirstFailure.NONE);
            nodes.add(new ResolvedNodeCandidate(nodeIndex, trigger, action, node.appearanceOverride()));
        }
        var candidate = new ResolvedSkillCandidate(document.schemaVersion(),
                new SkillReference(document.skillId(), document.revision()), nodes,
                document.appearance(), readReport, facts.report());
        // Exact-two is a whole-shape scalar, after every authorized envelope coordinate. It
        // cannot be hidden by collector saturation or allow an accepted projected prefix.
        if (originalNodeCount != 2) {
            firstFailure = first(firstFailure, FirstFailure.SEMANTIC_INVALID);
        }
        var handoff = new OrderedSemanticHandoff(candidate, context, collector, firstFailure, originalNodeCount);
        var analysis = new SkillValidationAnalyzer(new NodeProjectionResolver(), profiles)
                .analyzeOrdered(handoff, collector);
        return new OrderedValidationResult(analysis,
                first(firstFailure, semanticFailure(analysis.report())));
    }

    private static FirstFailure classify(TriggerResolution result) {
        return switch (result) {
            case TriggerResolution.Unknown ignored -> FirstFailure.UNKNOWN_TYPE;
            case TriggerResolution.MigrationFailed failed -> migrationFailure(failed.failure());
            case TriggerResolution.DecodeFailed ignored -> FirstFailure.DECODE_FAILED;
            case TriggerResolution.Resolved<?> ignored -> FirstFailure.NONE;
        };
    }

    private static FirstFailure classify(ActionResolution result) {
        return switch (result) {
            case ActionResolution.Unknown ignored -> FirstFailure.UNKNOWN_TYPE;
            case ActionResolution.MigrationFailed failed -> migrationFailure(failed.failure());
            case ActionResolution.DecodeFailed ignored -> FirstFailure.DECODE_FAILED;
            case ActionResolution.Resolved<?> ignored -> FirstFailure.NONE;
        };
    }

    private static FirstFailure migrationFailure(PayloadMigrationFailure failure) {
        return failure.code() == PayloadMigrationFailure.Code.FUTURE_SCHEMA_VERSION
                ? FirstFailure.FUTURE_SCHEMA : FirstFailure.MIGRATION_FAILED;
    }

    private static FirstFailure semanticFailure(ValidationResult result) {
        return result.hasErrors() ? FirstFailure.SEMANTIC_INVALID : FirstFailure.NONE;
    }

    private static FirstFailure first(FirstFailure current, FirstFailure next) {
        return current == FirstFailure.NONE ? next : current;
    }

    /** Typed first-fatal fact; independent of bounded issue retention. */
    public enum FirstFailure {
        NONE, UNKNOWN_TYPE, FUTURE_SCHEMA, MIGRATION_FAILED, DECODE_FAILED, SEMANTIC_INVALID
    }

    public record OrderedValidationResult(SkillValidationAnalysis analysis, FirstFailure firstFailure) {
        public OrderedValidationResult {
            Objects.requireNonNull(analysis, "analysis");
            Objects.requireNonNull(firstFailure, "firstFailure");
        }
    }

    /**
     * Completion witness. Only this resolver can construct it, after each authorized envelope
     * in the first two nodes completes its semantic stage. The cross-package analyzer cannot
     * use this handoff to skip that validation or lose the original whole-shape node count.
     */
    public static final class OrderedSemanticHandoff {
        private final ResolvedSkillCandidate candidate;
        private final ValidationContext context;
        private final ValidationCollector diagnostics;
        private final FirstFailure firstFailure;
        private final int originalNodeCount;

        private OrderedSemanticHandoff(ResolvedSkillCandidate candidate, ValidationContext context,
                ValidationCollector diagnostics, FirstFailure firstFailure, int originalNodeCount) {
            this.candidate = candidate;
            this.context = context;
            this.diagnostics = diagnostics;
            this.firstFailure = firstFailure;
            this.originalNodeCount = originalNodeCount;
        }

        public ResolvedSkillCandidate candidate() { return candidate; }
        public ValidationContext context() { return context; }
        public boolean ownsDiagnostics(ValidationCollector collector) { return diagnostics == collector; }
        public FirstFailure firstFailure() { return firstFailure; }
        public int originalNodeCount() { return originalNodeCount; }
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
                    resolveTrigger(node.trigger(), nodeIndex, facts, false),
                    resolveAction(node.action(), nodeIndex, facts, false),
                    node.appearanceOverride()));
        }
        return new ResolvedSkillCandidate(
                document.schemaVersion(),
                new SkillReference(document.skillId(), document.revision()),
                nodes,
                document.appearance(),
                readReport,
                facts.report());
    }

    private TriggerResolution resolveTrigger(
            DefinitionEnvelope source,
            int nodeIndex,
            PipelineFactCollector facts,
            boolean propagateUnexpected) {
        var descriptor = triggerLookup.find(source.typeId());
        if (descriptor.isEmpty()) {
            return new TriggerResolution.Unknown(new UnknownTriggerDefinition(
                    source,
                    DefinitionFailure.of(
                            DefinitionFailure.Code.UNKNOWN_TYPE,
                            "Unknown trigger type: " + source.typeId())));
        }
        return resolveTriggerWithDescriptor(
                source, descriptor.orElseThrow(), nodeIndex, facts, propagateUnexpected);
    }

    private <P extends TriggerPayload> TriggerResolution resolveTriggerWithDescriptor(
            DefinitionEnvelope source,
            TriggerType<P> descriptor,
            int nodeIndex,
            PipelineFactCollector facts,
            boolean propagateUnexpected) {
        var currentVersion = descriptor.currentPayloadSchemaVersion();
        if (propagateUnexpected && currentVersion < 0) {
            throw new IllegalArgumentException("currentSchemaVersion must be non-negative");
        }
        if (propagateUnexpected && source.schemaVersion() > currentVersion) {
            return new TriggerResolution.MigrationFailed(source, descriptor,
                    PayloadMigrationFailure.future(source.schemaVersion()));
        }
        var migrated = PayloadMigrator.migrate(
                source,
                currentVersion,
                Objects.requireNonNull(descriptor.payloadMigrationPlan(), "payloadMigrationPlan"),
                nodeIndex,
                facts, propagateUnexpected);
        if (migrated instanceof PayloadMigrator.Result.Failure failed) {
            return new TriggerResolution.MigrationFailed(source, descriptor, failed.failure());
        }

        var transientEnvelope = ((PayloadMigrator.Result.Success) migrated).transientEnvelope();
        var decoded = propagateUnexpected
                ? TriggerDefinitionCodec.decodeWithDescriptorPropagating(transientEnvelope, descriptor)
                : TriggerDefinitionCodec.decodeWithDescriptor(transientEnvelope, descriptor);
        if (decoded instanceof ResolvedTriggerDefinition<?> resolved) {
            return new TriggerResolution.Resolved<>(source, resolved);
        }
        var unknown = (UnknownTriggerDefinition) decoded;
        return new TriggerResolution.DecodeFailed(source, descriptor, unknown.failure());
    }

    private ActionResolution resolveAction(
            DefinitionEnvelope source,
            int nodeIndex,
            PipelineFactCollector facts,
            boolean propagateUnexpected) {
        var descriptor = actionLookup.find(source.typeId());
        if (descriptor.isEmpty()) {
            return new ActionResolution.Unknown(new UnknownActionDefinition(
                    source,
                    DefinitionFailure.of(
                            DefinitionFailure.Code.UNKNOWN_TYPE,
                            "Unknown action type: " + source.typeId())));
        }
        return resolveActionWithDescriptor(
                source, descriptor.orElseThrow(), nodeIndex, facts, propagateUnexpected);
    }

    private <P extends ActionPayload> ActionResolution resolveActionWithDescriptor(
            DefinitionEnvelope source,
            ActionType<P> descriptor,
            int nodeIndex,
            PipelineFactCollector facts,
            boolean propagateUnexpected) {
        var currentVersion = descriptor.currentPayloadSchemaVersion();
        if (propagateUnexpected && currentVersion < 0) {
            throw new IllegalArgumentException("currentSchemaVersion must be non-negative");
        }
        if (propagateUnexpected && source.schemaVersion() > currentVersion) {
            return new ActionResolution.MigrationFailed(source, descriptor,
                    PayloadMigrationFailure.future(source.schemaVersion()));
        }
        var migrated = PayloadMigrator.migrate(
                source,
                currentVersion,
                Objects.requireNonNull(descriptor.payloadMigrationPlan(), "payloadMigrationPlan"),
                nodeIndex,
                facts, propagateUnexpected);
        if (migrated instanceof PayloadMigrator.Result.Failure failed) {
            return new ActionResolution.MigrationFailed(source, descriptor, failed.failure());
        }

        var transientEnvelope = ((PayloadMigrator.Result.Success) migrated).transientEnvelope();
        var decoded = propagateUnexpected
                ? ActionDefinitionCodec.decodeWithDescriptorPropagating(transientEnvelope, descriptor)
                : ActionDefinitionCodec.decodeWithDescriptor(transientEnvelope, descriptor);
        if (decoded instanceof ResolvedActionDefinition<?> resolved) {
            return new ActionResolution.Resolved<>(source, resolved);
        }
        var unknown = (UnknownActionDefinition) decoded;
        return new ActionResolution.DecodeFailed(source, descriptor, unknown.failure());
    }
}
