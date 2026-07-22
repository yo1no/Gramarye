package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.InspectedSkillCandidate;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Narrows one completed validation analysis into an admitted, provenance-free definition. */
public final class SkillDefinitionProjector {
    public SkillValidationOutcome project(SkillValidationAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        var report = analysis.report();
        if (report.hasErrors()) {
            return new SkillValidationOutcome.Rejected(report);
        }

        var candidate = analysis.sourceCandidate();
        var inspection = requireNoErrorInvariants(analysis, candidate);
        var nodes = projectNodes(candidate, inspection);
        var definition = new ValidatedSkillDefinition(
                candidate.skill(), nodes, projectAppearance(candidate.appearance()));
        return new SkillValidationOutcome.Accepted(definition, report);
    }

    private static InspectedSkillCandidate requireNoErrorInvariants(
            SkillValidationAnalysis analysis,
            ResolvedSkillCandidate candidate) {
        var inspection = analysis.inspection().orElseThrow(() -> new IllegalStateException(
                "no-error analysis must contain inspection results"));
        if (inspection.sourceCandidate() != candidate) {
            throw new IllegalStateException("inspection source candidate identity mismatch");
        }
        if (candidate.skillSchemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("no-error candidate must use the current skill schema");
        }
        if (candidate.nodes().isEmpty()) {
            throw new IllegalStateException("no-error candidate must contain at least one node");
        }
        if (candidate.nodes().size() != inspection.nodes().size()) {
            throw new IllegalStateException("candidate and inspection node counts differ");
        }
        for (var index = 0; index < candidate.nodes().size(); index++) {
            if (candidate.nodes().get(index).nodeIndex() != index) {
                throw new IllegalStateException("candidate nodeIndex discontinuity");
            }
        }
        for (var index = 0; index < inspection.nodes().size(); index++) {
            if (inspection.nodes().get(index).nodeIndex() != index) {
                throw new IllegalStateException("inspection nodeIndex discontinuity");
            }
        }
        for (var node : candidate.nodes()) {
            if (!(node.trigger() instanceof TriggerResolution.Resolved<?>)) {
                throw new IllegalStateException("no-error Trigger must be resolved");
            }
        }
        for (var node : candidate.nodes()) {
            if (!(node.action() instanceof ActionResolution.Resolved<?>)) {
                throw new IllegalStateException("no-error Action must be resolved");
            }
        }
        for (var node : inspection.nodes()) {
            if (!(node.trigger() instanceof TriggerInspectionState.Success)) {
                throw new IllegalStateException("no-error Trigger inspection must succeed");
            }
        }
        for (var node : inspection.nodes()) {
            if (!(node.action() instanceof ActionInspectionState.Success)) {
                throw new IllegalStateException("no-error Action inspection must succeed");
            }
        }
        return inspection;
    }

    private static List<ValidatedNodeDefinition> projectNodes(
            ResolvedSkillCandidate candidate,
            InspectedSkillCandidate inspection) {
        var projected = new ArrayList<ValidatedNodeDefinition>(candidate.nodes().size());
        for (var index = 0; index < candidate.nodes().size(); index++) {
            var source = candidate.nodes().get(index);
            var inspected = inspection.nodes().get(index);
            var trigger = resolvedTrigger(source.trigger());
            var action = resolvedAction(source.action());
            var triggerProjection = successfulTriggerInspection(inspected.trigger());
            var actionProjection = successfulActionInspection(inspected.action());
            projected.add(new ValidatedNodeDefinition(
                    index,
                    trigger,
                    action,
                    projectReferences(triggerProjection, actionProjection),
                    projectAppearanceOverride(source.appearanceOverride())));
        }
        return List.copyOf(projected);
    }

    private static ResolvedTriggerDefinition<?> resolvedTrigger(TriggerResolution resolution) {
        if (resolution instanceof TriggerResolution.Resolved<?> resolved) {
            return resolved.definition();
        }
        throw new IllegalStateException("no-error Trigger must be resolved");
    }

    private static ResolvedActionDefinition<?> resolvedAction(ActionResolution resolution) {
        if (resolution instanceof ActionResolution.Resolved<?> resolved) {
            return resolved.definition();
        }
        throw new IllegalStateException("no-error Action must be resolved");
    }

    private static TriggerReferenceProjection successfulTriggerInspection(
            TriggerInspectionState inspection) {
        if (inspection instanceof TriggerInspectionState.Success success) {
            return success.projection();
        }
        throw new IllegalStateException("no-error Trigger inspection must succeed");
    }

    private static ActionReferenceProjection successfulActionInspection(
            ActionInspectionState inspection) {
        if (inspection instanceof ActionInspectionState.Success success) {
            return success.projection();
        }
        throw new IllegalStateException("no-error Action inspection must succeed");
    }

    private static ValidatedNodeReferenceProjection projectReferences(
            TriggerReferenceProjection trigger,
            ActionReferenceProjection action) {
        return new ValidatedNodeReferenceProjection(
                new ValidatedTriggerReferenceProjection(
                        trigger.sourceSelection(),
                        trigger.targetSelection(),
                        trigger.providesCurrentTarget(),
                        projectReferences(trigger.references())),
                new ValidatedActionReferenceProjection(
                        action.sourceSelection(),
                        action.targetSelection(),
                        projectReferences(action.references()),
                        action.producedOutputs()));
    }

    private static List<ValidatedNodeReference> projectReferences(List<NodeReference> references) {
        var projected = new ArrayList<ValidatedNodeReference>(references.size());
        for (var reference : references) {
            projected.add(new ValidatedNodeReference(
                    reference.referencedNodeIndex(),
                    reference.role(),
                    reference.requiredOutputKind()));
        }
        return List.copyOf(projected);
    }

    private static RuntimeNeutralAppearance projectAppearance(AppearanceDocument appearance) {
        return switch (appearance) {
            case AppearanceDocument.Default ignored -> RuntimeNeutralAppearance.Default.INSTANCE;
            case AppearanceDocument.Decoded decoded ->
                    new RuntimeNeutralAppearance.Typed(decoded.definition());
            case AppearanceDocument.Unparsed ignored ->
                    new RuntimeNeutralAppearance.Fallback(AppearanceFallbackReason.UNPARSED);
            case AppearanceDocument.Rejected rejected -> new RuntimeNeutralAppearance.Fallback(
                    fallbackReason(rejected.reason()));
        };
    }

    private static RuntimeNeutralAppearanceOverride projectAppearanceOverride(
            AppearanceOverrideDocument appearance) {
        return switch (appearance) {
            case AppearanceOverrideDocument.None ignored ->
                    RuntimeNeutralAppearanceOverride.None.INSTANCE;
            case AppearanceOverrideDocument.Decoded decoded ->
                    new RuntimeNeutralAppearanceOverride.Typed(decoded.override());
            case AppearanceOverrideDocument.Unparsed ignored ->
                    new RuntimeNeutralAppearanceOverride.Fallback(AppearanceFallbackReason.UNPARSED);
            case AppearanceOverrideDocument.Rejected rejected ->
                    new RuntimeNeutralAppearanceOverride.Fallback(
                            fallbackReason(rejected.reason()));
        };
    }

    private static AppearanceFallbackReason fallbackReason(AppearanceRejectionCode reason) {
        return switch (reason) {
            case DEPTH_LIMIT_EXCEEDED -> AppearanceFallbackReason.REJECTED_DEPTH_LIMIT;
            case NODE_LIMIT_EXCEEDED -> AppearanceFallbackReason.REJECTED_NODE_LIMIT;
        };
    }
}
