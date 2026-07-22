package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.InspectedSkillCandidate;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationFailure;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic, bounded and non-persistent P3-B3-C skill validation orchestrator. */
public final class SkillValidationAnalyzer {
    private static final ValidationPath DOCUMENT_ROOT = ValidationPath.empty();
    private static final ValidationPath SCHEMA_VERSION_PATH = DOCUMENT_ROOT.field("schema_version");
    private static final ValidationPath NODES_PATH = DOCUMENT_ROOT.field("nodes");
    private static final ValidationPath APPEARANCE_PATH = DOCUMENT_ROOT.field("appearance");

    private final NodeProjectionResolver projectionResolver;
    private final ProfileAvailabilityView profileAvailability;

    public SkillValidationAnalyzer(
            NodeProjectionResolver projectionResolver,
            ProfileAvailabilityView profileAvailability) {
        this.projectionResolver = Objects.requireNonNull(projectionResolver, "projectionResolver");
        this.profileAvailability = Objects.requireNonNull(profileAvailability, "profileAvailability");
    }

    public SkillValidationAnalysis analyze(
            ResolvedSkillCandidate candidate,
            ValidationContext context) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        var collector = new ValidationCollector();

        if (candidate.skillSchemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
            error(
                    collector,
                    SkillValidationIssueCodes.SKILL_UNSUPPORTED_SCHEMA,
                    SCHEMA_VERSION_PATH,
                    new ValidationIssueMetadata.Schema(
                            candidate.skillSchemaVersion(), SkillDocument.CURRENT_SCHEMA_VERSION));
            return new SkillValidationAnalysis(candidate, Optional.empty(), collector.result());
        }

        var inspection = projectionResolver.inspect(candidate);
        verifyPairing(candidate, inspection);
        var states = createStates(candidate, inspection);

        mapReadReport(candidate, collector);
        // Pipeline facts remain provenance/debug data on the candidate and never become issues.
        validateGlobalStructure(candidate, context, collector);
        mapResolutionStates(states, collector);
        validateAppearance(candidate.appearance(), APPEARANCE_PATH, context, collector);
        for (var state : states) {
            validateAppearance(
                    state.candidate.appearanceOverride(),
                    appearanceOverridePath(state.nodeIndex()),
                    context,
                    collector);
        }
        mapInspectionStates(states, collector);
        snapshotCapabilities(states, collector);
        validateLocalReferences(states, collector);
        validateCapabilityConsistency(states, collector);
        runSemanticValidators(states, context, collector);
        validateCrossNodeReferences(states, collector);
        validateProfiles(candidate, states, collector);

        return new SkillValidationAnalysis(
                candidate, Optional.of(inspection), collector.result());
    }

    private static void verifyPairing(
            ResolvedSkillCandidate candidate,
            InspectedSkillCandidate inspection) {
        if (inspection.sourceCandidate() != candidate) {
            throw new IllegalStateException("inspection source candidate identity mismatch");
        }
        if (inspection.nodes().size() != candidate.nodes().size()) {
            throw new IllegalStateException("inspection node count mismatch");
        }
        for (var index = 0; index < inspection.nodes().size(); index++) {
            if (inspection.nodes().get(index).nodeIndex() != index) {
                throw new IllegalStateException("inspection nodeIndex discontinuity");
            }
        }
    }

    private static List<NodeState> createStates(
            ResolvedSkillCandidate candidate,
            InspectedSkillCandidate inspection) {
        var states = new ArrayList<NodeState>(candidate.nodes().size());
        for (var index = 0; index < candidate.nodes().size(); index++) {
            states.add(new NodeState(
                    candidate.nodes().get(index),
                    inspection.nodes().get(index).trigger(),
                    inspection.nodes().get(index).action()));
        }
        return List.copyOf(states);
    }

    private static void mapReadReport(
            ResolvedSkillCandidate candidate,
            ValidationCollector collector) {
        for (var fact : candidate.readReport().facts()) {
            warning(collector, readCode(fact.code()), readPath(fact), ValidationIssueMetadata.none());
        }
        if (candidate.readReport().truncated()) {
            warning(
                    collector,
                    SkillValidationIssueCodes.READ_REPORT_TRUNCATED,
                    DOCUMENT_ROOT,
                    ValidationIssueMetadata.none());
        }
    }

    private static ValidationIssueCode readCode(ReadFactCode code) {
        return switch (code) {
            case INTENSITY_CLAMPED_LOW -> SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_LOW;
            case INTENSITY_CLAMPED_HIGH -> SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH;
            case LEGACY_NULL_PROFILE_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_PROFILE_NORMALIZED;
            case LEGACY_NULL_SCALAR_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_SCALAR_NORMALIZED;
            case LEGACY_NULL_APPEARANCE_DEFAULTED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_APPEARANCE_DEFAULTED;
            case LEGACY_NULL_OVERRIDE_NORMALIZED ->
                    SkillValidationIssueCodes.READ_LEGACY_NULL_OVERRIDE_NORMALIZED;
            case UNKNOWN_APPEARANCE_FIELD_IGNORED ->
                    SkillValidationIssueCodes.READ_UNKNOWN_APPEARANCE_FIELD_IGNORED;
        };
    }

    private static ValidationPath readPath(ReadFact fact) {
        var path = switch (fact.locationKind()) {
            case SKILL_APPEARANCE -> {
                if (fact.nodeIndex().isPresent()) {
                    throw new IllegalStateException("skill appearance fact must not have nodeIndex");
                }
                yield APPEARANCE_PATH;
            }
            case SKILL_NODE_APPEARANCE_OVERRIDE -> {
                if (fact.nodeIndex().isEmpty()) {
                    throw new IllegalStateException("node appearance fact requires nodeIndex");
                }
                yield appearanceOverridePath(fact.nodeIndex().getAsInt());
            }
            case DRAFT_APPEARANCE, DRAFT_NODE_APPEARANCE_OVERRIDE ->
                    throw new IllegalStateException("draft read fact cannot appear in SkillDocument report");
        };
        return fact.field().map(field -> path.field(fieldSegment(field))).orElse(path);
    }

    private static String fieldSegment(AppearanceField field) {
        return switch (field) {
            case PRIMARY_ARGB -> "primary_argb";
            case SECONDARY_ARGB -> "secondary_argb";
            case SOUND_PROFILE -> "sound_profile";
            case PARTICLE_PROFILE -> "particle_profile";
            case TRAIL_PROFILE -> "trail_profile";
            case INTENSITY_MILLI -> "intensity_milli";
        };
    }

    private static void validateGlobalStructure(
            ResolvedSkillCandidate candidate,
            ValidationContext context,
            ValidationCollector collector) {
        if (candidate.nodes().isEmpty()) {
            error(
                    collector,
                    SkillValidationIssueCodes.SKILL_EMPTY_NODES,
                    NODES_PATH,
                    ValidationIssueMetadata.none());
        }
        var maximum = context.policyLimits().maxNodes();
        if (candidate.nodes().size() > maximum) {
            error(
                    collector,
                    SkillValidationIssueCodes.SKILL_NODE_COUNT_POLICY_EXCEEDED,
                    NODES_PATH,
                    new ValidationIssueMetadata.Limit(candidate.nodes().size(), maximum));
        }
    }

    private static void mapResolutionStates(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            mapTriggerResolution(state.trigger.resolution, triggerRoot(state.nodeIndex()), collector);
            mapActionResolution(state.action.resolution, actionRoot(state.nodeIndex()), collector);
        }
    }

    private static void mapTriggerResolution(
            TriggerResolution resolution,
            ValidationPath path,
            ValidationCollector collector) {
        switch (resolution) {
            case TriggerResolution.Resolved<?> ignored -> {
            }
            case TriggerResolution.Unknown ignored -> error(
                    collector,
                    SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE,
                    path,
                    ValidationIssueMetadata.none());
            case TriggerResolution.MigrationFailed failed -> error(
                    collector, migrationCode(failed.failure()), path, ValidationIssueMetadata.none());
            case TriggerResolution.DecodeFailed failed -> error(
                    collector, decodeCode(failed.failure().code()), path, ValidationIssueMetadata.none());
        }
    }

    private static void mapActionResolution(
            ActionResolution resolution,
            ValidationPath path,
            ValidationCollector collector) {
        switch (resolution) {
            case ActionResolution.Resolved<?> ignored -> {
            }
            case ActionResolution.Unknown ignored -> error(
                    collector,
                    SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE,
                    path,
                    ValidationIssueMetadata.none());
            case ActionResolution.MigrationFailed failed -> error(
                    collector, migrationCode(failed.failure()), path, ValidationIssueMetadata.none());
            case ActionResolution.DecodeFailed failed -> error(
                    collector, decodeCode(failed.failure().code()), path, ValidationIssueMetadata.none());
        }
    }

    private static ValidationIssueCode migrationCode(PayloadMigrationFailure failure) {
        return switch (failure.code()) {
            case FUTURE_SCHEMA_VERSION ->
                    SkillValidationIssueCodes.DEFINITION_PAYLOAD_SCHEMA_FUTURE;
            case MISSING_MIGRATION_EDGE ->
                    SkillValidationIssueCodes.DEFINITION_PAYLOAD_MIGRATION_MISSING_EDGE;
            case STEP_FAILED,
                    STEP_RETURNED_PARTIAL,
                    STEP_THREW_EXCEPTION,
                    STEP_CHANGED_DYNAMIC_OPS,
                    PAYLOAD_TREE_DEPTH_EXCEEDED,
                    PAYLOAD_TREE_NODE_LIMIT_EXCEEDED,
                    PAYLOAD_KEY_LENGTH_EXCEEDED ->
                    SkillValidationIssueCodes.DEFINITION_PAYLOAD_MIGRATION_FAILED;
        };
    }

    private static ValidationIssueCode decodeCode(
            com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure.Code code) {
        return switch (code) {
            case PAYLOAD_DECODE_ERROR -> SkillValidationIssueCodes.DEFINITION_PAYLOAD_DECODE_ERROR;
            case CODEC_EXCEPTION -> SkillValidationIssueCodes.DEFINITION_PAYLOAD_CODEC_EXCEPTION;
            case UNKNOWN_TYPE, UNSUPPORTED_SCHEMA_VERSION ->
                    throw new IllegalStateException("unexpected DecodeFailed definition code: " + code);
        };
    }

    private static void mapInspectionStates(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            mapTriggerInspection(
                    state.trigger.inspection, triggerPayloadRoot(state.nodeIndex()), collector);
            mapActionInspection(
                    state.action.inspection, actionPayloadRoot(state.nodeIndex()), collector);
        }
    }

    private static void mapTriggerInspection(
            TriggerInspectionState inspection,
            ValidationPath path,
            ValidationCollector collector) {
        switch (inspection) {
            case TriggerInspectionState.NotResolved ignored -> {
            }
            case TriggerInspectionState.InspectorMissing ignored -> error(
                    collector,
                    SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_MISSING,
                    path,
                    ValidationIssueMetadata.none());
            case TriggerInspectionState.Failed failed -> error(
                    collector, failed.failure().code(), path, failed.failure().metadata());
            case TriggerInspectionState.Success ignored -> {
            }
        }
    }

    private static void mapActionInspection(
            ActionInspectionState inspection,
            ValidationPath path,
            ValidationCollector collector) {
        switch (inspection) {
            case ActionInspectionState.NotResolved ignored -> {
            }
            case ActionInspectionState.InspectorMissing ignored -> error(
                    collector,
                    SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_MISSING,
                    path,
                    ValidationIssueMetadata.none());
            case ActionInspectionState.Failed failed -> error(
                    collector, failed.failure().code(), path, failed.failure().metadata());
            case ActionInspectionState.Success ignored -> {
            }
        }
    }

    private static void snapshotCapabilities(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            if (state.trigger.resolution instanceof TriggerResolution.Resolved<?> resolved) {
                try {
                    state.trigger.capabilities = resolved.definition().descriptor().capabilities();
                    if (state.trigger.capabilities == null) {
                        error(
                                collector,
                                SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_CONTRACT_VIOLATION,
                                triggerPayloadRoot(state.nodeIndex()),
                                ValidationIssueMetadata.none());
                    }
                } catch (RuntimeException exception) {
                    error(
                            collector,
                            SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_EXCEPTION,
                            triggerPayloadRoot(state.nodeIndex()),
                            ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
                }
            }
            if (state.action.resolution instanceof ActionResolution.Resolved<?> resolved) {
                try {
                    state.action.capabilities = resolved.definition().descriptor().capabilities();
                    if (state.action.capabilities == null) {
                        error(
                                collector,
                                SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_CONTRACT_VIOLATION,
                                actionPayloadRoot(state.nodeIndex()),
                                ValidationIssueMetadata.none());
                    }
                } catch (RuntimeException exception) {
                    error(
                            collector,
                            SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_EXCEPTION,
                            actionPayloadRoot(state.nodeIndex()),
                            ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
                }
            }
        }
    }

    private static void validateLocalReferences(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            if (state.trigger.inspection instanceof TriggerInspectionState.Success success) {
                state.trigger.local = validateReferences(
                        state.nodeIndex(),
                        triggerPayloadRoot(state.nodeIndex()),
                        success.projection().sourceSelection(),
                        success.projection().targetSelection(),
                        success.projection().references(),
                        collector);
            }
            if (state.nodeIndex() == 0
                    && state.trigger.capabilities != null
                    && state.trigger.capabilities.requiresContinuationState()) {
                error(
                        collector,
                        SkillValidationIssueCodes.TRIGGER_CONTINUATION_NOT_ALLOWED_ON_FIRST_NODE,
                        triggerPayloadRoot(0),
                        ValidationIssueMetadata.none());
            }
            if (state.action.inspection instanceof ActionInspectionState.Success success) {
                state.action.local = validateReferences(
                        state.nodeIndex(),
                        actionPayloadRoot(state.nodeIndex()),
                        success.projection().sourceSelection(),
                        success.projection().targetSelection(),
                        success.projection().references(),
                        collector);
            }
        }
    }

    private static LocalReferenceState validateReferences(
            int currentNode,
            ValidationPath payloadRoot,
            SourceSelection sourceSelection,
            TargetSelection targetSelection,
            List<NodeReference> references,
            ValidationCollector collector) {
        if (references.size() > MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE) {
            throw new IllegalStateException("inspector reference hard ceiling invariant violated");
        }
        if (currentNode == 0
                && (sourceSelection == SourceSelection.PRIOR_NODE
                        || targetSelection == TargetSelection.PRIOR_OUTPUT
                        || !references.isEmpty())) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_FIRST_NODE_PRIOR_DEPENDENCY,
                    payloadRoot,
                    ValidationIssueMetadata.none());
        }

        var semanticKeys = new LinkedHashSet<SemanticReferenceKey>();
        var eligible = new ArrayList<EligibleReference>(references.size());
        var declaredSource = false;
        var declaredTarget = false;
        for (var reference : references) {
            var prefixed = PayloadPathPrefixer.prefix(payloadRoot, reference.payloadPath());
            if (prefixed instanceof PayloadPathPrefixer.Result.Overflow overflow) {
                error(
                        collector,
                        SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_CONTRACT_VIOLATION,
                        payloadRoot,
                        overflow.metadata());
                continue;
            }
            var path = ((PayloadPathPrefixer.Result.Success) prefixed).path();
            declaredSource |= isSourceRole(reference.role());
            declaredTarget |= reference.role() == ReferenceRole.TARGET;

            var key = new SemanticReferenceKey(
                    reference.referencedNodeIndex(),
                    reference.role(),
                    reference.requiredOutputKind());
            if (!semanticKeys.add(key)) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_DUPLICATE,
                        path,
                        ValidationIssueMetadata.none());
                continue;
            }
            if (reference.referencedNodeIndex() < 0) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_NEGATIVE_INDEX,
                        path,
                        new ValidationIssueMetadata.Reference(
                                currentNode, reference.referencedNodeIndex()));
                continue;
            }
            if (reference.referencedNodeIndex() >= currentNode) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_NOT_PRIOR,
                        path,
                        new ValidationIssueMetadata.Reference(
                                currentNode, reference.referencedNodeIndex()));
                continue;
            }
            eligible.add(new EligibleReference(reference, path));
        }

        if (sourceSelection == SourceSelection.PRIOR_NODE && !declaredSource) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_PRIOR_SOURCE_MISSING,
                    payloadRoot,
                    ValidationIssueMetadata.none());
        } else if (sourceSelection != SourceSelection.PRIOR_NODE && declaredSource) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_UNEXPECTED_PRIOR_SOURCE,
                    payloadRoot,
                    ValidationIssueMetadata.none());
        }
        if (targetSelection == TargetSelection.PRIOR_OUTPUT && !declaredTarget) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_PRIOR_TARGET_MISSING,
                    payloadRoot,
                    ValidationIssueMetadata.none());
        } else if (targetSelection != TargetSelection.PRIOR_OUTPUT && declaredTarget) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_UNEXPECTED_PRIOR_TARGET,
                    payloadRoot,
                    ValidationIssueMetadata.none());
        }
        return new LocalReferenceState(declaredSource, declaredTarget, eligible);
    }

    private static boolean isSourceRole(ReferenceRole role) {
        return switch (role) {
            case SOURCE, SPLIT_SOURCE, CHAIN_SOURCE, REPEAT_SOURCE -> true;
            case TARGET -> false;
        };
    }

    private static void validateCapabilityConsistency(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            if (state.trigger.capabilities != null
                    && state.trigger.inspection instanceof TriggerInspectionState.Success success) {
                validateSourceCapability(
                        state.trigger.capabilities.sourceRequirement(),
                        success.projection().sourceSelection(),
                        state.trigger.local.declaredSource,
                        triggerPayloadRoot(state.nodeIndex()),
                        collector);
                validateTargetCapability(
                        state.trigger.capabilities.targetRequirement(),
                        success.projection().targetSelection(),
                        triggerPayloadRoot(state.nodeIndex()),
                        collector);
            }
            if (state.action.capabilities != null
                    && state.action.inspection instanceof ActionInspectionState.Success success) {
                var path = actionPayloadRoot(state.nodeIndex());
                validateSourceCapability(
                        state.action.capabilities.sourceRequirement(),
                        success.projection().sourceSelection(),
                        state.action.local.declaredSource,
                        path,
                        collector);
                validateTargetCapability(
                        state.action.capabilities.targetRequirement(),
                        success.projection().targetSelection(),
                        path,
                        collector);
                if (success.projection().targetSelection() == TargetSelection.SELF
                        && !state.action.capabilities.allowsSelfTarget()) {
                    error(
                            collector,
                            SkillValidationIssueCodes.CAPABILITY_SELF_TARGET_FORBIDDEN,
                            path,
                            ValidationIssueMetadata.none());
                }
                for (var output : ActionOutputKind.values()) {
                    if (success.projection().producedOutputs().contains(output)
                            && !state.action.capabilities.outputKinds().contains(output)) {
                        error(
                                collector,
                                SkillValidationIssueCodes.CAPABILITY_UNDECLARED_OUTPUT,
                                path,
                                ValidationIssueMetadata.none());
                    }
                }
            }
        }
    }

    private static void validateSourceCapability(
            SourceRequirement requirement,
            SourceSelection selection,
            boolean declaredSource,
            ValidationPath path,
            ValidationCollector collector) {
        switch (requirement) {
            case PRIOR_NODE -> {
                if (selection != SourceSelection.PRIOR_NODE || !declaredSource) {
                    error(
                            collector,
                            SkillValidationIssueCodes.CAPABILITY_REQUIRED_SOURCE_MISSING,
                            path,
                            ValidationIssueMetadata.none());
                }
            }
            case NONE -> {
                if (selection == SourceSelection.PRIOR_NODE || declaredSource) {
                    error(
                            collector,
                            SkillValidationIssueCodes.CAPABILITY_FORBIDDEN_PRIOR_SOURCE,
                            path,
                            ValidationIssueMetadata.none());
                }
            }
        }
    }

    private static void validateTargetCapability(
            TargetRequirement requirement,
            TargetSelection selection,
            ValidationPath path,
            ValidationCollector collector) {
        switch (requirement) {
            case NONE -> {
                if (selection != TargetSelection.NONE) {
                    error(
                            collector,
                            SkillValidationIssueCodes.CAPABILITY_UNEXPECTED_TARGET,
                            path,
                            ValidationIssueMetadata.none());
                }
            }
            case OPTIONAL -> {
            }
            case REQUIRED -> {
                if (selection == TargetSelection.NONE) {
                    error(
                            collector,
                            SkillValidationIssueCodes.CAPABILITY_REQUIRED_TARGET_MISSING,
                            path,
                            ValidationIssueMetadata.none());
                }
            }
        }
    }

    private static void runSemanticValidators(
            List<NodeState> states,
            ValidationContext context,
            ValidationCollector collector) {
        for (var state : states) {
            if (state.trigger.resolution instanceof TriggerResolution.Resolved<?> resolved) {
                validateTriggerDefinition(
                        resolved.definition(),
                        context,
                        triggerPayloadRoot(state.nodeIndex()),
                        collector);
            }
            if (state.action.resolution instanceof ActionResolution.Resolved<?> resolved) {
                validateActionDefinition(
                        resolved.definition(),
                        context,
                        actionPayloadRoot(state.nodeIndex()),
                        collector);
            }
        }
    }

    private static <P extends TriggerPayload> void validateTriggerDefinition(
            ResolvedTriggerDefinition<P> definition,
            ValidationContext context,
            ValidationPath payloadRoot,
            ValidationCollector collector) {
        ValidationResult result;
        try {
            result = definition.descriptor().validate(definition.payload(), context);
        } catch (RuntimeException exception) {
            error(
                    collector,
                    SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_EXCEPTION,
                    payloadRoot,
                    ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
            return;
        }
        addDescriptorResult(result, payloadRoot, collector);
    }

    private static <P extends ActionPayload> void validateActionDefinition(
            ResolvedActionDefinition<P> definition,
            ValidationContext context,
            ValidationPath payloadRoot,
            ValidationCollector collector) {
        ValidationResult result;
        try {
            result = definition.descriptor().validate(definition.payload(), context);
        } catch (RuntimeException exception) {
            error(
                    collector,
                    SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_EXCEPTION,
                    payloadRoot,
                    ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
            return;
        }
        addDescriptorResult(result, payloadRoot, collector);
    }

    private static void addDescriptorResult(
            ValidationResult result,
            ValidationPath payloadRoot,
            ValidationCollector collector) {
        if (result == null) {
            error(
                    collector,
                    SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_CONTRACT_VIOLATION,
                    payloadRoot,
                    ValidationIssueMetadata.none());
            return;
        }
        for (var issue : result.issues()) {
            var prefixed = PayloadPathPrefixer.prefix(payloadRoot, issue.path());
            if (prefixed instanceof PayloadPathPrefixer.Result.Success success) {
                collector.add(new ValidationIssue(
                        issue.code(), issue.severity(), success.path(), issue.metadata()));
            } else {
                var overflow = (PayloadPathPrefixer.Result.Overflow) prefixed;
                error(
                        collector,
                        SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_CONTRACT_VIOLATION,
                        payloadRoot,
                        overflow.metadata());
            }
        }
        collector.inheritReportState(result);
    }

    private static void validateCrossNodeReferences(
            List<NodeState> states,
            ValidationCollector collector) {
        for (var state : states) {
            validateEligibleReferences(state.trigger.local.eligible, states, collector);
            validateEligibleReferences(state.action.local.eligible, states, collector);
            validateCurrentTarget(state, collector);
        }
    }

    private static void validateEligibleReferences(
            List<EligibleReference> references,
            List<NodeState> states,
            ValidationCollector collector) {
        if (references.size() > MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE) {
            throw new IllegalStateException("eligible reference hard ceiling invariant violated");
        }
        for (var eligible : references) {
            var reference = eligible.reference;
            var producerIndex = reference.referencedNodeIndex();
            if (producerIndex < 0 || producerIndex >= states.size()) {
                throw new IllegalStateException("eligible reference producer index invariant violated");
            }
            var producer = states.get(producerIndex);
            if (!(producer.action.resolution instanceof ActionResolution.Resolved<?>)) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_PRODUCER_UNRESOLVED,
                        eligible.path,
                        ValidationIssueMetadata.none());
                continue;
            }
            if (!(producer.action.inspection instanceof ActionInspectionState.Success success)) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_PRODUCER_INSPECTION_UNAVAILABLE,
                        eligible.path,
                        ValidationIssueMetadata.none());
                continue;
            }
            if (reference.requiredOutputKind().isPresent()
                    && !success.projection().producedOutputs().contains(
                            reference.requiredOutputKind().orElseThrow())) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_REQUIRED_OUTPUT_MISSING,
                        eligible.path,
                        ValidationIssueMetadata.none());
            }
            if (producer.action.capabilities != null
                    && !producerSupportsRole(producer.action.capabilities, reference.role())) {
                error(
                        collector,
                        SkillValidationIssueCodes.REFERENCE_PRODUCER_ROLE_UNSUPPORTED,
                        eligible.path,
                        ValidationIssueMetadata.none());
            }
        }
    }

    private static boolean producerSupportsRole(
            ActionCapabilities capabilities,
            ReferenceRole role) {
        return switch (role) {
            case SOURCE, TARGET -> true;
            case SPLIT_SOURCE -> capabilities.splittableSource();
            case CHAIN_SOURCE -> capabilities.chainableSource();
            case REPEAT_SOURCE -> capabilities.repeatableSource();
        };
    }

    private static void validateCurrentTarget(
            NodeState state,
            ValidationCollector collector) {
        if (!(state.action.inspection instanceof ActionInspectionState.Success actionSuccess)
                || actionSuccess.projection().targetSelection() != TargetSelection.CURRENT_TARGET) {
            return;
        }
        if (!(state.trigger.resolution instanceof TriggerResolution.Resolved<?>)) {
            return;
        }
        if (!(state.trigger.inspection instanceof TriggerInspectionState.Success triggerSuccess)) {
            return;
        }
        if (!triggerSuccess.projection().providesCurrentTarget()) {
            error(
                    collector,
                    SkillValidationIssueCodes.REFERENCE_CURRENT_TARGET_UNAVAILABLE,
                    actionPayloadRoot(state.nodeIndex()),
                    ValidationIssueMetadata.none());
        }
    }

    private static void validateAppearance(
            AppearanceDocument appearance,
            ValidationPath path,
            ValidationContext context,
            ValidationCollector collector) {
        switch (appearance) {
            case AppearanceDocument.Default ignored -> {
            }
            case AppearanceDocument.Decoded ignored -> {
            }
            case AppearanceDocument.Unparsed unparsed -> validateUnparsedAppearance(
                    unparsed.copyRawAppearance(), path, context, collector);
            case AppearanceDocument.Rejected rejected ->
                    mapAppearanceRejection(rejected.reason(), path, collector);
        }
    }

    private static void validateAppearance(
            AppearanceOverrideDocument appearance,
            ValidationPath path,
            ValidationContext context,
            ValidationCollector collector) {
        switch (appearance) {
            case AppearanceOverrideDocument.None ignored -> {
            }
            case AppearanceOverrideDocument.Decoded ignored -> {
            }
            case AppearanceOverrideDocument.Unparsed unparsed -> validateUnparsedAppearance(
                    unparsed.copyRawAppearance(), path, context, collector);
            case AppearanceOverrideDocument.Rejected rejected ->
                    mapAppearanceRejection(rejected.reason(), path, collector);
        }
    }

    private static void validateUnparsedAppearance(
            com.mojang.serialization.Dynamic<?> raw,
            ValidationPath path,
            ValidationContext context,
            ValidationCollector collector) {
        warning(
                collector,
                SkillValidationIssueCodes.APPEARANCE_UNPARSED_FALLBACK,
                path,
                ValidationIssueMetadata.none());
        var depthResult = DynamicTreeBounds.check(
                raw,
                context.policyLimits().maxUnparsedAppearanceDepth(),
                MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_NODES);
        switch (depthResult) {
            case WITHIN_LIMITS -> {
            }
            case DEPTH_EXCEEDED -> warning(
                    collector,
                    SkillValidationIssueCodes.APPEARANCE_POLICY_DEPTH_EXCEEDED,
                    path,
                    ValidationIssueMetadata.none());
            case NODE_COUNT_EXCEEDED, KEY_LENGTH_EXCEEDED, UNSUPPORTED ->
                    throw new IllegalStateException(
                            "unparsed appearance snapshot violated hard snapshot invariants");
        }

        var nodeResult = DynamicTreeBounds.check(
                raw,
                MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_DEPTH,
                context.policyLimits().maxUnparsedAppearanceNodes());
        switch (nodeResult) {
            case WITHIN_LIMITS -> {
            }
            case NODE_COUNT_EXCEEDED -> warning(
                    collector,
                    SkillValidationIssueCodes.APPEARANCE_POLICY_NODE_COUNT_EXCEEDED,
                    path,
                    ValidationIssueMetadata.none());
            case DEPTH_EXCEEDED, KEY_LENGTH_EXCEEDED, UNSUPPORTED ->
                    throw new IllegalStateException(
                            "unparsed appearance snapshot violated hard snapshot invariants");
        }
    }

    private static void mapAppearanceRejection(
            AppearanceRejectionCode reason,
            ValidationPath path,
            ValidationCollector collector) {
        var code = switch (reason) {
            case DEPTH_LIMIT_EXCEEDED ->
                    SkillValidationIssueCodes.APPEARANCE_REJECTED_DEPTH_FALLBACK;
            case NODE_LIMIT_EXCEEDED ->
                    SkillValidationIssueCodes.APPEARANCE_REJECTED_NODE_LIMIT_FALLBACK;
        };
        warning(collector, code, path, ValidationIssueMetadata.none());
    }

    private void validateProfiles(
            ResolvedSkillCandidate candidate,
            List<NodeState> states,
            ValidationCollector collector) {
        if (candidate.appearance() instanceof AppearanceDocument.Decoded decoded) {
            validateProfiles(decoded.definition(), APPEARANCE_PATH, collector);
        }
        for (var state : states) {
            if (state.candidate.appearanceOverride()
                    instanceof AppearanceOverrideDocument.Decoded decoded) {
                validateProfiles(
                        decoded.override(),
                        appearanceOverridePath(state.nodeIndex()),
                        collector);
            }
        }
    }

    private void validateProfiles(
            AppearanceDefinition appearance,
            ValidationPath root,
            ValidationCollector collector) {
        validateProfile(appearance.soundProfile(), AppearanceField.SOUND_PROFILE, root, collector);
        validateProfile(
                appearance.particleProfile(), AppearanceField.PARTICLE_PROFILE, root, collector);
        validateProfile(appearance.trailProfile(), AppearanceField.TRAIL_PROFILE, root, collector);
    }

    private void validateProfiles(
            AppearanceOverride appearance,
            ValidationPath root,
            ValidationCollector collector) {
        validateProfile(appearance.soundProfile(), AppearanceField.SOUND_PROFILE, root, collector);
        validateProfile(
                appearance.particleProfile(), AppearanceField.PARTICLE_PROFILE, root, collector);
        validateProfile(appearance.trailProfile(), AppearanceField.TRAIL_PROFILE, root, collector);
    }

    private void validateProfile(
            ProfileSelection selection,
            AppearanceField field,
            ValidationPath root,
            ValidationCollector collector) {
        if (!(selection instanceof ProfileSelection.Specified specified)) {
            return;
        }
        var path = root.field(fieldSegment(field));
        ProfileAvailability availability;
        try {
            availability = profileAvailability.availability(field, specified.id());
        } catch (RuntimeException exception) {
            warning(
                    collector,
                    SkillValidationIssueCodes.APPEARANCE_PROFILE_AVAILABILITY_EXCEPTION,
                    path,
                    ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
            return;
        }
        if (availability == null) {
            warning(
                    collector,
                    SkillValidationIssueCodes.APPEARANCE_PROFILE_AVAILABILITY_EXCEPTION,
                    path,
                    ValidationIssueMetadata.none());
            return;
        }
        switch (availability) {
            case AVAILABLE, UNKNOWN -> {
            }
            case MISSING -> warning(
                    collector,
                    SkillValidationIssueCodes.APPEARANCE_PROFILE_MISSING,
                    path,
                    ValidationIssueMetadata.none());
        }
    }

    private static ValidationPath nodeRoot(int nodeIndex) {
        return NODES_PATH.index(nodeIndex);
    }

    private static ValidationPath triggerRoot(int nodeIndex) {
        return nodeRoot(nodeIndex).field("trigger");
    }

    private static ValidationPath actionRoot(int nodeIndex) {
        return nodeRoot(nodeIndex).field("action");
    }

    private static ValidationPath triggerPayloadRoot(int nodeIndex) {
        return triggerRoot(nodeIndex).field("payload");
    }

    private static ValidationPath actionPayloadRoot(int nodeIndex) {
        return actionRoot(nodeIndex).field("payload");
    }

    private static ValidationPath appearanceOverridePath(int nodeIndex) {
        return nodeRoot(nodeIndex).field("appearance_override");
    }

    private static void error(
            ValidationCollector collector,
            ValidationIssueCode code,
            ValidationPath path,
            ValidationIssueMetadata metadata) {
        collector.add(new ValidationIssue(code, ValidationSeverity.ERROR, path, metadata));
    }

    private static void warning(
            ValidationCollector collector,
            ValidationIssueCode code,
            ValidationPath path,
            ValidationIssueMetadata metadata) {
        collector.add(new ValidationIssue(code, ValidationSeverity.WARNING, path, metadata));
    }

    private static final class NodeState {
        private final ResolvedNodeCandidate candidate;
        private final TriggerSideState trigger;
        private final ActionSideState action;

        private NodeState(
                ResolvedNodeCandidate candidate,
                TriggerInspectionState triggerInspection,
                ActionInspectionState actionInspection) {
            this.candidate = Objects.requireNonNull(candidate, "candidate");
            trigger = new TriggerSideState(candidate.trigger(), triggerInspection);
            action = new ActionSideState(candidate.action(), actionInspection);
        }

        private int nodeIndex() {
            return candidate.nodeIndex();
        }
    }

    private static final class TriggerSideState {
        private final TriggerResolution resolution;
        private final TriggerInspectionState inspection;
        private TriggerCapabilities capabilities;
        private LocalReferenceState local = LocalReferenceState.empty();

        private TriggerSideState(
                TriggerResolution resolution,
                TriggerInspectionState inspection) {
            this.resolution = Objects.requireNonNull(resolution, "resolution");
            this.inspection = Objects.requireNonNull(inspection, "inspection");
        }
    }

    private static final class ActionSideState {
        private final ActionResolution resolution;
        private final ActionInspectionState inspection;
        private ActionCapabilities capabilities;
        private LocalReferenceState local = LocalReferenceState.empty();

        private ActionSideState(
                ActionResolution resolution,
                ActionInspectionState inspection) {
            this.resolution = Objects.requireNonNull(resolution, "resolution");
            this.inspection = Objects.requireNonNull(inspection, "inspection");
        }
    }

    private record LocalReferenceState(
            boolean declaredSource,
            boolean declaredTarget,
            List<EligibleReference> eligible) {
        private LocalReferenceState {
            eligible = List.copyOf(Objects.requireNonNull(eligible, "eligible"));
            if (eligible.size() > MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE) {
                throw new IllegalArgumentException("eligible references exceed hard ceiling");
            }
        }

        private static LocalReferenceState empty() {
            return new LocalReferenceState(false, false, List.of());
        }
    }

    private record EligibleReference(NodeReference reference, ValidationPath path) {
        private EligibleReference {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(path, "path");
        }
    }

    private record SemanticReferenceKey(
            int referencedNodeIndex,
            ReferenceRole role,
            Optional<ActionOutputKind> requiredOutputKind) {
        private SemanticReferenceKey {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(requiredOutputKind, "requiredOutputKind");
        }
    }
}
