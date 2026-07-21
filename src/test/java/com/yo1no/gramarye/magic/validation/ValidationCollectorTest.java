package com.yo1no.gramarye.magic.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationCollectorTest {
    @Test
    void exactDuplicatesAreRemovedButDifferentMetadataRemains() {
        var first = issue(0, ValidationSeverity.WARNING, new ValidationIssueMetadata.Limit(1, 2));
        var exactDuplicate = issue(0, ValidationSeverity.WARNING, new ValidationIssueMetadata.Limit(1, 2));
        var differentMetadata = issue(0, ValidationSeverity.WARNING, new ValidationIssueMetadata.Limit(2, 2));

        var collector = new ValidationCollector()
                .add(first)
                .add(exactDuplicate)
                .add(differentMetadata);
        var result = collector.result();

        assertEquals(List.of(first, differentMetadata), result.issues());
        assertEquals(2, collector.retainedIdentityCount());
        assertFalse(result.truncated());
    }

    @Test
    void exactHardCapIsRetainedAndNextUniqueWarningIsTruncated() {
        var collector = warningCollectorAtCap();

        var atCap = collector.result();
        collector.add(issue(
                MagicSafetyCeilings.MAX_VALIDATION_ISSUES,
                ValidationSeverity.WARNING,
                ValidationIssueMetadata.none()));
        var overCap = collector.result();

        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, atCap.issues().size());
        assertFalse(atCap.truncated());
        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, overCap.issues().size());
        assertTrue(overCap.truncated());
        assertFalse(overCap.omittedError());
        assertTrue(overCap.isValid());
    }

    @Test
    void hiddenErrorAfterWarningsPreventsFalseAcceptance() {
        var collector = warningCollectorAtCap();
        collector.add(issue(
                MagicSafetyCeilings.MAX_VALIDATION_ISSUES,
                ValidationSeverity.ERROR,
                ValidationIssueMetadata.none()));

        var result = collector.result();

        assertTrue(result.truncated());
        assertTrue(result.omittedError());
        assertTrue(result.hasErrors());
        assertFalse(result.isValid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void floodDoesNotGrowRetainedIdentityTrackingPastHardCap() {
        var collector = new ValidationCollector();
        for (var index = 0; index < 20_000; index++) {
            collector.add(issue(index, ValidationSeverity.WARNING, ValidationIssueMetadata.none()));
        }
        collector.add(issue(20_000, ValidationSeverity.ERROR, ValidationIssueMetadata.none()));

        var result = collector.result();

        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, collector.retainedIdentityCount());
        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, result.issues().size());
        assertTrue(result.truncated());
        assertTrue(result.omittedError());
    }

    @Test
    void resultIsAnImmutableSnapshotOfCollectorState() {
        var collector = new ValidationCollector().add(issue(
                0, ValidationSeverity.WARNING, ValidationIssueMetadata.none()));
        var first = collector.result();
        collector.add(issue(1, ValidationSeverity.WARNING, ValidationIssueMetadata.none()));

        assertEquals(1, first.issues().size());
        assertEquals(2, collector.result().issues().size());
        assertThrows(UnsupportedOperationException.class, () -> first.issues().clear());
    }

    @Test
    void mergePreservesLeftThenRightOrderAndDeduplicatesAcrossSides() {
        var first = issue(0, ValidationSeverity.WARNING, ValidationIssueMetadata.none());
        var shared = issue(1, ValidationSeverity.ERROR, ValidationIssueMetadata.none());
        var third = issue(2, ValidationSeverity.WARNING, ValidationIssueMetadata.none());
        var left = new ValidationResult(List.of(first, shared), false, false);
        var right = new ValidationResult(List.of(shared, third), false, false);

        var merged = left.merge(right);

        assertEquals(List.of(first, shared, third), merged.issues());
        assertTrue(merged.hasErrors());
        assertFalse(merged.truncated());
    }

    @Test
    void mergePropagatesExistingHiddenErrorFlags() {
        var warnings = uniqueWarnings(MagicSafetyCeilings.MAX_VALIDATION_ISSUES);
        var left = new ValidationResult(warnings, true, true);

        var merged = left.merge(ValidationResult.valid());

        assertTrue(merged.truncated());
        assertTrue(merged.omittedError());
        assertTrue(merged.hasErrors());
    }

    @Test
    void mergeReappliesCapAndMarksAHiddenRightSideError() {
        var left = new ValidationResult(
                uniqueWarnings(MagicSafetyCeilings.MAX_VALIDATION_ISSUES),
                false,
                false);
        var hiddenRightError = ValidationResult.of(issue(
                MagicSafetyCeilings.MAX_VALIDATION_ISSUES,
                ValidationSeverity.ERROR,
                ValidationIssueMetadata.none()));

        var merged = left.merge(hiddenRightError);

        assertEquals(MagicSafetyCeilings.MAX_VALIDATION_ISSUES, merged.issues().size());
        assertTrue(merged.truncated());
        assertTrue(merged.omittedError());
        assertTrue(merged.hasErrors());
    }

    @Test
    void collectorRejectsNullInputs() {
        var collector = new ValidationCollector();

        assertThrows(NullPointerException.class, () -> collector.add((ValidationIssue) null));
        assertThrows(NullPointerException.class, () -> collector.add((ValidationResult) null));
    }

    private static ValidationCollector warningCollectorAtCap() {
        var collector = new ValidationCollector();
        for (var issue : uniqueWarnings(MagicSafetyCeilings.MAX_VALIDATION_ISSUES)) {
            collector.add(issue);
        }
        return collector;
    }

    private static List<ValidationIssue> uniqueWarnings(int count) {
        var issues = new ArrayList<ValidationIssue>(count);
        for (var index = 0; index < count; index++) {
            issues.add(issue(index, ValidationSeverity.WARNING, ValidationIssueMetadata.none()));
        }
        return issues;
    }

    private static ValidationIssue issue(
            int index,
            ValidationSeverity severity,
            ValidationIssueMetadata metadata) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", "collector.issue_" + index),
                severity,
                ValidationPath.empty().field("field"),
                metadata);
    }
}
