package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.InspectedSkillCandidate;
import com.yo1no.gramarye.magic.definition.inspection.NodeReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillDefinitionProjectorInvariantTest {
    private final SkillDefinitionProjector projector = new SkillDefinitionProjector();

    @Test
    void analysisAndCandidateConstructorsOwnUnreachablePairingInvariants() {
        var first = candidateWithResolvedNode();
        var second = candidateWithResolvedNode();
        var secondInspection = successfulInspection(second);

        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationAnalysis(first, Optional.empty(), ValidationResult.valid()));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationAnalysis(first, Optional.of(secondInspection), ValidationResult.valid()));

        var future = SkillValidationTestFixtures.candidate(
                1,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationAnalysis(future, Optional.empty(), ValidationResult.valid()));
    }

    @Test
    void candidateAndInspectionConstructorsOwnCountAndIndexInvariants() {
        var candidate = candidateWithResolvedNode();
        var projection = successfulInspection(candidate).nodes().getFirst();

        assertThrows(IllegalArgumentException.class, () ->
                new InspectedSkillCandidate(candidate, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new InspectedSkillCandidate(
                candidate,
                List.of(new NodeReferenceProjection(1, projection.trigger(), projection.action()))));

        assertThrows(IllegalArgumentException.class, () -> SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        1,
                        candidate.nodes().getFirst().trigger(),
                        candidate.nodes().getFirst().action())));
    }

    @Test
    void noErrorEmptyCandidateIsProgrammingFailure() {
        var candidate = SkillValidationTestFixtures.candidate();
        var analysis = SkillValidationTestFixtures.analysis(candidate, ValidationResult.valid());

        assertThrows(IllegalStateException.class, () -> projector.project(analysis));
    }

    @Test
    void noErrorUnresolvedTriggerOrActionIsProgrammingFailure() {
        var successfulTrigger = triggerProjection();
        var successfulAction = actionProjection();
        var actionDescriptor = SkillValidationTestFixtures.ActionDescriptor.successful(successfulAction);
        var triggerDescriptor = SkillValidationTestFixtures.TriggerDescriptor.successful(successfulTrigger);

        var unknownTriggerCandidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.unknownTrigger(),
                        SkillValidationTestFixtures.resolvedAction(actionDescriptor)));
        var unknownTriggerAnalysis = SkillValidationTestFixtures.analysis(
                unknownTriggerCandidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        TriggerInspectionState.NotResolved.INSTANCE,
                        new ActionInspectionState.Success(successfulAction)));

        var unknownActionCandidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(triggerDescriptor),
                        SkillValidationTestFixtures.unknownAction()));
        var unknownActionAnalysis = SkillValidationTestFixtures.analysis(
                unknownActionCandidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(successfulTrigger),
                        ActionInspectionState.NotResolved.INSTANCE));

        assertThrows(IllegalStateException.class, () -> projector.project(unknownTriggerAnalysis));
        assertThrows(IllegalStateException.class, () -> projector.project(unknownActionAnalysis));
    }

    @Test
    void noErrorNonSuccessInspectionIsProgrammingFailure() {
        var candidate = candidateWithResolvedNode();
        var triggerProjection = triggerProjection();
        var actionProjection = actionProjection();
        var missingTrigger = SkillValidationTestFixtures.analysis(
                candidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        TriggerInspectionState.InspectorMissing.INSTANCE,
                        new ActionInspectionState.Success(actionProjection)));
        var missingAction = SkillValidationTestFixtures.analysis(
                candidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(triggerProjection),
                        ActionInspectionState.InspectorMissing.INSTANCE));

        assertThrows(IllegalStateException.class, () -> projector.project(missingTrigger));
        assertThrows(IllegalStateException.class, () -> projector.project(missingAction));
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate
            candidateWithResolvedNode() {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(triggerProjection());
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(actionProjection());
        return SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action)));
    }

    private static InspectedSkillCandidate successfulInspection(
            com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate candidate) {
        return SkillValidationTestFixtures.inspection(
                candidate,
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(triggerProjection()),
                        new ActionInspectionState.Success(actionProjection())));
    }

    private static com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection
            triggerProjection() {
        return SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
    }

    private static com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection
            actionProjection() {
        return SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of());
    }
}
