package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillSubmissionInputTest {
    @Test
    void directPreservesDraftAndUsesARealEmptyReport() {
        var draft = SubmissionTestFixtures.draft(0, List.of(SubmissionTestFixtures.completeNode()));

        var input = SkillSubmissionInput.direct(draft);

        assertAll(
                () -> assertSame(draft, input.draft()),
                () -> assertTrue(input.readReport().facts().isEmpty()),
                () -> assertFalse(input.readReport().truncated()));
    }

    @Test
    void fromReadResultPreservesBothProvenanceReferences() {
        var draft = SubmissionTestFixtures.draft(0, List.of(SubmissionTestFixtures.completeNode()));
        var report = new SkillDraftReadReport(List.of(), true);
        var readResult = new SkillDraftReadResult(draft, report);

        var input = SkillSubmissionInput.fromReadResult(readResult);

        assertAll(
                () -> assertSame(draft, input.draft()),
                () -> assertSame(report, input.readReport()));
    }

    @Test
    void factoriesRejectNull() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionInput.direct(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionInput.fromReadResult(null)));
    }

    @Test
    void publicSurfaceHasNoArbitraryPairingPersistenceOrMutationApi() {
        var constructors = SkillSubmissionInput.class.getDeclaredConstructors();
        var methodNames = Arrays.stream(SkillSubmissionInput.class.getDeclaredMethods())
                .map(method -> method.getName())
                .toList();

        assertAll(
                () -> assertTrue(Modifier.isFinal(SkillSubmissionInput.class.getModifiers())),
                () -> assertFalse(SkillSubmissionInput.class.isRecord()),
                () -> assertEquals(1, constructors.length),
                () -> assertTrue(Modifier.isPrivate(constructors[0].getModifiers())),
                () -> assertFalse(methodNames.contains("of")),
                () -> assertFalse(methodNames.stream().anyMatch(name -> name.startsWith("set"))),
                () -> assertFalse(methodNames.stream().anyMatch(name -> name.contains("write"))),
                () -> assertFalse(methodNames.stream().anyMatch(name -> name.contains("codec"))));
    }

    @Test
    void toStringIsBoundedAndDoesNotExposeEnvelopePayload() {
        var secret = "unique-submission-input-secret";
        var draft = SubmissionTestFixtures.draft(0, List.of(SubmissionTestFixtures.completeNode(
                SubmissionTestFixtures.envelope("secret_trigger", secret),
                SubmissionTestFixtures.envelope("secret_action", secret),
                com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument.none())));

        var rendered = SkillSubmissionInput.direct(draft).toString();

        assertAll(
                () -> assertFalse(rendered.contains(secret)),
                () -> assertFalse(rendered.contains("test:secret_trigger")),
                () -> assertFalse(rendered.contains(SubmissionTestFixtures.SKILL_ID.toString())),
                () -> assertTrue(rendered.length() < 256));
    }
}
