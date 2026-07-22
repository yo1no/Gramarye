package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DraftSubmissionPrecheckerTest {
    private final DraftSubmissionPrechecker prechecker = new DraftSubmissionPrechecker();

    @Test
    void currentSchemaProducesReadyAndPreservesInputIdentity() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(
                0, List.of(SubmissionTestFixtures.completeNode())));

        var ready = assertInstanceOf(DraftSubmissionPrecheck.Ready.class, prechecker.check(input));

        assertAll(
                () -> assertSame(input, ready.input()),
                () -> assertFalse(ready.report().hasErrors()),
                () -> assertTrue(ready.report().issues().isEmpty()));
    }

    @Test
    void higherSchemaProducesTypedInvalidAfterReadWarnings() {
        var fact = new ReadFact(
                ReadFactCode.INTENSITY_CLAMPED_HIGH,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(),
                Optional.of(AppearanceField.INTENSITY_MILLI));
        var draft = SubmissionTestFixtures.draft(1, List.of(SubmissionTestFixtures.missingBoth()));
        var input = SkillSubmissionInput.fromReadResult(new SkillDraftReadResult(
                draft, new SkillDraftReadReport(List.of(fact), true)));

        var invalid = assertInstanceOf(DraftSubmissionPrecheck.Invalid.class, prechecker.check(input));

        assertAll(
                () -> assertEquals(List.of(
                                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                                SkillValidationIssueCodes.READ_REPORT_TRUNCATED,
                                SkillSubmissionIssueCodes.DRAFT_UNSUPPORTED_SCHEMA),
                        invalid.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertEquals("draft_schema_version",
                        invalid.report().issues().get(2).path().render()),
                () -> assertEquals(new ValidationIssueMetadata.Schema(1, 0),
                        invalid.report().issues().get(2).metadata()),
                () -> assertTrue(invalid.report().hasErrors()),
                () -> assertFalse(invalid.report().issues().stream().anyMatch(issue ->
                        issue.code().equals(SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING)
                                || issue.code().equals(SkillSubmissionIssueCodes.DRAFT_ACTION_MISSING))));
    }

    @Test
    void missingSlotsDoNotBlockTheSchemaStage() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(
                0, List.of(SubmissionTestFixtures.missingBoth())));

        var ready = assertInstanceOf(DraftSubmissionPrecheck.Ready.class, prechecker.check(input));

        assertTrue(ready.report().issues().isEmpty());
    }

    @Test
    void negativeSchemaIsAlreadyRejectedByTheDraftInvariant() {
        assertThrows(IllegalArgumentException.class,
                () -> SubmissionTestFixtures.draft(-1, List.of()));
    }

    @Test
    void readyAcceptsWarningOnlyTruncationButRejectsHiddenError() {
        var input = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(0, List.of()));
        var warning = warning("precheck.warning");
        var validTruncation = new ValidationResult(List.of(warning), true, false);
        var hiddenError = new ValidationResult(List.of(warning), true, true);

        assertAll(
                () -> assertDoesNotThrow(
                        () -> new DraftSubmissionPrecheck.Ready(input, validTruncation)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftSubmissionPrecheck.Ready(input, hiddenError)),
                () -> assertTrue(hiddenError.hasErrors()));
    }

    @Test
    void invalidAndReadyConstructorsEnforceTheirSeverityAndSchemaInvariants() {
        var current = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(0, List.of()));
        var future = SkillSubmissionInput.direct(SubmissionTestFixtures.draft(1, List.of()));
        var error = new ValidationIssue(
                SkillSubmissionIssueCodes.DRAFT_UNSUPPORTED_SCHEMA,
                ValidationSeverity.ERROR,
                ValidationPath.empty().field("draft_schema_version"),
                new ValidationIssueMetadata.Schema(1, 0));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftSubmissionPrecheck.Invalid(ValidationResult.valid())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new DraftSubmissionPrecheck.Ready(future, ValidationResult.valid())),
                () -> assertDoesNotThrow(
                        () -> new DraftSubmissionPrecheck.Invalid(ValidationResult.of(error))),
                () -> assertThrows(NullPointerException.class, () -> prechecker.check(null)),
                () -> assertDoesNotThrow(
                        () -> new DraftSubmissionPrecheck.Ready(current, ValidationResult.valid())));
    }

    private static ValidationIssue warning(String path) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", path),
                ValidationSeverity.WARNING,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());
    }
}
