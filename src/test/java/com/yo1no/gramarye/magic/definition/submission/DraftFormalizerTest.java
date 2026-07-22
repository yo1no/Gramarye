package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraftWriter;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftFormalizerTest {
    private final DraftFormalizer formalizer = new DraftFormalizer();

    @Test
    void presentSlotsFormalizeInOrderWithoutCopyingSemanticChildren() {
        var firstTrigger = SubmissionTestFixtures.envelope("first_trigger");
        var firstAction = SubmissionTestFixtures.envelope("first_action");
        var secondTrigger = SubmissionTestFixtures.envelope("second_trigger");
        var secondAction = SubmissionTestFixtures.envelope("second_action");
        var firstOverride = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var secondOverride = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED);
        var draft = SubmissionTestFixtures.draft(0, List.of(
                SubmissionTestFixtures.completeNode(firstTrigger, firstAction, firstOverride),
                SubmissionTestFixtures.completeNode(secondTrigger, secondAction, secondOverride)));
        var input = SkillSubmissionInput.direct(draft);

        var ready = assertInstanceOf(
                DraftFormalizationResult.Ready.class,
                formalizer.formalize(precheckReady(input, ValidationResult.valid())));

        assertAll(
                () -> assertSame(input, ready.input()),
                () -> assertEquals(2, ready.nodes().size()),
                () -> assertSame(firstTrigger, ready.nodes().get(0).trigger()),
                () -> assertSame(firstAction, ready.nodes().get(0).action()),
                () -> assertSame(firstOverride, ready.nodes().get(0).appearanceOverride()),
                () -> assertSame(secondTrigger, ready.nodes().get(1).trigger()),
                () -> assertSame(secondAction, ready.nodes().get(1).action()),
                () -> assertSame(secondOverride, ready.nodes().get(1).appearanceOverride()),
                () -> assertFalse(Arrays.stream(NodeDocument.class.getRecordComponents())
                        .anyMatch(component -> component.getName().equals("index"))));
    }

    @Test
    void allMissingPresentCombinationsProduceTheExpectedOutcome() {
        assertCombination(SubmissionTestFixtures.completeNode(), List.of());
        assertCombination(
                SubmissionTestFixtures.missingTrigger(),
                List.of(SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING));
        assertCombination(
                SubmissionTestFixtures.missingAction(),
                List.of(SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING));
        assertCombination(
                SubmissionTestFixtures.missingBoth(),
                List.of(
                        SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING,
                        SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING));
    }

    @Test
    void multiNodeIssuesAreDeterministicByNodeThenTriggerBeforeAction() {
        var draft = SubmissionTestFixtures.draft(0, List.of(
                SubmissionTestFixtures.missingBoth(),
                SubmissionTestFixtures.missingAction(),
                SubmissionTestFixtures.missingTrigger()));

        var invalid = assertInstanceOf(
                DraftFormalizationResult.Invalid.class,
                formalizer.formalize(precheckReady(
                        SkillSubmissionInput.direct(draft), ValidationResult.valid())));

        assertAll(
                () -> assertEquals(List.of(
                                SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING,
                                SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING,
                                SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING,
                                SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING),
                        invalid.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertEquals(List.of(
                                "nodes[0].trigger",
                                "nodes[0].action",
                                "nodes[1].action",
                                "nodes[2].trigger"),
                        invalid.report().issues().stream().map(issue -> issue.path().render()).toList()));
    }

    @Test
    void invalidResultExposesNoPartialNodeList() {
        var draft = SubmissionTestFixtures.draft(0, List.of(
                SubmissionTestFixtures.completeNode(),
                SubmissionTestFixtures.missingTrigger(),
                SubmissionTestFixtures.completeNode()));

        var invalid = assertInstanceOf(
                DraftFormalizationResult.Invalid.class,
                formalizer.formalize(precheckReady(
                        SkillSubmissionInput.direct(draft), ValidationResult.valid())));

        assertAll(
                () -> assertTrue(invalid.report().hasErrors()),
                () -> assertEquals(List.of("report"),
                        Arrays.stream(DraftFormalizationResult.Invalid.class.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertFalse(Arrays.stream(DraftFormalizationResult.Invalid.class.getMethods())
                        .anyMatch(method -> method.getName().equals("nodes"))));
    }

    @Test
    void readyListIsImmutableAndMustCoverTheWholeDraft() {
        var draft = SubmissionTestFixtures.draft(0, List.of(SubmissionTestFixtures.completeNode()));
        var input = SkillSubmissionInput.direct(draft);
        var ready = assertInstanceOf(
                DraftFormalizationResult.Ready.class,
                formalizer.formalize(precheckReady(input, ValidationResult.valid())));

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> ready.nodes().clear()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftFormalizationResult.Ready(
                                input, List.of(), ValidationResult.valid())));
    }

    @Test
    void emptyDraftFormalizesReadyAndDefersEmptySkillPolicyToP3C3() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(0, List.of()));

        var ready = assertInstanceOf(
                DraftFormalizationResult.Ready.class,
                formalizer.formalize(precheckReady(input, ValidationResult.valid())));

        assertAll(
                () -> assertTrue(ready.nodes().isEmpty()),
                () -> assertTrue(ready.report().issues().isEmpty()),
                () -> assertFalse(ready.report().hasErrors()));
    }

    @Test
    void mergePreservesPrecheckOrderAndTruncationState() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(
                0, List.of(SubmissionTestFixtures.completeNode())));
        var first = warning("precheck.first", "appearance");
        var second = warning("precheck.second", "nodes");
        var precheckReport = new ValidationResult(List.of(first, second), true, false);

        var ready = assertInstanceOf(
                DraftFormalizationResult.Ready.class,
                formalizer.formalize(precheckReady(input, precheckReport)));

        assertAll(
                () -> assertEquals(List.of(first, second), ready.report().issues()),
                () -> assertTrue(ready.report().truncated()),
                () -> assertFalse(ready.report().omittedError()),
                () -> assertFalse(ready.report().hasErrors()));
    }

    @Test
    void missingSlotAfterAFullWarningReportMarksAHiddenError() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(
                0, List.of(SubmissionTestFixtures.missingTrigger())));
        var precheckReport = new ValidationResult(
                uniqueWarnings(MagicSafetyCeilings.MAX_VALIDATION_ISSUES),
                false,
                false);

        var invalid = assertInstanceOf(
                DraftFormalizationResult.Invalid.class,
                formalizer.formalize(precheckReady(input, precheckReport)));

        assertAll(
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_VALIDATION_ISSUES,
                        invalid.report().issues().size()),
                () -> assertTrue(invalid.report().truncated()),
                () -> assertTrue(invalid.report().omittedError()),
                () -> assertTrue(invalid.report().hasErrors()),
                () -> assertTrue(invalid.report().errors().isEmpty()));
    }

    @Test
    void readyResultRejectsHiddenErrorsButAllowsWarningOnlyTruncation() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(0, List.of()));
        var warning = warning("result.warning", "appearance");
        var validTruncation = new ValidationResult(List.of(warning), true, false);
        var hiddenError = new ValidationResult(List.of(warning), true, true);

        assertAll(
                () -> assertDoesNotThrow(() -> new DraftFormalizationResult.Ready(
                        input, List.of(), validTruncation)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftFormalizationResult.Ready(input, List.of(), hiddenError)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftFormalizationResult.Invalid(ValidationResult.valid())));
    }

    @Test
    void formalizationDoesNotMutateDraftAndWriterOutputIsStructurallyDeterministic() {
        var appearance = new AppearanceDocument.Rejected(
                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED);
        var override = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var trigger = SubmissionTestFixtures.envelope("trigger");
        var action = SubmissionTestFixtures.envelope("action");
        var draft = SubmissionTestFixtures.draft(0, List.of(
                SubmissionTestFixtures.completeNode(trigger, action, override)), appearance);
        var input = SkillSubmissionInput.direct(draft);
        var beforeModel = draft;
        var beforeEncoded = SkillDraftWriter.write(draft, JsonOps.INSTANCE).getOrThrow();

        var ready = assertInstanceOf(
                DraftFormalizationResult.Ready.class,
                formalizer.formalize(precheckReady(input, ValidationResult.valid())));
        var afterEncoded = SkillDraftWriter.write(input.draft(), JsonOps.INSTANCE).getOrThrow();

        assertAll(
                () -> assertSame(beforeModel, input.draft()),
                () -> assertEquals(beforeModel, input.draft()),
                () -> assertEquals(beforeEncoded, afterEncoded),
                () -> assertSame(appearance, input.draft().appearance()),
                () -> assertSame(trigger, ready.nodes().getFirst().trigger()),
                () -> assertSame(action, ready.nodes().getFirst().action()),
                () -> assertSame(override, ready.nodes().getFirst().appearanceOverride()));
    }

    @Test
    void formalizerRejectsNullReadyToken() {
        assertThrows(NullPointerException.class, () -> formalizer.formalize(null));
    }

    private void assertCombination(DraftNode node, List<?> expectedCodes) {
        var draft = SubmissionTestFixtures.draft(0, List.of(node));
        var result = formalizer.formalize(precheckReady(
                SkillSubmissionInput.direct(draft), ValidationResult.valid()));
        if (expectedCodes.isEmpty()) {
            var ready = assertInstanceOf(DraftFormalizationResult.Ready.class, result);
            assertEquals(1, ready.nodes().size());
            return;
        }
        var invalid = assertInstanceOf(DraftFormalizationResult.Invalid.class, result);
        assertEquals(expectedCodes,
                invalid.report().issues().stream().map(issue -> issue.code()).toList());
    }

    private static DraftSubmissionPrecheck.Ready precheckReady(
            SkillSubmissionInput input,
            ValidationResult report) {
        return new DraftSubmissionPrecheck.Ready(input, report);
    }

    private static ValidationIssue warning(String code, String path) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", code),
                ValidationSeverity.WARNING,
                ValidationPath.empty().field(path),
                ValidationIssueMetadata.none());
    }

    private static List<ValidationIssue> uniqueWarnings(int count) {
        var warnings = new ArrayList<ValidationIssue>(count);
        for (var index = 0; index < count; index++) {
            warnings.add(new ValidationIssue(
                    ValidationIssueCode.fromNamespaceAndPath(
                            "gramarye", "submission.precheck_warning_" + index),
                    ValidationSeverity.WARNING,
                    ValidationPath.empty().field("precheck").index(index),
                    ValidationIssueMetadata.none()));
        }
        return warnings;
    }
}
