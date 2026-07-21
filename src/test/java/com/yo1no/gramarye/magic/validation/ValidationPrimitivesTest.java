package com.yo1no.gramarye.magic.validation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ValidationPrimitivesTest {
    @Test
    void issueCodeUsesBoundedResourceLocationIdentity() {
        var expected = ResourceLocation.fromNamespaceAndPath("gramarye", "skill.empty_nodes");
        var code = ValidationIssueCode.fromNamespaceAndPath("gramarye", "skill.empty_nodes");

        assertAll(
                () -> assertEquals(expected, code.value()),
                () -> assertEquals("gramarye:skill.empty_nodes", code.toString()),
                () -> assertEquals(code, new ValidationIssueCode(expected)),
                () -> assertThrows(NullPointerException.class, () -> new ValidationIssueCode(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath(null, "path")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath("", "path")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath("gramarye", "")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath("Bad Namespace", "path")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath("gramarye", "Bad Path")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath(
                                "a".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH + 1), "path")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationIssueCode.fromNamespaceAndPath(
                                "g", "a".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH))));
    }

    @Test
    void validationPathRendersEmptyFieldIndexAndMixedShapes() {
        assertAll(
                () -> assertEquals("", ValidationPath.empty().render()),
                () -> assertEquals("nodes", ValidationPath.empty().field("nodes").render()),
                () -> assertEquals("[3]", ValidationPath.empty().index(3).render()),
                () -> assertEquals(
                        "nodes[3].action.payload.source_node",
                        ValidationPath.empty()
                                .field("nodes")
                                .index(3)
                                .field("action")
                                .field("payload")
                                .field("source_node")
                                .render()),
                () -> assertEquals("nodes[3]", ValidationPath.empty().field("nodes").index(3).toString()));
    }

    @Test
    void validationPathIsImmutableAndRejectsUnboundedOrAmbiguousSegments() {
        var source = new ArrayList<ValidationPathSegment>();
        source.add(new ValidationPathSegment.Field("nodes"));
        var path = new ValidationPath(source);
        source.clear();
        var maximumSegments = IntStream.range(0, MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS)
                .mapToObj(ValidationPathSegment.Index::new)
                .map(ValidationPathSegment.class::cast)
                .toList();

        assertAll(
                () -> assertEquals("nodes", path.render()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> path.segments().add(new ValidationPathSegment.Field("other"))),
                () -> assertDoesNotThrow(() -> new ValidationPath(maximumSegments)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPath(IntStream.rangeClosed(
                                        0, MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS)
                                .mapToObj(ValidationPathSegment.Index::new)
                                .map(ValidationPathSegment.class::cast)
                                .toList())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPathSegment.Field(" ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPathSegment.Field("payload.source")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPathSegment.Field("payload[0]")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPathSegment.Field("payload\nsource")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationPathSegment.Index(-1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ValidationPath.empty()
                                .field("a".repeat(600))
                                .field("b".repeat(600))));
    }

    @Test
    void metadataIsClosedImmutableAndMachineReadable() {
        var limit = new ValidationIssueMetadata.Limit(-1, 64);
        var schema = new ValidationIssueMetadata.Schema(1, 2);
        var reference = new ValidationIssueMetadata.Reference(3, -1);
        var exception = ValidationIssueMetadata.ExceptionClass.from(IllegalStateException.class);

        assertAll(
                () -> assertEquals(ValidationIssueMetadata.None.INSTANCE, ValidationIssueMetadata.none()),
                () -> assertEquals(new ValidationIssueMetadata.Limit(-1, 64), limit),
                () -> assertEquals(new ValidationIssueMetadata.Schema(1, 2), schema),
                () -> assertEquals(new ValidationIssueMetadata.Reference(3, -1), reference),
                () -> assertEquals(IllegalStateException.class.getName(), exception.className()),
                () -> assertEquals(
                        exception,
                        ValidationIssueMetadata.ExceptionClass.from(IllegalStateException.class)),
                () -> assertTrue(ValidationIssueMetadata.class.isSealed()),
                () -> assertEquals(5, ValidationIssueMetadata.class.getPermittedSubclasses().length),
                () -> assertTrue(Arrays.stream(
                                ValidationIssueMetadata.ExceptionClass.class.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()))),
                () -> assertFalse(Arrays.stream(
                                ValidationIssueMetadata.ExceptionClass.class.getMethods())
                        .anyMatch(method -> method.getName().toLowerCase().contains("message"))),
                () -> assertThrows(NullPointerException.class,
                        () -> ValidationIssueMetadata.ExceptionClass.from(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationIssueMetadata.Schema(-1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationIssueMetadata.Reference(-1, 0)));
    }

    @Test
    void validationIssueHasNoHumanDiagnosticField() {
        var issue = issue("test.issue", ValidationSeverity.ERROR, ValidationIssueMetadata.none());

        assertAll(
                () -> assertEquals(ValidationSeverity.ERROR, issue.severity()),
                () -> assertEquals("field", issue.path().render()),
                () -> assertFalse(Arrays.stream(ValidationIssue.class.getRecordComponents())
                        .anyMatch(component -> component.getName().equals("message"))),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue(null, issue.severity(), issue.path(), issue.metadata())),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue(issue.code(), null, issue.path(), issue.metadata())),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue(issue.code(), issue.severity(), null, issue.metadata())),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationIssue(issue.code(), issue.severity(), issue.path(), null)));
    }

    @Test
    void validationResultTracksRetainedAndOmittedErrors() {
        var warning = issue("test.warning", ValidationSeverity.WARNING, ValidationIssueMetadata.none());
        var error = issue("test.error", ValidationSeverity.ERROR, ValidationIssueMetadata.none());
        var valid = ValidationResult.valid();
        var warningOnly = ValidationResult.of(warning);
        var errorResult = ValidationResult.of(error);
        var omitted = new ValidationResult(List.of(warning), true, true);

        assertAll(
                () -> assertTrue(valid.isValid()),
                () -> assertFalse(valid.hasErrors()),
                () -> assertTrue(warningOnly.isValid()),
                () -> assertEquals(List.of(warning), warningOnly.warnings()),
                () -> assertFalse(errorResult.isValid()),
                () -> assertEquals(List.of(error), errorResult.errors()),
                () -> assertTrue(omitted.hasErrors()),
                () -> assertTrue(omitted.errors().isEmpty()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationResult(List.of(), false, true)));
    }

    @Test
    void validationResultDefensivelyCopiesAndRejectsNonCanonicalCollections() {
        var warning = issue("test.warning", ValidationSeverity.WARNING, ValidationIssueMetadata.none());
        var source = new ArrayList<>(List.of(warning));
        var result = new ValidationResult(source, false, false);
        source.clear();

        assertAll(
                () -> assertEquals(List.of(warning), result.issues()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.issues().add(warning)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> result.warnings().add(warning)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationResult(null, false, false)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ValidationResult(Arrays.asList((ValidationIssue) null), false, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ValidationResult(List.of(warning, warning), false, false)),
                () -> assertThrows(NullPointerException.class, () -> ValidationResult.of(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> ValidationResult.valid().merge(null)));
    }

    @Test
    void contextStillContainsOnlyValidatedPolicyLimits() {
        var context = new ValidationContext(MagicPolicyLimits.DEFAULTS);

        assertAll(
                () -> assertEquals(MagicPolicyLimits.DEFAULTS, context.policyLimits()),
                () -> assertThrows(NullPointerException.class, () -> new ValidationContext(null)));
    }

    private static ValidationIssue issue(
            String code,
            ValidationSeverity severity,
            ValidationIssueMetadata metadata) {
        return new ValidationIssue(
                ValidationIssueCode.fromNamespaceAndPath("gramarye", code),
                severity,
                ValidationPath.empty().field("field"),
                metadata);
    }
}
