package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillValidationAnalyzerReferenceTest {
    private static final ValidationContext CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void negativeEqualAndForwardIndicesAreRejectedButValidPriorIsEligible() {
        var producer = neutralNode(0);
        var trigger = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        reference(-1, ReferenceRole.SOURCE, "negative"),
                        reference(1, ReferenceRole.SPLIT_SOURCE, "equal"),
                        reference(2, ReferenceRole.CHAIN_SOURCE, "forward"),
                        reference(0, ReferenceRole.REPEAT_SOURCE, "valid")),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);
        var candidate = SkillValidationTestFixtures.candidate(
                producer,
                SkillValidationTestFixtures.node(
                        1,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(neutralActionDescriptor())));

        var analysis = analyzer().analyze(candidate, CONTEXT);

        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_NEGATIVE_INDEX));
        assertEquals(2, count(analysis, SkillValidationIssueCodes.REFERENCE_NOT_PRIOR));
        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_PRODUCER_ROLE_UNSUPPORTED));
        assertFalse(has(analysis, SkillValidationIssueCodes.REFERENCE_PRIOR_SOURCE_MISSING));
    }

    @Test
    void duplicateSemanticKeyIgnoresPayloadPathAndOnlyFirstReachesCrossNode() {
        var producerAction = actionDescriptor(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        Set.of(ActionOutputKind.EFFECT)),
                SkillValidationTestFixtures.actionCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.OPTIONAL,
                        true,
                        Set.of(ActionOutputKind.EFFECT),
                        false,
                        false,
                        false));
        var producer = node(0, neutralTriggerDescriptor(true), producerAction);
        var first = new NodeReference(
                0,
                ReferenceRole.SOURCE,
                ValidationPath.empty().field("first"),
                Optional.of(ActionOutputKind.EFFECT));
        var duplicate = new NodeReference(
                0,
                ReferenceRole.SOURCE,
                ValidationPath.empty().field("second"),
                Optional.of(ActionOutputKind.EFFECT));
        var consumer = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        first,
                        duplicate),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);

        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        producer,
                        node(1, consumer, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_DUPLICATE));
        var duplicateIssue = analysis.report().issues().stream()
                .filter(issue -> issue.code().equals(SkillValidationIssueCodes.REFERENCE_DUPLICATE))
                .findFirst().orElseThrow();
        assertEquals("nodes[1].trigger.payload.second", duplicateIssue.path().render());
        assertFalse(has(analysis, SkillValidationIssueCodes.REFERENCE_REQUIRED_OUTPUT_MISSING));
    }

    @Test
    void invalidIndicesStillCountAsDeclaredSelectionReferences() {
        var trigger = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.PRIOR_OUTPUT,
                        false,
                        reference(1, ReferenceRole.SOURCE, "source"),
                        reference(1, ReferenceRole.TARGET, "target")),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.REQUIRED,
                false);
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        neutralNode(0), node(1, trigger, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(2, count(analysis, SkillValidationIssueCodes.REFERENCE_NOT_PRIOR));
        assertFalse(has(analysis, SkillValidationIssueCodes.REFERENCE_PRIOR_SOURCE_MISSING));
        assertFalse(has(analysis, SkillValidationIssueCodes.REFERENCE_PRIOR_TARGET_MISSING));
        assertFalse(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_SOURCE_MISSING));
        assertFalse(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_TARGET_MISSING));
    }

    @Test
    void unexpectedDeclaredRolesAreReportedEvenWhenIndexIsInvalid() {
        var trigger = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        false,
                        reference(-1, ReferenceRole.SOURCE, "source"),
                        reference(-1, ReferenceRole.TARGET, "target")),
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false);
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(neutralNode(0),
                        node(1, trigger, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(2, count(analysis, SkillValidationIssueCodes.REFERENCE_NEGATIVE_INDEX));
        assertTrue(has(analysis, SkillValidationIssueCodes.REFERENCE_UNEXPECTED_PRIOR_SOURCE));
        assertTrue(has(analysis, SkillValidationIssueCodes.REFERENCE_UNEXPECTED_PRIOR_TARGET));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_FORBIDDEN_PRIOR_SOURCE));
    }

    @Test
    void firstNodePriorReferenceProducesSingleCascadeAndNoFalseMissingSource() {
        var trigger = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        reference(0, ReferenceRole.SOURCE, "source")),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);

        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(0, trigger, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(1, count(
                analysis, SkillValidationIssueCodes.REFERENCE_FIRST_NODE_PRIOR_DEPENDENCY));
        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_NOT_PRIOR));
        assertFalse(has(analysis, SkillValidationIssueCodes.REFERENCE_PRIOR_SOURCE_MISSING));
    }

    @Test
    void firstNodePriorSelectionWithoutReferenceReportsMissingSource() {
        var trigger = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE, TargetSelection.NONE, false),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);

        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(0, trigger, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(1, count(
                analysis, SkillValidationIssueCodes.REFERENCE_FIRST_NODE_PRIOR_DEPENDENCY));
        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_PRIOR_SOURCE_MISSING));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_SOURCE_MISSING));
    }

    @Test
    void continuationCapabilityIsCheckedEvenWhenInspectorIsMissing() {
        var trigger = new SkillValidationTestFixtures.TriggerDescriptor(
                Optional::empty,
                () -> SkillValidationTestFixtures.triggerCapabilities(
                        SourceRequirement.NONE, TargetRequirement.OPTIONAL, true),
                (payload, context) -> ValidationResult.valid());
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(0, trigger, neutralActionDescriptor())),
                CONTEXT);

        assertTrue(has(
                analysis,
                SkillValidationIssueCodes.TRIGGER_CONTINUATION_NOT_ALLOWED_ON_FIRST_NODE));
        assertTrue(has(analysis, SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_MISSING));
    }

    @Test
    void sourceAndTargetCapabilityMappingsAreExhaustive() {
        var priorRequired = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.REQUIRED,
                false);
        var forbiddenPrior = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE, TargetSelection.SELF, false),
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false);
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        node(0, priorRequired, neutralActionDescriptor()),
                        node(1, forbiddenPrior, neutralActionDescriptor())),
                CONTEXT);

        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_SOURCE_MISSING));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_TARGET_MISSING));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_FORBIDDEN_PRIOR_SOURCE));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_UNEXPECTED_TARGET));
        assertEquals(2, SourceRequirement.values().length);
        assertEquals(3, TargetRequirement.values().length);
    }

    @Test
    void optionalTargetsAreStructurallyAcceptedWhileSelfAndOutputsUseActionCapabilities() {
        for (var selection : TargetSelection.values()) {
            var action = actionDescriptor(
                    SkillValidationTestFixtures.actionProjection(
                            SourceSelection.NONE, selection, Set.of()),
                    SkillValidationTestFixtures.actionCapabilities(
                            SourceRequirement.NONE,
                            TargetRequirement.OPTIONAL,
                            true,
                            Set.of(),
                            false,
                            false,
                            false));
            var analysis = analyzer().analyze(
                    SkillValidationTestFixtures.candidate(node(0, neutralTriggerDescriptor(true), action)),
                    CONTEXT);
            assertFalse(has(analysis, SkillValidationIssueCodes.CAPABILITY_REQUIRED_TARGET_MISSING));
            assertFalse(has(analysis, SkillValidationIssueCodes.CAPABILITY_UNEXPECTED_TARGET));
        }

        var action = actionDescriptor(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE,
                        TargetSelection.SELF,
                        Set.of(ActionOutputKind.EFFECT)),
                SkillValidationTestFixtures.actionCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.OPTIONAL,
                        false,
                        Set.of(),
                        false,
                        false,
                        false));
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(0, neutralTriggerDescriptor(true), action)),
                CONTEXT);
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_SELF_TARGET_FORBIDDEN));
        assertTrue(has(analysis, SkillValidationIssueCodes.CAPABILITY_UNDECLARED_OUTPUT));
    }

    @Test
    void crossNodeProducerResolutionInspectionOutputAndRolesAreCheckedWithoutCapabilityReentry() {
        var producerAction = actionDescriptor(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of(ActionOutputKind.EFFECT)),
                SkillValidationTestFixtures.actionCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.OPTIONAL,
                        true,
                        Set.of(ActionOutputKind.EFFECT),
                        false,
                        false,
                        false));
        var consumer = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        requiredReference(0, ReferenceRole.SOURCE, ActionOutputKind.PROJECTILE, "output"),
                        reference(0, ReferenceRole.SPLIT_SOURCE, "split"),
                        reference(0, ReferenceRole.CHAIN_SOURCE, "chain"),
                        reference(0, ReferenceRole.REPEAT_SOURCE, "repeat")),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);
        var analysis = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        node(0, neutralTriggerDescriptor(true), producerAction),
                        node(1, consumer, neutralActionDescriptor())),
                CONTEXT);

        assertEquals(1, count(analysis, SkillValidationIssueCodes.REFERENCE_REQUIRED_OUTPUT_MISSING));
        assertEquals(3, count(analysis, SkillValidationIssueCodes.REFERENCE_PRODUCER_ROLE_UNSUPPORTED));
        assertEquals(1, producerAction.capabilityCalls());
    }

    @Test
    void unresolvedAndUninspectedProducersStopCrossNodeCascades() {
        var consumer = triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.PRIOR_NODE,
                        TargetSelection.NONE,
                        false,
                        requiredReference(0, ReferenceRole.SPLIT_SOURCE, ActionOutputKind.EFFECT, "ref")),
                SourceRequirement.PRIOR_NODE,
                TargetRequirement.OPTIONAL,
                false);
        var unresolved = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        SkillValidationTestFixtures.node(
                                0,
                                SkillValidationTestFixtures.unknownTrigger(),
                                SkillValidationTestFixtures.unknownAction()),
                        node(1, consumer, neutralActionDescriptor())),
                CONTEXT);
        assertTrue(has(unresolved, SkillValidationIssueCodes.REFERENCE_PRODUCER_UNRESOLVED));
        assertFalse(has(unresolved, SkillValidationIssueCodes.REFERENCE_REQUIRED_OUTPUT_MISSING));

        var missingInspectorAction = new SkillValidationTestFixtures.ActionDescriptor(
                Optional::empty,
                SkillValidationTestFixtures::defaultActionCapabilities,
                (payload, context) -> ValidationResult.valid());
        var unavailable = analyzer().analyze(
                SkillValidationTestFixtures.candidate(
                        node(0, neutralTriggerDescriptor(true), missingInspectorAction),
                        node(1, consumer, neutralActionDescriptor())),
                CONTEXT);
        assertTrue(has(
                unavailable,
                SkillValidationIssueCodes.REFERENCE_PRODUCER_INSPECTION_UNAVAILABLE));
        assertFalse(has(unavailable, SkillValidationIssueCodes.REFERENCE_REQUIRED_OUTPUT_MISSING));
    }

    @Test
    void currentTargetUsesOnlyExplicitTriggerProvisionAndSuppressesRootCauseCascade() {
        var available = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(
                        0,
                        neutralTriggerDescriptor(true),
                        actionWithCurrentTarget())),
                CONTEXT);
        assertFalse(has(available, SkillValidationIssueCodes.REFERENCE_CURRENT_TARGET_UNAVAILABLE));

        var unavailable = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node(
                        0,
                        neutralTriggerDescriptor(false),
                        actionWithCurrentTarget())),
                CONTEXT);
        assertTrue(has(unavailable, SkillValidationIssueCodes.REFERENCE_CURRENT_TARGET_UNAVAILABLE));

        var unresolved = analyzer().analyze(
                SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.unknownTrigger(),
                        SkillValidationTestFixtures.resolvedAction(actionWithCurrentTarget()))),
                CONTEXT);
        assertTrue(has(unresolved, SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE));
        assertFalse(has(unresolved, SkillValidationIssueCodes.REFERENCE_CURRENT_TARGET_UNAVAILABLE));
    }

    private static SkillValidationAnalyzer analyzer() {
        return new SkillValidationAnalyzer(
                new NodeProjectionResolver(), ProfileAvailabilityView.unknown());
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate neutralNode(
            int index) {
        return node(index, neutralTriggerDescriptor(true), neutralActionDescriptor());
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate node(
            int index,
            SkillValidationTestFixtures.TriggerDescriptor trigger,
            SkillValidationTestFixtures.ActionDescriptor action) {
        return SkillValidationTestFixtures.node(
                index,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action));
    }

    private static SkillValidationTestFixtures.TriggerDescriptor neutralTriggerDescriptor(
            boolean providesCurrentTarget) {
        return triggerDescriptor(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE,
                        TargetSelection.NONE,
                        providesCurrentTarget),
                SourceRequirement.NONE,
                TargetRequirement.OPTIONAL,
                false);
    }

    private static SkillValidationTestFixtures.ActionDescriptor neutralActionDescriptor() {
        return actionDescriptor(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()),
                SkillValidationTestFixtures.defaultActionCapabilities());
    }

    private static SkillValidationTestFixtures.ActionDescriptor actionWithCurrentTarget() {
        return actionDescriptor(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.CURRENT_TARGET, Set.of()),
                SkillValidationTestFixtures.actionCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.REQUIRED,
                        false,
                        Set.of(),
                        false,
                        false,
                        false));
    }

    private static SkillValidationTestFixtures.TriggerDescriptor triggerDescriptor(
            com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection projection,
            SourceRequirement source,
            TargetRequirement target,
            boolean continuation) {
        return new SkillValidationTestFixtures.TriggerDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(projection)),
                () -> SkillValidationTestFixtures.triggerCapabilities(source, target, continuation),
                (payload, context) -> ValidationResult.valid());
    }

    private static SkillValidationTestFixtures.ActionDescriptor actionDescriptor(
            com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection projection,
            com.yo1no.gramarye.magic.capability.ActionCapabilities capabilities) {
        return new SkillValidationTestFixtures.ActionDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(projection)),
                () -> capabilities,
                (payload, context) -> ValidationResult.valid());
    }

    private static NodeReference reference(int index, ReferenceRole role, String field) {
        return new NodeReference(
                index, role, ValidationPath.empty().field(field), Optional.empty());
    }

    private static NodeReference requiredReference(
            int index,
            ReferenceRole role,
            ActionOutputKind output,
            String field) {
        return new NodeReference(
                index,
                role,
                ValidationPath.empty().field(field),
                Optional.of(output));
    }

    private static int count(SkillValidationAnalysis analysis, ValidationIssueCode code) {
        return (int) analysis.report().issues().stream()
                .filter(issue -> issue.code().equals(code))
                .count();
    }

    private static boolean has(SkillValidationAnalysis analysis, ValidationIssueCode code) {
        return count(analysis, code) > 0;
    }
}
