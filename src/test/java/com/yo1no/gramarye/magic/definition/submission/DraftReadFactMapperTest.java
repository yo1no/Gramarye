package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DraftReadFactMapperTest {
    @Test
    void everyReadFactCodeMapsToTheExistingB3IssueCodeInSourceOrder() {
        var codes = List.of(ReadFactCode.values());
        var facts = codes.stream()
                .map(code -> fact(
                        code,
                        ReadLocationKind.DRAFT_APPEARANCE,
                        OptionalInt.empty(),
                        Optional.empty()))
                .toList();

        var result = map(facts, false, 1);

        assertEquals(List.of(
                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_LOW,
                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                SkillValidationIssueCodes.READ_LEGACY_NULL_PROFILE_NORMALIZED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_SCALAR_NORMALIZED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_APPEARANCE_DEFAULTED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_OVERRIDE_NORMALIZED,
                SkillValidationIssueCodes.READ_UNKNOWN_APPEARANCE_FIELD_IGNORED),
                result.issues().stream().map(issue -> issue.code()).toList());
        assertTrue(result.issues().stream()
                .allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
        assertTrue(result.issues().stream()
                .allMatch(issue -> issue.metadata().equals(ValidationIssueMetadata.none())));
    }

    @Test
    void draftLocationsUseTypedRootAndNodeOverridePaths() {
        var facts = List.of(
                fact(ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED,
                        ReadLocationKind.DRAFT_APPEARANCE,
                        OptionalInt.empty(), Optional.empty()),
                fact(ReadFactCode.LEGACY_NULL_OVERRIDE_NORMALIZED,
                        ReadLocationKind.DRAFT_NODE_APPEARANCE_OVERRIDE,
                        OptionalInt.of(1), Optional.empty()));

        var result = map(facts, false, 2);

        assertEquals(List.of("appearance", "nodes[1].appearance_override"),
                result.issues().stream().map(issue -> issue.path().render()).toList());
    }

    @Test
    void everyAppearanceFieldUsesItsCanonicalSuffix() {
        var facts = List.of(AppearanceField.values()).stream()
                .map(field -> fact(
                        ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED,
                        ReadLocationKind.DRAFT_APPEARANCE,
                        OptionalInt.empty(),
                        Optional.of(field)))
                .toList();

        var result = map(facts, false, 1);

        assertEquals(List.of(
                "appearance.primary_argb",
                "appearance.secondary_argb",
                "appearance.sound_profile",
                "appearance.particle_profile",
                "appearance.trail_profile",
                "appearance.intensity_milli"),
                result.issues().stream().map(issue -> issue.path().render()).toList());
    }

    @Test
    void readReportTruncationAddsTheLastWarningWithoutSettingCollectorTruncation() {
        var result = map(List.of(fact(
                ReadFactCode.INTENSITY_CLAMPED_LOW,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(),
                Optional.of(AppearanceField.INTENSITY_MILLI))), true, 1);

        assertAll(
                () -> assertEquals(2, result.issues().size()),
                () -> assertEquals(
                        SkillValidationIssueCodes.READ_REPORT_TRUNCATED,
                        result.issues().get(1).code()),
                () -> assertEquals("", result.issues().get(1).path().render()),
                () -> assertFalse(result.truncated()),
                () -> assertFalse(result.omittedError()));
    }

    @Test
    void documentLocationsAreProgrammingInvariantViolations() {
        assertAll(
                () -> assertInvariantViolation(fact(
                        ReadFactCode.INTENSITY_CLAMPED_LOW,
                        ReadLocationKind.SKILL_APPEARANCE,
                        OptionalInt.empty(), Optional.empty()), 1),
                () -> assertInvariantViolation(fact(
                        ReadFactCode.INTENSITY_CLAMPED_LOW,
                        ReadLocationKind.SKILL_NODE_APPEARANCE_OVERRIDE,
                        OptionalInt.of(0), Optional.empty()), 1));
    }

    @Test
    void malformedDraftLocationsAreProgrammingInvariantViolations() {
        assertAll(
                () -> assertInvariantViolation(fact(
                        ReadFactCode.INTENSITY_CLAMPED_LOW,
                        ReadLocationKind.DRAFT_APPEARANCE,
                        OptionalInt.of(0), Optional.empty()), 1),
                () -> assertInvariantViolation(fact(
                        ReadFactCode.INTENSITY_CLAMPED_LOW,
                        ReadLocationKind.DRAFT_NODE_APPEARANCE_OVERRIDE,
                        OptionalInt.empty(), Optional.empty()), 1),
                () -> assertInvariantViolation(fact(
                        ReadFactCode.INTENSITY_CLAMPED_LOW,
                        ReadLocationKind.DRAFT_NODE_APPEARANCE_OVERRIDE,
                        OptionalInt.of(1), Optional.empty()), 1),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> DraftSubmissionPaths.trigger(-1)));
    }

    @Test
    void unknownAppearanceFieldFactRetainsNoUnknownNameOrRawData() {
        var result = map(List.of(fact(
                ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(), Optional.empty())), false, 1);
        var issue = result.issues().getFirst();

        assertAll(
                () -> assertEquals(
                        SkillValidationIssueCodes.READ_UNKNOWN_APPEARANCE_FIELD_IGNORED,
                        issue.code()),
                () -> assertEquals("appearance", issue.path().render()),
                () -> assertEquals(ValidationIssueMetadata.none(), issue.metadata()),
                () -> assertFalse(issue.toString().contains("unknown_field_name")));
    }

    @Test
    void mapperRejectsNullBoundaries() {
        var input = input(List.of(), false, 1);
        var collector = new ValidationCollector();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> DraftReadFactMapper.append(null, collector)),
                () -> assertThrows(NullPointerException.class,
                        () -> DraftReadFactMapper.append(input, null)));
    }

    private static ValidationResult map(List<ReadFact> facts, boolean truncated, int nodeCount) {
        var collector = new ValidationCollector();
        DraftReadFactMapper.append(input(facts, truncated, nodeCount), collector);
        return collector.result();
    }

    private static SkillSubmissionInput input(
            List<ReadFact> facts,
            boolean truncated,
            int nodeCount) {
        var nodes = java.util.stream.IntStream.range(0, nodeCount)
                .mapToObj(ignored -> SubmissionTestFixtures.completeNode())
                .toList();
        var draft = SubmissionTestFixtures.draft(0, nodes);
        return SkillSubmissionInput.fromReadResult(new SkillDraftReadResult(
                draft, new SkillDraftReadReport(facts, truncated)));
    }

    private static void assertInvariantViolation(ReadFact fact, int nodeCount) {
        assertThrows(IllegalStateException.class,
                () -> DraftReadFactMapper.append(
                        input(List.of(fact), false, nodeCount),
                        new ValidationCollector()));
    }

    private static ReadFact fact(
            ReadFactCode code,
            ReadLocationKind location,
            OptionalInt nodeIndex,
            Optional<AppearanceField> field) {
        return new ReadFact(code, location, nodeIndex, field);
    }
}
