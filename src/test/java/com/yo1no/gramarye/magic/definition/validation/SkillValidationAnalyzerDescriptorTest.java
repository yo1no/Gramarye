package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationPathSegment;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SkillValidationAnalyzerDescriptorTest {
    private static final ValidationContext CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);
    private static final ValidationIssueCode THIRD_PARTY_WARNING =
            ValidationIssueCode.fromNamespaceAndPath("othermod", "payload.warning");
    private static final ValidationIssueCode THIRD_PARTY_ERROR =
            ValidationIssueCode.fromNamespaceAndPath("othermod", "payload.error");

    @Test
    void capabilitySnapshotOccursOncePerResolvedSideEvenWhenInspectionFails() {
        var trigger = new SkillValidationTestFixtures.TriggerDescriptor(
                Optional::empty,
                SkillValidationTestFixtures::defaultTriggerCapabilities,
                (payload, context) -> ValidationResult.valid());
        var action = new SkillValidationTestFixtures.ActionDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(
                        SkillValidationTestFixtures.actionProjection(
                                SourceSelection.NONE, TargetSelection.NONE, Set.of()))),
                SkillValidationTestFixtures::defaultActionCapabilities,
                (payload, context) -> ValidationResult.valid());

        analyzer().analyze(candidate(trigger, action), CONTEXT);

        assertEquals(1, trigger.capabilityCalls());
        assertEquals(1, action.capabilityCalls());
        assertEquals(1, trigger.validatorCalls());
        assertEquals(1, action.validatorCalls());
    }

    @Test
    void nullAndRuntimeCapabilityFailuresAreBoundedAndValidatorStillRuns() {
        var trigger = descriptorWithCapabilities(() -> null);
        var action = actionDescriptorWithCapabilities(() -> {
            throw new IllegalStateException("secret-capability-message");
        });

        var analysis = analyzer().analyze(candidate(trigger, action), CONTEXT);

        assertEquals(List.of(
                SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_CONTRACT_VIOLATION,
                SkillValidationIssueCodes.DESCRIPTOR_CAPABILITIES_EXCEPTION),
                analysis.report().issues().stream().map(issue -> issue.code()).toList());
        var metadata = assertInstanceOf(
                ValidationIssueMetadata.ExceptionClass.class,
                analysis.report().issues().get(1).metadata());
        assertEquals(IllegalStateException.class.getName(), metadata.className());
        assertFalse(analysis.report().toString().contains("secret-capability-message"));
        assertEquals(1, trigger.validatorCalls());
        assertEquals(1, action.validatorCalls());
    }

    @Test
    void capabilityErrorIsNotCaught() {
        var trigger = descriptorWithCapabilities(() -> {
            throw new AssertionError("must escape");
        });
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));

        assertThrows(AssertionError.class, () -> analyzer().analyze(candidate(trigger, action), CONTEXT));
    }

    @Test
    void validatorsReceiveSameContextOnceInNodeAndSideOrder() {
        var order = new java.util.ArrayList<String>();
        var firstTrigger = triggerDescriptor((payload, context) -> {
            assertSame(CONTEXT, context);
            order.add("0-trigger");
            return ValidationResult.valid();
        });
        var firstAction = actionDescriptor((payload, context) -> {
            assertSame(CONTEXT, context);
            order.add("0-action");
            return ValidationResult.valid();
        });
        var secondTrigger = triggerDescriptor((payload, context) -> {
            assertSame(CONTEXT, context);
            order.add("1-trigger");
            return ValidationResult.valid();
        });
        var secondAction = actionDescriptor((payload, context) -> {
            assertSame(CONTEXT, context);
            order.add("1-action");
            return ValidationResult.valid();
        });
        var candidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(firstTrigger),
                        SkillValidationTestFixtures.resolvedAction(firstAction)),
                SkillValidationTestFixtures.node(
                        1,
                        SkillValidationTestFixtures.resolvedTrigger(secondTrigger),
                        SkillValidationTestFixtures.resolvedAction(secondAction)));

        analyzer().analyze(candidate, CONTEXT);

        assertEquals(List.of("0-trigger", "0-action", "1-trigger", "1-action"), order);
        assertEquals(0, firstTrigger.codecCalls());
        assertEquals(0, firstAction.codecCalls());
        assertEquals(1, firstTrigger.inspectorAccessorCalls());
        assertEquals(1, firstAction.inspectorAccessorCalls());
    }

    @Test
    void nullRuntimeExceptionAndErrorValidatorPoliciesAreDistinct() {
        var nullTrigger = triggerDescriptor((payload, context) -> null);
        var throwingAction = actionDescriptor((payload, context) -> {
            throw new UnsupportedOperationException("secret-validator-message");
        });
        var analysis = analyzer().analyze(candidate(nullTrigger, throwingAction), CONTEXT);

        assertEquals(List.of(
                SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_CONTRACT_VIOLATION,
                SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_EXCEPTION),
                analysis.report().issues().stream().map(issue -> issue.code()).toList());
        assertFalse(analysis.report().toString().contains("secret-validator-message"));

        var errorTrigger = triggerDescriptor((payload, context) -> {
            throw new AssertionError("must escape");
        });
        assertThrows(AssertionError.class, () -> analyzer().analyze(
                candidate(errorTrigger, actionDescriptor((payload, context) -> ValidationResult.valid())),
                CONTEXT));
    }

    @Test
    void descriptorIssuesArePrefixedInOrderAndExactDuplicatesAreRemoved() {
        var warning = new ValidationIssue(
                THIRD_PARTY_WARNING,
                ValidationSeverity.WARNING,
                ValidationPath.empty().field("first"),
                ValidationIssueMetadata.none());
        var error = new ValidationIssue(
                THIRD_PARTY_ERROR,
                ValidationSeverity.ERROR,
                ValidationPath.empty().field("second"),
                ValidationIssueMetadata.none());
        var trigger = triggerDescriptor((payload, context) ->
                new ValidationResult(List.of(warning, error), false, false));
        var action = actionDescriptor((payload, context) ->
                ValidationResult.of(warning));

        var analysis = analyzer().analyze(candidate(trigger, action), CONTEXT);

        assertEquals(List.of(THIRD_PARTY_WARNING, THIRD_PARTY_ERROR, THIRD_PARTY_WARNING),
                analysis.report().issues().stream()
                        .filter(issue -> issue.code().value().getNamespace().equals("othermod"))
                        .map(issue -> issue.code()).toList());
        assertEquals("nodes[0].trigger.payload.first", analysis.report().issues().get(0).path().render());
        assertEquals("nodes[0].trigger.payload.second", analysis.report().issues().get(1).path().render());
        assertEquals("nodes[0].action.payload.first", analysis.report().issues().get(2).path().render());
    }

    @Test
    void descriptorSegmentAndRenderOverflowBecomeContractViolations() {
        var segments = IntStream.range(0, MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS)
                .mapToObj(index -> (ValidationPathSegment) new ValidationPathSegment.Index(index))
                .toList();
        var segmentOverflow = new ValidationIssue(
                THIRD_PARTY_WARNING,
                ValidationSeverity.WARNING,
                new ValidationPath(segments),
                ValidationIssueMetadata.none());
        var renderOverflow = new ValidationIssue(
                THIRD_PARTY_WARNING,
                ValidationSeverity.WARNING,
                ValidationPath.empty().field("x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH)),
                ValidationIssueMetadata.none());
        var trigger = triggerDescriptor((payload, context) ->
                new ValidationResult(List.of(segmentOverflow, renderOverflow), false, false));

        var analysis = analyzer().analyze(
                candidate(trigger, actionDescriptor((payload, context) -> ValidationResult.valid())),
                CONTEXT);

        var violations = analysis.report().issues().stream()
                .filter(issue -> issue.code().equals(
                        SkillValidationIssueCodes.DESCRIPTOR_VALIDATOR_CONTRACT_VIOLATION))
                .toList();
        assertEquals(2, violations.size());
        assertTrue(violations.stream().allMatch(issue ->
                issue.path().render().equals("nodes[0].trigger.payload")));
        assertInstanceOf(ValidationIssueMetadata.Limit.class, violations.get(0).metadata());
        assertInstanceOf(ValidationIssueMetadata.Limit.class, violations.get(1).metadata());
        assertFalse(analysis.report().issues().contains(segmentOverflow));
        assertFalse(analysis.report().issues().contains(renderOverflow));
    }

    @Test
    void descriptorReportFlagsPropagateWithoutInventingOmittedErrors() {
        var warning = new ValidationIssue(
                THIRD_PARTY_WARNING,
                ValidationSeverity.WARNING,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());
        var trigger = triggerDescriptor((payload, context) ->
                new ValidationResult(List.of(warning), true, false));
        var analysis = analyzer().analyze(
                candidate(trigger, actionDescriptor((payload, context) -> ValidationResult.valid())),
                CONTEXT);

        assertTrue(analysis.report().truncated());
        assertFalse(analysis.report().omittedError());
        assertFalse(analysis.report().hasErrors());

        var omitted = triggerDescriptor((payload, context) ->
                new ValidationResult(List.of(), true, true));
        var omittedAnalysis = analyzer().analyze(
                candidate(omitted, actionDescriptor((payload, context) -> ValidationResult.valid())),
                CONTEXT);
        assertTrue(omittedAnalysis.report().truncated());
        assertTrue(omittedAnalysis.report().omittedError());
        assertTrue(omittedAnalysis.report().hasErrors());
    }

    @Test
    void collectorContinuesAfterWarningCapAndDetectsHiddenError() {
        var warnings = IntStream.range(0, MagicSafetyCeilings.MAX_VALIDATION_ISSUES)
                .mapToObj(index -> new ValidationIssue(
                        THIRD_PARTY_WARNING,
                        ValidationSeverity.WARNING,
                        ValidationPath.empty().index(index),
                        ValidationIssueMetadata.none()))
                .toList();
        var trigger = triggerDescriptor((payload, context) ->
                new ValidationResult(warnings, false, false));
        var action = actionDescriptor((payload, context) -> ValidationResult.of(new ValidationIssue(
                THIRD_PARTY_ERROR,
                ValidationSeverity.ERROR,
                ValidationPath.empty(),
                ValidationIssueMetadata.none())));

        var analysis = analyzer().analyze(candidate(trigger, action), CONTEXT);

        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, analysis.report().issues().size());
        assertTrue(analysis.report().truncated());
        assertTrue(analysis.report().omittedError());
        assertTrue(analysis.report().hasErrors());
    }

    private static SkillValidationAnalyzer analyzer() {
        return new SkillValidationAnalyzer(
                new NodeProjectionResolver(), ProfileAvailabilityView.unknown());
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate candidate(
            SkillValidationTestFixtures.TriggerDescriptor trigger,
            SkillValidationTestFixtures.ActionDescriptor action) {
        return SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action)));
    }

    private static SkillValidationTestFixtures.TriggerDescriptor descriptorWithCapabilities(
            java.util.function.Supplier<com.yo1no.gramarye.magic.capability.TriggerCapabilities>
                    capabilities) {
        return new SkillValidationTestFixtures.TriggerDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(
                        SkillValidationTestFixtures.triggerProjection(
                                SourceSelection.NONE, TargetSelection.NONE, true))),
                capabilities,
                (payload, context) -> ValidationResult.valid());
    }

    private static SkillValidationTestFixtures.ActionDescriptor actionDescriptorWithCapabilities(
            java.util.function.Supplier<com.yo1no.gramarye.magic.capability.ActionCapabilities>
                    capabilities) {
        return new SkillValidationTestFixtures.ActionDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(
                        SkillValidationTestFixtures.actionProjection(
                                SourceSelection.NONE, TargetSelection.NONE, Set.of()))),
                capabilities,
                (payload, context) -> ValidationResult.valid());
    }

    private static SkillValidationTestFixtures.TriggerDescriptor triggerDescriptor(
            java.util.function.BiFunction<
                            SkillValidationTestFixtures.TriggerData,
                            ValidationContext,
                            ValidationResult>
                    validator) {
        return new SkillValidationTestFixtures.TriggerDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(
                        SkillValidationTestFixtures.triggerProjection(
                                SourceSelection.NONE, TargetSelection.NONE, true))),
                () -> SkillValidationTestFixtures.triggerCapabilities(
                        SourceRequirement.NONE, TargetRequirement.OPTIONAL, false),
                validator);
    }

    private static SkillValidationTestFixtures.ActionDescriptor actionDescriptor(
            java.util.function.BiFunction<
                            SkillValidationTestFixtures.ActionData,
                            ValidationContext,
                            ValidationResult>
                    validator) {
        return new SkillValidationTestFixtures.ActionDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(
                        SkillValidationTestFixtures.actionProjection(
                                SourceSelection.NONE, TargetSelection.NONE, Set.of()))),
                () -> SkillValidationTestFixtures.actionCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.OPTIONAL,
                        true,
                        Set.of(),
                        false,
                        false,
                        false),
                validator);
    }
}
