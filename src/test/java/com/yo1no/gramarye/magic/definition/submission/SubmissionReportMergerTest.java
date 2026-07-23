package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SubmissionReportMergerTest {
    @Test
    void crossSourceExactDuplicateIsFirstWinsWithoutReorderingLaterIssues() {
        var a = issue("a", "node_0", ValidationIssueMetadata.none());
        var b = issue("b", "node_1", ValidationIssueMetadata.none());
        var c = issue("c", "node_2", ValidationIssueMetadata.none());
        var earlier = new ValidationResult(List.of(a, b), false, false);
        var later = new ValidationResult(List.of(a, c), false, false);

        var merged = SubmissionReportMerger.merge(earlier, later);

        assertAll(
                () -> assertEquals(List.of(a, b, c), merged.issues()),
                () -> assertEquals(List.of(a, b), earlier.issues()),
                () -> assertEquals(List.of(a, c), later.issues()));
    }

    @Test
    void sameCodeWithDifferentPathOrMetadataIsNotDeduplicated() {
        var code = ValidationIssueCode.fromNamespaceAndPath("gramarye", "merge.shared");
        var first = new ValidationIssue(
                code,
                ValidationSeverity.WARNING,
                ValidationPath.empty().field("first"),
                ValidationIssueMetadata.none());
        var second = new ValidationIssue(
                code,
                ValidationSeverity.WARNING,
                ValidationPath.empty().field("second"),
                ValidationIssueMetadata.none());
        var third = new ValidationIssue(
                code,
                ValidationSeverity.WARNING,
                ValidationPath.empty().field("first"),
                new ValidationIssueMetadata.Limit(2, 3));

        var merged = SubmissionReportMerger.merge(
                ValidationResult.of(first),
                new ValidationResult(List.of(second, third), false, false));

        assertEquals(List.of(first, second, third), merged.issues());
    }

    @Test
    void truncationPropagatesFromEitherSideWithoutInventingAnOmittedError() {
        var warning = issue("warning", "appearance", ValidationIssueMetadata.none());
        var earlierTruncated = new ValidationResult(List.of(warning), true, false);
        var laterTruncated = new ValidationResult(List.of(warning), true, false);

        var fromEarlier = SubmissionReportMerger.merge(
                earlierTruncated, ValidationResult.valid());
        var fromLater = SubmissionReportMerger.merge(
                ValidationResult.valid(), laterTruncated);

        assertAll(
                () -> assertTrue(fromEarlier.truncated()),
                () -> assertFalse(fromEarlier.omittedError()),
                () -> assertFalse(fromEarlier.hasErrors()),
                () -> assertTrue(fromLater.truncated()),
                () -> assertFalse(fromLater.omittedError()),
                () -> assertFalse(fromLater.hasErrors()));
    }

    @Test
    void hiddenErrorPropagatesFromEitherSide() {
        var warning = issue("warning", "appearance", ValidationIssueMetadata.none());
        var hidden = new ValidationResult(List.of(warning), true, true);

        var fromEarlier = SubmissionReportMerger.merge(hidden, ValidationResult.valid());
        var fromLater = SubmissionReportMerger.merge(ValidationResult.valid(), hidden);

        assertAll(
                () -> assertTrue(fromEarlier.omittedError()),
                () -> assertTrue(fromEarlier.hasErrors()),
                () -> assertTrue(fromLater.omittedError()),
                () -> assertTrue(fromLater.hasErrors()));
    }

    @Test
    void receivingCapTurnsAnOmittedLaterErrorIntoHiddenError() {
        var fullWarnings = IntStream.range(0, MagicSafetyCeilings.MAX_VALIDATION_ISSUES)
                .mapToObj(index -> issue(
                        "warning_" + index,
                        "node_" + index,
                        ValidationIssueMetadata.none()))
                .toList();
        var error = new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "merge.late_error"),
                ValidationSeverity.ERROR,
                ValidationPath.empty(),
                ValidationIssueMetadata.none());

        var merged = SubmissionReportMerger.merge(
                new ValidationResult(fullWarnings, false, false),
                ValidationResult.of(error));

        assertAll(
                () -> assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES,
                        merged.issues().size()),
                () -> assertTrue(merged.truncated()),
                () -> assertTrue(merged.omittedError()),
                () -> assertTrue(merged.hasErrors()),
                () -> assertTrue(merged.errors().isEmpty()));
    }

    @Test
    void nullBoundariesAreRejected() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> SubmissionReportMerger.merge(null, ValidationResult.valid())),
                () -> assertThrows(NullPointerException.class,
                        () -> SubmissionReportMerger.merge(ValidationResult.valid(), null)));
    }

    private static ValidationIssue issue(
            String code,
            String path,
            ValidationIssueMetadata metadata) {
        var validationPath = ValidationPath.empty();
        if (!path.isEmpty()) {
            validationPath = validationPath.field(path);
        }
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "merge." + code),
                ValidationSeverity.WARNING,
                validationPath,
                metadata);
    }
}
