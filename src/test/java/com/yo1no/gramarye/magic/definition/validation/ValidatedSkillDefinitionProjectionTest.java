package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ValidatedSkillDefinitionProjectionTest {
    @Test
    void projectorPreservesIdentityOrderDefinitionsAndPathFreeReferenceValues() {
        var trigger0Projection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
        var action0Projection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of(ActionOutputKind.PROJECTILE));
        var trigger1References = new NodeReference[] {
            reference(0, ReferenceRole.SOURCE, "source", Optional.empty()),
            reference(0, ReferenceRole.TARGET, "target", Optional.of(ActionOutputKind.PROJECTILE))
        };
        var action1References = new NodeReference[] {
            reference(0, ReferenceRole.CHAIN_SOURCE, "chain", Optional.empty()),
            reference(0, ReferenceRole.REPEAT_SOURCE, "repeat", Optional.empty())
        };
        var trigger1Projection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.PRIOR_OUTPUT,
                true,
                trigger1References);
        var outputs = new LinkedHashSet<ActionOutputKind>();
        outputs.add(ActionOutputKind.SCHEDULE);
        outputs.add(ActionOutputKind.EFFECT);
        outputs.add(ActionOutputKind.MARKER);
        var action1Projection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.CURRENT_TARGET,
                outputs,
                action1References);

        var trigger0 = SkillValidationTestFixtures.TriggerDescriptor.successful(trigger0Projection);
        var action0 = SkillValidationTestFixtures.ActionDescriptor.successful(action0Projection);
        var trigger1 = SkillValidationTestFixtures.TriggerDescriptor.successful(trigger1Projection);
        var action1 = SkillValidationTestFixtures.ActionDescriptor.successful(action1Projection);
        var node0 = SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger0),
                SkillValidationTestFixtures.resolvedAction(action0));
        var node1 = SkillValidationTestFixtures.node(
                1,
                SkillValidationTestFixtures.resolvedTrigger(trigger1),
                SkillValidationTestFixtures.resolvedAction(action1));
        var candidate = SkillValidationTestFixtures.candidate(node0, node1);
        var analysis = SkillValidationTestFixtures.analysis(
                candidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(trigger0Projection),
                        new ActionInspectionState.Success(action0Projection)),
                SkillValidationTestFixtures.inspectedNode(
                        1,
                        new TriggerInspectionState.Success(trigger1Projection),
                        new ActionInspectionState.Success(action1Projection)));

        var outcome = assertInstanceOf(
                SkillValidationOutcome.Accepted.class,
                new SkillDefinitionProjector().project(analysis));
        var definition = outcome.definition();
        var projectedNode = definition.nodes().get(1);
        var sourceTrigger = ((TriggerResolution.Resolved<?>) node1.trigger()).definition();
        var sourceAction = ((ActionResolution.Resolved<?>) node1.action()).definition();

        assertSame(candidate.skill(), definition.reference());
        assertEquals(List.of(0, 1), definition.nodes().stream()
                .map(ValidatedNodeDefinition::nodeIndex).toList());
        assertSame(sourceTrigger, projectedNode.trigger());
        assertSame(sourceAction, projectedNode.action());
        assertEquals(SourceSelection.PRIOR_NODE,
                projectedNode.references().trigger().sourceSelection());
        assertEquals(TargetSelection.PRIOR_OUTPUT,
                projectedNode.references().trigger().targetSelection());
        assertTrue(projectedNode.references().trigger().providesCurrentTarget());
        assertEquals(List.of(ReferenceRole.SOURCE, ReferenceRole.TARGET),
                projectedNode.references().trigger().references().stream()
                        .map(ValidatedNodeReference::role).toList());
        assertEquals(List.of(ActionOutputKind.EFFECT, ActionOutputKind.MARKER, ActionOutputKind.SCHEDULE),
                List.copyOf(projectedNode.references().action().producedOutputs()));
        assertEquals(List.of(ReferenceRole.CHAIN_SOURCE, ReferenceRole.REPEAT_SOURCE),
                projectedNode.references().action().references().stream()
                        .map(ValidatedNodeReference::role).toList());
        assertThrows(UnsupportedOperationException.class, () -> definition.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                projectedNode.references().trigger().references().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                projectedNode.references().action().producedOutputs().clear());

        consumeTrigger(projectedNode.trigger());
        consumeAction(projectedNode.action());
    }

    @Test
    void validatedReferenceConstructorEnforcesOnlyLocalProgrammingInvariants() {
        var arbitraryPriorMeaning = new ValidatedNodeReference(
                99, ReferenceRole.SPLIT_SOURCE, Optional.of(ActionOutputKind.CONSTRUCT));

        assertEquals(99, arbitraryPriorMeaning.referencedNodeIndex());
        assertEquals(ReferenceRole.SPLIT_SOURCE, arbitraryPriorMeaning.role());
        assertEquals(Optional.of(ActionOutputKind.CONSTRUCT),
                arbitraryPriorMeaning.requiredOutputKind());
        assertThrows(IllegalArgumentException.class, () ->
                new ValidatedNodeReference(-1, ReferenceRole.SOURCE, Optional.empty()));
        assertThrows(NullPointerException.class, () ->
                new ValidatedNodeReference(0, null, Optional.empty()));
        assertThrows(NullPointerException.class, () ->
                new ValidatedNodeReference(0, ReferenceRole.SOURCE, null));
    }

    @Test
    void controlledCollectionsDefensivelyCopyAndCoreConstructorsEnforceShape() {
        var triggerProjection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
        var actionProjection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of());
        var triggerDescriptor = SkillValidationTestFixtures.TriggerDescriptor.successful(
                triggerProjection);
        var actionDescriptor = SkillValidationTestFixtures.ActionDescriptor.successful(
                actionProjection);
        var candidate = SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(triggerDescriptor),
                SkillValidationTestFixtures.resolvedAction(actionDescriptor)));
        var trigger = ((TriggerResolution.Resolved<?>) candidate.nodes().getFirst().trigger())
                .definition();
        var action = ((ActionResolution.Resolved<?>) candidate.nodes().getFirst().action())
                .definition();
        var reference = new ValidatedNodeReference(
                0, ReferenceRole.SOURCE, Optional.empty());
        var triggerReferences = new ArrayList<>(List.of(reference));
        var actionReferences = new ArrayList<>(List.of(reference));
        var outputs = EnumSet.of(ActionOutputKind.EFFECT);
        var projectedReferences = new ValidatedNodeReferenceProjection(
                new ValidatedTriggerReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        triggerReferences),
                new ValidatedActionReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        actionReferences,
                        outputs));
        var node = new ValidatedNodeDefinition(
                0,
                trigger,
                action,
                projectedReferences,
                RuntimeNeutralAppearanceOverride.None.INSTANCE);
        var nodes = new ArrayList<>(List.of(node));
        var definition = new ValidatedSkillDefinition(
                candidate.skill(), nodes, RuntimeNeutralAppearance.Default.INSTANCE);

        triggerReferences.clear();
        actionReferences.clear();
        outputs.add(ActionOutputKind.SCHEDULE);
        nodes.clear();

        assertEquals(1, definition.nodes().size());
        assertEquals(1, definition.nodes().getFirst().references().trigger().references().size());
        assertEquals(1, definition.nodes().getFirst().references().action().references().size());
        assertEquals(Set.of(ActionOutputKind.EFFECT),
                definition.nodes().getFirst().references().action().producedOutputs());
        assertThrows(IllegalArgumentException.class, () -> new ValidatedSkillDefinition(
                candidate.skill(), List.of(), RuntimeNeutralAppearance.Default.INSTANCE));
        var wrongIndex = new ValidatedNodeDefinition(
                1,
                trigger,
                action,
                projectedReferences,
                RuntimeNeutralAppearanceOverride.None.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> new ValidatedSkillDefinition(
                candidate.skill(), List.of(wrongIndex), RuntimeNeutralAppearance.Default.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> new ValidatedNodeDefinition(
                -1,
                trigger,
                action,
                projectedReferences,
                RuntimeNeutralAppearanceOverride.None.INSTANCE));
    }

    @Test
    void controlledModelHasPackageConstructorsAndNoPersistenceRuntimeOrProvenanceApi() {
        var controlledTypes = List.of(
                ValidatedSkillDefinition.class,
                ValidatedNodeDefinition.class,
                ValidatedNodeReferenceProjection.class,
                ValidatedTriggerReferenceProjection.class,
                ValidatedActionReferenceProjection.class,
                ValidatedNodeReference.class);
        for (var type : controlledTypes) {
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        }

        var forbiddenFragments = List.of(
                "DefinitionEnvelope",
                "Dynamic",
                "ValidationPath",
                "ValidationResult",
                "SkillDocument",
                "ResolvedSkillCandidate",
                "InspectedSkillCandidate",
                "TriggerResolution",
                "ActionResolution",
                "TriggerInspectionState",
                "ActionInspectionState",
                "Unknown",
                "RawSnapshot");
        for (var type : controlledTypes) {
            var api = Arrays.stream(type.getDeclaredMethods())
                    .map(method -> method.getName() + method.getGenericReturnType())
                    .collect(Collectors.joining("\n"));
            var fields = Arrays.stream(type.getDeclaredFields())
                    .map(field -> field.getName() + field.getGenericType())
                    .collect(Collectors.joining("\n"));
            for (var forbidden : forbiddenFragments) {
                assertFalse(api.contains(forbidden), type + " API contains " + forbidden);
                assertFalse(fields.contains(forbidden), type + " field contains " + forbidden);
            }
        }

        var definitionMethods = Arrays.stream(ValidatedSkillDefinition.class.getDeclaredMethods())
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("reference", "nodes", "appearance"), definitionMethods);
        var nodeMethods = Arrays.stream(ValidatedNodeDefinition.class.getDeclaredMethods())
                .map(method -> method.getName()).collect(Collectors.toSet());
        assertEquals(Set.of("nodeIndex", "trigger", "action", "references", "appearanceOverride"),
                nodeMethods);
    }

    @Test
    void resolvedDefinitionsRemainTypedAndRawProvenanceFree() {
        assertRawFreeShape(
                ResolvedTriggerDefinition.class,
                Set.of("descriptor", "schemaVersion", "payload"));
        assertRawFreeShape(
                ResolvedActionDefinition.class,
                Set.of("descriptor", "schemaVersion", "payload"));
    }

    private static NodeReference reference(
            int index,
            ReferenceRole role,
            String path,
            Optional<ActionOutputKind> output) {
        return new NodeReference(index, role, ValidationPath.empty().field(path), output);
    }

    private static void assertRawFreeShape(Class<?> type, Set<String> expectedFields) {
        assertEquals(expectedFields, Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName()).collect(Collectors.toSet()));
        var typeNames = Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getGenericType().getTypeName())
                .collect(Collectors.joining("\n"));
        assertFalse(typeNames.contains("DefinitionEnvelope"));
        assertFalse(typeNames.contains("Dynamic"));
        assertFalse(typeNames.contains("Unknown"));
    }

    private static void consumeTrigger(ResolvedTriggerDefinition<?> definition) {
        consumeTriggerCaptured(definition);
    }

    private static <P extends TriggerPayload> void consumeTriggerCaptured(
            ResolvedTriggerDefinition<P> definition) {
        TriggerType<P> descriptor = definition.descriptor();
        P payload = definition.payload();
        assertSame(descriptor, definition.descriptor());
        assertSame(payload, definition.payload());
    }

    private static void consumeAction(ResolvedActionDefinition<?> definition) {
        consumeActionCaptured(definition);
    }

    private static <P extends ActionPayload> void consumeActionCaptured(
            ResolvedActionDefinition<P> definition) {
        ActionType<P> descriptor = definition.descriptor();
        P payload = definition.payload();
        assertSame(descriptor, definition.descriptor());
        assertSame(payload, definition.payload());
    }
}
