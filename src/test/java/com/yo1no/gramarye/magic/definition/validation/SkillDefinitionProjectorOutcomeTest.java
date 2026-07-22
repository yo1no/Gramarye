package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillDefinitionProjectorOutcomeTest {
    private final SkillDefinitionProjector projector = new SkillDefinitionProjector();

    @Test
    void emptyWarningAndTruncatedReportsAreAcceptedWithReferenceIdentity() {
        var reports = List.of(
                ValidationResult.valid(),
                ValidationResult.of(issue(ValidationSeverity.WARNING, "warning")),
                new ValidationResult(List.of(), true, false));

        for (var report : reports) {
            var analysis = validAnalysis(report);
            var outcome = assertInstanceOf(
                    SkillValidationOutcome.Accepted.class,
                    projector.project(analysis));

            assertSame(report, outcome.report());
            assertSame(analysis.report(), outcome.report());
            assertFalse(outcome.report().hasErrors());
        }
    }

    @Test
    void retainedAndOmittedErrorsAreRejectedWithoutPartialDefinition() {
        var reports = List.of(
                ValidationResult.of(issue(ValidationSeverity.ERROR, "retained_error")),
                new ValidationResult(List.of(), true, true));

        for (var report : reports) {
            var analysis = futureSchemaAnalysis(report);
            var outcome = assertInstanceOf(
                    SkillValidationOutcome.Rejected.class,
                    projector.project(analysis));

            assertSame(report, outcome.report());
            assertSame(analysis.report(), outcome.report());
        }
    }

    @Test
    void outcomeConstructorsEnforceSeverityButAllowCallerOwnedWarningPairing() {
        var accepted = assertInstanceOf(
                SkillValidationOutcome.Accepted.class,
                projector.project(validAnalysis(ValidationResult.valid())));
        var warningReport = ValidationResult.of(issue(ValidationSeverity.WARNING, "other_warning"));

        assertDoesNotThrow(() -> new SkillValidationOutcome.Accepted(
                accepted.definition(), warningReport));
        assertThrows(IllegalArgumentException.class, () -> new SkillValidationOutcome.Accepted(
                accepted.definition(),
                ValidationResult.of(issue(ValidationSeverity.ERROR, "error"))));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationOutcome.Rejected(warningReport));
        assertThrows(NullPointerException.class, () ->
                new SkillValidationOutcome.Accepted(null, warningReport));
        assertThrows(NullPointerException.class, () ->
                new SkillValidationOutcome.Rejected(null));
    }

    @Test
    void repeatedProjectionIsDeterministicAndAlwaysRetainsOriginalReport() {
        var report = ValidationResult.of(issue(ValidationSeverity.WARNING, "warning"));
        var analysis = validAnalysis(report);

        var first = assertInstanceOf(
                SkillValidationOutcome.Accepted.class, projector.project(analysis));
        var second = assertInstanceOf(
                SkillValidationOutcome.Accepted.class, projector.project(analysis));

        assertSame(report, first.report());
        assertSame(report, second.report());
        assertSame(first.definition().reference(), second.definition().reference());
        assertSame(first.definition().nodes().getFirst().trigger(),
                second.definition().nodes().getFirst().trigger());
        assertSame(first.definition().nodes().getFirst().action(),
                second.definition().nodes().getFirst().action());
    }

    private static SkillValidationAnalysis validAnalysis(ValidationResult report) {
        var triggerProjection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
        var actionProjection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of());
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(triggerProjection);
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(actionProjection);
        var candidate = SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action)));
        return SkillValidationTestFixtures.analysis(
                candidate,
                report,
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(triggerProjection),
                        new ActionInspectionState.Success(actionProjection)));
    }

    private static SkillValidationAnalysis futureSchemaAnalysis(ValidationResult report) {
        var candidate = SkillValidationTestFixtures.candidate(
                1,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false));
        return new SkillValidationAnalysis(candidate, Optional.empty(), report);
    }

    private static ValidationIssue issue(ValidationSeverity severity, String path) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "test." + path),
                severity,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());
    }
}
