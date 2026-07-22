package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class P3C1ApiGateTest {
    @Test
    void p3C1TypesRetainTheirVisibilityAfterP3C2AddsItsPublicApi() {
        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillSubmissionInput.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillSubmissionInput.class.getModifiers())),
                () -> assertTrue(Modifier.isPublic(SkillSubmissionIssueCodes.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillSubmissionIssueCodes.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftSubmissionPrecheck.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftSubmissionPrechecker.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftFormalizationResult.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftFormalizer.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftReadFactMapper.class.getModifiers())),
                () -> assertFalse(Modifier.isPublic(DraftSubmissionPaths.class.getModifiers())));
    }

    @Test
    void issueVocabularyContainsOnlyTheThreeDraftSpecificCodes() {
        var publicCodeFields = Arrays.stream(SkillSubmissionIssueCodes.class.getFields())
                .filter(field -> field.getType() == ValidationIssueCode.class)
                .collect(Collectors.toMap(field -> field.getName(), field -> {
                    try {
                        return (ValidationIssueCode) field.get(null);
                    } catch (IllegalAccessException exception) {
                        throw new AssertionError(exception);
                    }
                }));

        assertAll(
                () -> assertEquals(Set.of(
                                "DRAFT_UNSUPPORTED_SCHEMA",
                                "DRAFT_TRIGGER_MISSING",
                                "DRAFT_ACTION_MISSING"),
                        publicCodeFields.keySet()),
                () -> assertEquals("gramarye:draft.unsupported_schema",
                        publicCodeFields.get("DRAFT_UNSUPPORTED_SCHEMA").toString()),
                () -> assertEquals("gramarye:draft.trigger_missing",
                        publicCodeFields.get("DRAFT_TRIGGER_MISSING").toString()),
                () -> assertEquals("gramarye:draft.action_missing",
                        publicCodeFields.get("DRAFT_ACTION_MISSING").toString()));
    }

    @Test
    void existingMetadataVocabularySuppliesNoneAndSchemaWithoutParallelTypes() {
        var schema = new ValidationIssueMetadata.Schema(5, 0);

        assertAll(
                () -> assertInstanceOf(
                        ValidationIssueMetadata.None.class,
                        ValidationIssueMetadata.none()),
                () -> assertEquals(5, schema.actual()),
                () -> assertEquals(0, schema.expected()));
    }
}
