package com.yo1no.gramarye.magic.validation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationPrimitivesTest {
    @Test
    void resultWithoutIssuesIsValid() {
        var result = ValidationResult.valid();

        assertAll(
                () -> assertTrue(result.isValid()),
                () -> assertFalse(result.hasErrors()),
                () -> assertTrue(result.issues().isEmpty()),
                () -> assertTrue(result.errors().isEmpty()),
                () -> assertTrue(result.warnings().isEmpty()));
    }

    @Test
    void warningDoesNotInvalidateResult() {
        var warning = issue("definition.deprecated", ValidationSeverity.WARNING, "$.nodes[0]");
        var result = ValidationResult.of(warning);

        assertAll(
                () -> assertTrue(result.isValid()),
                () -> assertFalse(result.hasErrors()),
                () -> assertEquals(List.of(warning), result.warnings()),
                () -> assertTrue(result.errors().isEmpty()));
    }

    @Test
    void errorInvalidatesResult() {
        var error = issue("definition.invalid", ValidationSeverity.ERROR, "$.nodes[1]");
        var result = ValidationResult.of(error);

        assertAll(
                () -> assertFalse(result.isValid()),
                () -> assertTrue(result.hasErrors()),
                () -> assertEquals(List.of(error), result.errors()),
                () -> assertTrue(result.warnings().isEmpty()));
    }

    @Test
    void issueCollectionIsDefensivelyCopiedAndUnmodifiable() {
        var warning = issue("definition.warning", ValidationSeverity.WARNING, "$");
        var source = new ArrayList<>(List.of(warning));
        var result = new ValidationResult(source);

        source.clear();

        assertAll(
                () -> assertEquals(List.of(warning), result.issues()),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.issues().add(warning)),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.warnings().add(warning)));
    }

    @Test
    void mergePreservesLeftThenRightIssueOrder() {
        var first = issue("first", ValidationSeverity.WARNING, "$.first");
        var second = issue("second", ValidationSeverity.ERROR, "$.second");
        var third = issue("third", ValidationSeverity.WARNING, "$.third");
        var left = new ValidationResult(List.of(first, second));
        var right = ValidationResult.of(third);

        var merged = left.merge(right);

        assertEquals(List.of(first, second, third), merged.issues());
    }

    @Test
    void nullIssueFieldsAndCollectionsAreRejected() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue(null, ValidationSeverity.ERROR, "$", "message")),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue("code", null, "$", "message")),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue("code", ValidationSeverity.ERROR, null, "message")),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue("code", ValidationSeverity.ERROR, "$", null)),
                () -> assertThrows(NullPointerException.class, () -> new ValidationResult(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationResult(Arrays.asList((ValidationIssue) null))),
                () -> assertThrows(NullPointerException.class, () -> ValidationResult.of(null)),
                () -> assertThrows(NullPointerException.class, () -> ValidationResult.valid().merge(null)));
    }

    @Test
    void stableTextFieldsRejectBlankValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationIssue(" ", ValidationSeverity.ERROR, "$", "message")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationIssue("code", ValidationSeverity.ERROR, " ", "message")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationIssue("code", ValidationSeverity.ERROR, "$", " ")));
    }

    @Test
    void contextContainsOnlyValidatedPolicyLimits() {
        var context = new ValidationContext(MagicPolicyLimits.DEFAULTS);

        assertAll(
                () -> assertEquals(MagicPolicyLimits.DEFAULTS, context.policyLimits()),
                () -> assertThrows(NullPointerException.class, () -> new ValidationContext(null)));
    }

    private static ValidationIssue issue(String code, ValidationSeverity severity, String path) {
        return new ValidationIssue(code, severity, path, "Human-readable message");
    }
}
