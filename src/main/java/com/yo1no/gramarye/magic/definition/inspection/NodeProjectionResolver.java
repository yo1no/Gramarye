package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** The single P3-B3-B seam that invokes typed inspectors without validation or re-resolution. */
public final class NodeProjectionResolver {
    public InspectedSkillCandidate inspect(ResolvedSkillCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        var projections = new ArrayList<NodeReferenceProjection>(candidate.nodes().size());
        for (var node : candidate.nodes()) {
            projections.add(new NodeReferenceProjection(
                    node.nodeIndex(),
                    inspectTrigger(node.trigger()),
                    inspectAction(node.action())));
        }
        return new InspectedSkillCandidate(candidate, projections);
    }

    private static TriggerInspectionState inspectTrigger(TriggerResolution resolution) {
        if (resolution instanceof TriggerResolution.Resolved<?> resolved) {
            return inspectResolvedTrigger(resolved.definition());
        }
        return TriggerInspectionState.NotResolved.INSTANCE;
    }

    private static <P extends TriggerPayload> TriggerInspectionState inspectResolvedTrigger(
            ResolvedTriggerDefinition<P> definition) {
        Optional<TriggerPayloadInspector<P>> inspector;
        try {
            inspector = definition.descriptor().payloadInspector();
        } catch (RuntimeException exception) {
            return new TriggerInspectionState.Failed(exceptionFailure(exception));
        }
        if (inspector == null) {
            return triggerContractViolation(ValidationIssueMetadata.none());
        }
        if (inspector.isEmpty()) {
            return TriggerInspectionState.InspectorMissing.INSTANCE;
        }
        try {
            var result = inspector.orElseThrow().inspect(definition.payload());
            if (result == null) {
                return triggerContractViolation(ValidationIssueMetadata.none());
            }
            return toTriggerState(result);
        } catch (InspectionContractViolationException exception) {
            return triggerContractViolation(exception.metadata());
        } catch (RuntimeException exception) {
            return new TriggerInspectionState.Failed(exceptionFailure(exception));
        }
    }

    private static TriggerInspectionState toTriggerState(
            PayloadInspectionResult<TriggerReferenceProjection> result) {
        return switch (result) {
            case PayloadInspectionResult.Success(var projection) -> {
                var failure = pathBudgetFailure(projection.references());
                yield failure == null
                        ? new TriggerInspectionState.Success(projection)
                        : new TriggerInspectionState.Failed(failure);
            }
            case PayloadInspectionResult.Failure(var failure) ->
                    new TriggerInspectionState.Failed(failure);
        };
    }

    private static ActionInspectionState inspectAction(ActionResolution resolution) {
        if (resolution instanceof ActionResolution.Resolved<?> resolved) {
            return inspectResolvedAction(resolved.definition());
        }
        return ActionInspectionState.NotResolved.INSTANCE;
    }

    private static <P extends ActionPayload> ActionInspectionState inspectResolvedAction(
            ResolvedActionDefinition<P> definition) {
        Optional<ActionPayloadInspector<P>> inspector;
        try {
            inspector = definition.descriptor().payloadInspector();
        } catch (RuntimeException exception) {
            return new ActionInspectionState.Failed(exceptionFailure(exception));
        }
        if (inspector == null) {
            return actionContractViolation(ValidationIssueMetadata.none());
        }
        if (inspector.isEmpty()) {
            return ActionInspectionState.InspectorMissing.INSTANCE;
        }
        try {
            var result = inspector.orElseThrow().inspect(definition.payload());
            if (result == null) {
                return actionContractViolation(ValidationIssueMetadata.none());
            }
            return toActionState(result);
        } catch (InspectionContractViolationException exception) {
            return actionContractViolation(exception.metadata());
        } catch (RuntimeException exception) {
            return new ActionInspectionState.Failed(exceptionFailure(exception));
        }
    }

    private static ActionInspectionState toActionState(
            PayloadInspectionResult<ActionReferenceProjection> result) {
        return switch (result) {
            case PayloadInspectionResult.Success(var projection) -> {
                var failure = pathBudgetFailure(projection.references());
                yield failure == null
                        ? new ActionInspectionState.Success(projection)
                        : new ActionInspectionState.Failed(failure);
            }
            case PayloadInspectionResult.Failure(var failure) ->
                    new ActionInspectionState.Failed(failure);
        };
    }

    private static PayloadInspectionFailure pathBudgetFailure(
            Iterable<NodeReference> references) {
        for (var reference : references) {
            var path = reference.payloadPath();
            var segmentFailure = limitFailure(
                    path,
                    path.segments().size(),
                    MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS);
            if (segmentFailure != null) {
                return segmentFailure;
            }
            var renderFailure = limitFailure(
                    path,
                    path.render().length(),
                    MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH);
            if (renderFailure != null) {
                return renderFailure;
            }
        }
        return null;
    }

    private static PayloadInspectionFailure limitFailure(
            ValidationPath path,
            int actual,
            int maximum) {
        Objects.requireNonNull(path, "path");
        return actual <= maximum
                ? null
                : contractViolation(new ValidationIssueMetadata.Limit(actual, maximum));
    }

    private static TriggerInspectionState triggerContractViolation(
            ValidationIssueMetadata metadata) {
        return new TriggerInspectionState.Failed(contractViolation(metadata));
    }

    private static ActionInspectionState actionContractViolation(
            ValidationIssueMetadata metadata) {
        return new ActionInspectionState.Failed(contractViolation(metadata));
    }

    private static PayloadInspectionFailure contractViolation(
            ValidationIssueMetadata metadata) {
        return new PayloadInspectionFailure(
                PayloadInspectionFailureCodes.INSPECTOR_CONTRACT_VIOLATION,
                metadata);
    }

    private static PayloadInspectionFailure exceptionFailure(RuntimeException exception) {
        return new PayloadInspectionFailure(
                PayloadInspectionFailureCodes.INSPECTOR_EXCEPTION,
                ValidationIssueMetadata.ExceptionClass.from(exception.getClass()));
    }
}
