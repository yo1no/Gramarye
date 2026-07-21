package com.yo1no.gramarye.magic.definition.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationPathSegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class NodeProjectionResolverTest {
    private final NodeProjectionResolver resolver = new NodeProjectionResolver();

    @Test
    void typedInspectorsRunOnceInTriggerThenActionOrderWithoutCodecUse() {
        var order = new ArrayList<String>();
        var triggerCalls = new AtomicInteger();
        var actionCalls = new AtomicInteger();
        var triggerDescriptor = new InspectionTestFixtures.TriggerDescriptor(payload -> {
            order.add("trigger");
            triggerCalls.incrementAndGet();
            assertEquals(7, payload.sourceNode());
            return new PayloadInspectionResult.Success<>(triggerProjection(reference(
                    payload.sourceNode(), ReferenceRole.SOURCE, "source_node")));
        });
        var actionDescriptor = new InspectionTestFixtures.ActionDescriptor(payload -> {
            order.add("action");
            actionCalls.incrementAndGet();
            assertEquals(9, payload.sourceNode());
            return new PayloadInspectionResult.Success<>(actionProjection(
                    Set.of(ActionOutputKind.EFFECT),
                    reference(payload.sourceNode(), ReferenceRole.TARGET, "target_node")));
        });
        var candidate = InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                0,
                InspectionTestFixtures.resolvedTrigger(triggerDescriptor, 7),
                InspectionTestFixtures.resolvedAction(actionDescriptor, 9)));

        var inspected = resolver.inspect(candidate);
        var node = inspected.nodes().getFirst();

        assertInstanceOf(TriggerInspectionState.Success.class, node.trigger());
        assertInstanceOf(ActionInspectionState.Success.class, node.action());
        assertEquals(List.of("trigger", "action"), order);
        assertEquals(1, triggerCalls.get());
        assertEquals(1, actionCalls.get());
        assertEquals(0, triggerDescriptor.payloadCodecCalls());
        assertEquals(0, actionDescriptor.payloadCodecCalls());
        assertSame(candidate, inspected.sourceCandidate());
    }

    @Test
    void missingAndExplicitFailureStatesRemainDistinct() {
        var explicitFailure = new PayloadInspectionFailure(
                ValidationIssueCode.fromNamespaceAndPath("othermod", "payload.unsupported_shape"),
                new ValidationIssueMetadata.Schema(2, 1));
        var missingTrigger = InspectionTestFixtures.TriggerDescriptor.missingInspector();
        var failingAction = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Failure<>(explicitFailure));
        var node = resolver.inspect(InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.resolvedTrigger(missingTrigger, 0),
                        InspectionTestFixtures.resolvedAction(failingAction, 0))))
                .nodes().getFirst();

        assertInstanceOf(TriggerInspectionState.InspectorMissing.class, node.trigger());
        var failed = assertInstanceOf(ActionInspectionState.Failed.class, node.action());
        assertSame(explicitFailure, failed.failure());
    }

    @Test
    void runtimeExceptionIsBoundedAndDoesNotBlockOtherSide() {
        var trigger = new InspectionTestFixtures.TriggerDescriptor(payload -> {
            throw new IllegalStateException("unique-secret-inspector-message");
        });
        var actionCalls = new AtomicInteger();
        var action = new InspectionTestFixtures.ActionDescriptor(payload -> {
            actionCalls.incrementAndGet();
            return new PayloadInspectionResult.Success<>(actionProjection(Set.of()));
        });
        var node = resolver.inspect(InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.resolvedTrigger(trigger, 0),
                        InspectionTestFixtures.resolvedAction(action, 0))))
                .nodes().getFirst();

        var failed = assertInstanceOf(TriggerInspectionState.Failed.class, node.trigger());
        var exceptionMetadata = assertInstanceOf(
                ValidationIssueMetadata.ExceptionClass.class,
                failed.failure().metadata());
        assertEquals(PayloadInspectionFailureCodes.INSPECTOR_EXCEPTION, failed.failure().code());
        assertEquals(IllegalStateException.class.getName(), exceptionMetadata.className());
        assertFalse(failed.failure().toString().contains("unique-secret-inspector-message"));
        assertInstanceOf(ActionInspectionState.Success.class, node.action());
        assertEquals(1, actionCalls.get());
    }

    @Test
    void errorIsNotCaught() {
        var descriptor = new InspectionTestFixtures.TriggerDescriptor(payload -> {
            throw new AssertionError("must escape");
        });
        var candidate = InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                0,
                InspectionTestFixtures.resolvedTrigger(descriptor, 0),
                InspectionTestFixtures.unknownAction()));

        assertThrows(AssertionError.class, () -> resolver.inspect(candidate));
    }

    @Test
    void nullResultProjectionAndFailureBecomeContractViolations() {
        var nullResult = new InspectionTestFixtures.TriggerDescriptor(payload -> null);
        var nullProjection = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<TriggerReferenceProjection>(null));
        var nullReference = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(new TriggerReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.CURRENT_TARGET,
                        Arrays.asList(reference(0, ReferenceRole.SOURCE, "source"), null))));
        var nullFailure = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Failure<ActionReferenceProjection>(null));

        assertContractViolation(triggerState(nullResult));
        assertContractViolation(triggerState(nullProjection));
        assertContractViolation(triggerState(nullReference));
        assertContractViolation(actionState(nullFailure));
    }

    @Test
    void referenceHardCeilingAcceptsCapAndRejectsCapPlusOnePerSide() {
        var maximum = MagicSafetyCeilings.MAX_INSPECTED_REFERENCES_PER_SIDE;
        var exactReferences = references(maximum);
        var overReferences = references(maximum + 1);
        var triggerExact = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(new TriggerReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.CURRENT_TARGET,
                        exactReferences)));
        var triggerOver = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(new TriggerReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.CURRENT_TARGET,
                        overReferences)));
        var actionExact = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Success<>(new ActionReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.PRIOR_OUTPUT,
                        exactReferences,
                        Set.of(ActionOutputKind.EFFECT))));
        var actionOver = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Success<>(new ActionReferenceProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.PRIOR_OUTPUT,
                        overReferences,
                        Set.of(ActionOutputKind.EFFECT))));

        var triggerSuccess = assertInstanceOf(
                TriggerInspectionState.Success.class,
                triggerState(triggerExact));
        var triggerFailure = assertContractViolation(triggerState(triggerOver));
        var actionSuccess = assertInstanceOf(
                ActionInspectionState.Success.class,
                actionState(actionExact));
        var actionFailure = assertContractViolation(actionState(actionOver));

        assertEquals(maximum, triggerSuccess.projection().references().size());
        assertEquals(maximum, actionSuccess.projection().references().size());
        assertEquals(Set.of(ActionOutputKind.EFFECT), actionSuccess.projection().producedOutputs());
        var expectedLimit = new ValidationIssueMetadata.Limit(maximum + 1, maximum);
        assertEquals(expectedLimit, triggerFailure.metadata());
        assertEquals(expectedLimit, actionFailure.metadata());
    }

    @Test
    void descriptorAccessorEmptyNullAndRuntimeExceptionAreIsolated() {
        assertInstanceOf(
                TriggerInspectionState.InspectorMissing.class,
                triggerState(InspectionTestFixtures.TriggerDescriptor.missingInspector()));

        var nullTriggerAccessor = InspectionTestFixtures.TriggerDescriptor.withInspectorAccessor(
                () -> null);
        var nullActionAccessor = InspectionTestFixtures.ActionDescriptor.withInspectorAccessor(
                () -> null);
        assertContractViolation(triggerState(nullTriggerAccessor));
        assertContractViolation(actionState(nullActionAccessor));

        var throwingTriggerAccessor = InspectionTestFixtures.TriggerDescriptor.withInspectorAccessor(
                () -> {
                    throw new IllegalStateException("unique-secret-trigger-accessor-message");
                });
        var throwingActionAccessor = InspectionTestFixtures.ActionDescriptor.withInspectorAccessor(
                () -> {
                    throw new UnsupportedOperationException("unique-secret-action-accessor-message");
                });
        var triggerFailure = assertInspectorException(
                triggerState(throwingTriggerAccessor), IllegalStateException.class);
        var actionFailure = assertInspectorException(
                actionState(throwingActionAccessor), UnsupportedOperationException.class);

        assertFalse(triggerFailure.toString().contains("unique-secret-trigger-accessor-message"));
        assertFalse(actionFailure.toString().contains("unique-secret-action-accessor-message"));
    }

    @Test
    void descriptorAccessorErrorIsNotCaught() {
        var trigger = InspectionTestFixtures.TriggerDescriptor.withInspectorAccessor(() -> {
            throw new AssertionError("trigger accessor error must escape");
        });
        var action = InspectionTestFixtures.ActionDescriptor.withInspectorAccessor(() -> {
            throw new AssertionError("action accessor error must escape");
        });

        assertThrows(AssertionError.class, () -> triggerState(trigger));
        assertThrows(AssertionError.class, () -> actionState(action));
    }

    @Test
    void relativePathSegmentBudgetAcceptsExactAndRejectsOverLimit() {
        var exact = new ValidationPath(IntStream.range(
                        0, MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS)
                .mapToObj(ValidationPathSegment.Index::new)
                .map(ValidationPathSegment.class::cast)
                .toList());
        var over = new ValidationPath(IntStream.rangeClosed(
                        0, MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS)
                .mapToObj(ValidationPathSegment.Index::new)
                .map(ValidationPathSegment.class::cast)
                .toList());

        assertInstanceOf(TriggerInspectionState.Success.class, triggerState(pathInspector(exact)));
        var failure = assertContractViolation(triggerState(pathInspector(over)));
        assertEquals(
                new ValidationIssueMetadata.Limit(
                        MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS + 1,
                        MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_SEGMENTS),
                failure.metadata());
    }

    @Test
    void relativePathRenderBudgetAcceptsExactAndRejectsOverLimit() {
        var exact = ValidationPath.empty().field(
                "x".repeat(MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH));
        var over = ValidationPath.empty().field(
                "x".repeat(MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH + 1));

        assertInstanceOf(TriggerInspectionState.Success.class, triggerState(pathInspector(exact)));
        var failure = assertContractViolation(triggerState(pathInspector(over)));
        assertEquals(
                new ValidationIssueMetadata.Limit(
                        MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH + 1,
                        MagicSafetyCeilings.MAX_INSPECTOR_RELATIVE_PATH_RENDER_LENGTH),
                failure.metadata());
    }

    @Test
    void unknownMigrationAndDecodeFailuresAllMapToNotResolved() {
        var triggerDescriptor = InspectionTestFixtures.TriggerDescriptor.missingInspector();
        var actionDescriptor = InspectionTestFixtures.ActionDescriptor.missingInspector();
        var candidate = InspectionTestFixtures.candidate(
                InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.unknownTrigger(),
                        InspectionTestFixtures.unknownAction()),
                InspectionTestFixtures.node(
                        1,
                        InspectionTestFixtures.migrationFailedTrigger(triggerDescriptor),
                        InspectionTestFixtures.migrationFailedAction(actionDescriptor)),
                InspectionTestFixtures.node(
                        2,
                        InspectionTestFixtures.decodeFailedTrigger(triggerDescriptor),
                        InspectionTestFixtures.decodeFailedAction(actionDescriptor)));

        var inspected = resolver.inspect(candidate);

        assertTrue(inspected.nodes().stream()
                .allMatch(node -> node.trigger() instanceof TriggerInspectionState.NotResolved));
        assertTrue(inspected.nodes().stream()
                .allMatch(node -> node.action() instanceof ActionInspectionState.NotResolved));
    }

    @Test
    void mixedResolutionStatesNeverContaminateTheOtherSide() {
        var triggerSuccess = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(triggerProjection()));
        var actionSuccess = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Success<>(actionProjection(Set.of())));
        var triggerMissing = InspectionTestFixtures.TriggerDescriptor.missingInspector();
        var actionMissing = InspectionTestFixtures.ActionDescriptor.missingInspector();
        var candidate = InspectionTestFixtures.candidate(
                InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.unknownTrigger(),
                        InspectionTestFixtures.resolvedAction(actionSuccess, 0)),
                InspectionTestFixtures.node(
                        1,
                        InspectionTestFixtures.resolvedTrigger(triggerSuccess, 0),
                        InspectionTestFixtures.unknownAction()),
                InspectionTestFixtures.node(
                        2,
                        InspectionTestFixtures.resolvedTrigger(triggerSuccess, 0),
                        InspectionTestFixtures.migrationFailedAction(actionMissing)),
                InspectionTestFixtures.node(
                        3,
                        InspectionTestFixtures.resolvedTrigger(triggerMissing, 0),
                        InspectionTestFixtures.resolvedAction(actionSuccess, 0)),
                InspectionTestFixtures.node(
                        4,
                        InspectionTestFixtures.resolvedTrigger(triggerSuccess, 0),
                        InspectionTestFixtures.resolvedAction(actionMissing, 0)));

        var nodes = resolver.inspect(candidate).nodes();

        assertInstanceOf(TriggerInspectionState.NotResolved.class, nodes.get(0).trigger());
        assertInstanceOf(ActionInspectionState.Success.class, nodes.get(0).action());
        assertInstanceOf(TriggerInspectionState.Success.class, nodes.get(1).trigger());
        assertInstanceOf(ActionInspectionState.NotResolved.class, nodes.get(1).action());
        assertInstanceOf(TriggerInspectionState.Success.class, nodes.get(2).trigger());
        assertInstanceOf(ActionInspectionState.NotResolved.class, nodes.get(2).action());
        assertInstanceOf(TriggerInspectionState.InspectorMissing.class, nodes.get(3).trigger());
        assertInstanceOf(ActionInspectionState.Success.class, nodes.get(3).action());
        assertInstanceOf(TriggerInspectionState.Success.class, nodes.get(4).trigger());
        assertInstanceOf(ActionInspectionState.InspectorMissing.class, nodes.get(4).action());
    }

    @Test
    void inspectedCandidateKeepsSourcePairingAndImmutableContinuousNodes() {
        var candidate = InspectionTestFixtures.candidate(
                InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.unknownTrigger(),
                        InspectionTestFixtures.unknownAction()),
                InspectionTestFixtures.node(
                        1,
                        InspectionTestFixtures.unknownTrigger(),
                        InspectionTestFixtures.unknownAction()));

        var inspected = resolver.inspect(candidate);

        assertSame(candidate, inspected.sourceCandidate());
        assertEquals(candidate.nodes().size(), inspected.nodes().size());
        assertEquals(List.of(0, 1), inspected.nodes().stream()
                .map(NodeReferenceProjection::nodeIndex)
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> inspected.nodes().clear());
        assertFalse(hasMethodNamed(InspectedSkillCandidate.class, "codec"));
        assertFalse(hasMethodNamed(InspectedSkillCandidate.class, "write"));
        assertFalse(hasMethodNamed(InspectedSkillCandidate.class, "execute"));
    }

    @Test
    void independentRunsAreStructurallyDeterministic() {
        var trigger = new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(triggerProjection(reference(
                        payload.sourceNode(), ReferenceRole.SOURCE, "source_node"))));
        var action = new InspectionTestFixtures.ActionDescriptor(payload ->
                new PayloadInspectionResult.Success<>(actionProjection(
                        Set.of(ActionOutputKind.SCHEDULE, ActionOutputKind.EFFECT),
                        reference(payload.sourceNode(), ReferenceRole.REPEAT_SOURCE, "repeat_node"))));
        var candidate = InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                0,
                InspectionTestFixtures.resolvedTrigger(trigger, 0),
                InspectionTestFixtures.resolvedAction(action, 0)));

        var first = resolver.inspect(candidate);
        var second = resolver.inspect(candidate);

        assertEquals(first, second);
        var firstAction = assertInstanceOf(
                ActionInspectionState.Success.class,
                first.nodes().getFirst().action());
        var secondAction = assertInstanceOf(
                ActionInspectionState.Success.class,
                second.nodes().getFirst().action());
        assertEquals(
                List.copyOf(firstAction.projection().producedOutputs()),
                List.copyOf(secondAction.projection().producedOutputs()));
    }

    private TriggerInspectionState triggerState(
            InspectionTestFixtures.TriggerDescriptor descriptor) {
        return resolver.inspect(InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.resolvedTrigger(descriptor, 0),
                        InspectionTestFixtures.unknownAction())))
                .nodes().getFirst().trigger();
    }

    private ActionInspectionState actionState(
            InspectionTestFixtures.ActionDescriptor descriptor) {
        return resolver.inspect(InspectionTestFixtures.candidate(InspectionTestFixtures.node(
                        0,
                        InspectionTestFixtures.unknownTrigger(),
                        InspectionTestFixtures.resolvedAction(descriptor, 0))))
                .nodes().getFirst().action();
    }

    private static InspectionTestFixtures.TriggerDescriptor pathInspector(ValidationPath path) {
        return new InspectionTestFixtures.TriggerDescriptor(payload ->
                new PayloadInspectionResult.Success<>(triggerProjection(new NodeReference(
                        0,
                        ReferenceRole.SOURCE,
                        path,
                        Optional.empty()))));
    }

    private static PayloadInspectionFailure assertContractViolation(
            TriggerInspectionState state) {
        var failed = assertInstanceOf(TriggerInspectionState.Failed.class, state);
        assertEquals(
                PayloadInspectionFailureCodes.INSPECTOR_CONTRACT_VIOLATION,
                failed.failure().code());
        return failed.failure();
    }

    private static PayloadInspectionFailure assertContractViolation(
            ActionInspectionState state) {
        var failed = assertInstanceOf(ActionInspectionState.Failed.class, state);
        assertEquals(
                PayloadInspectionFailureCodes.INSPECTOR_CONTRACT_VIOLATION,
                failed.failure().code());
        return failed.failure();
    }

    private static PayloadInspectionFailure assertInspectorException(
            TriggerInspectionState state,
            Class<? extends RuntimeException> exceptionClass) {
        var failed = assertInstanceOf(TriggerInspectionState.Failed.class, state);
        return assertInspectorException(failed.failure(), exceptionClass);
    }

    private static PayloadInspectionFailure assertInspectorException(
            ActionInspectionState state,
            Class<? extends RuntimeException> exceptionClass) {
        var failed = assertInstanceOf(ActionInspectionState.Failed.class, state);
        return assertInspectorException(failed.failure(), exceptionClass);
    }

    private static PayloadInspectionFailure assertInspectorException(
            PayloadInspectionFailure failure,
            Class<? extends RuntimeException> exceptionClass) {
        assertEquals(PayloadInspectionFailureCodes.INSPECTOR_EXCEPTION, failure.code());
        var metadata = assertInstanceOf(
                ValidationIssueMetadata.ExceptionClass.class,
                failure.metadata());
        assertEquals(exceptionClass.getName(), metadata.className());
        return failure;
    }

    private static TriggerReferenceProjection triggerProjection(NodeReference... references) {
        return new TriggerReferenceProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.CURRENT_TARGET,
                List.of(references));
    }

    private static ActionReferenceProjection actionProjection(
            Set<ActionOutputKind> outputs,
            NodeReference... references) {
        return new ActionReferenceProjection(
                SourceSelection.PRIOR_NODE,
                TargetSelection.PRIOR_OUTPUT,
                List.of(references),
                outputs);
    }

    private static NodeReference reference(int index, ReferenceRole role, String field) {
        return new NodeReference(
                index,
                role,
                ValidationPath.empty().field(field),
                Optional.empty());
    }

    private static List<NodeReference> references(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> reference(index, ReferenceRole.SOURCE, "reference"))
                .toList();
    }

    private static boolean hasMethodNamed(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equals(name));
    }
}
