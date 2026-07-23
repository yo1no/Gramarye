package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkillSubmissionPlanTest {
    @Test
    void newPreparedTokenProducesIdentityPreservingAbsentPlan() {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty());
        var prepared = prepare(SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft)));

        var plan = SkillSubmissionPlan.from(prepared);
        var absent = assertInstanceOf(
                SkillCommitPrecondition.ExpectedAbsent.class,
                plan.precondition());

        assertAll(
                () -> assertSame(prepared.authority().authorization().owner(), plan.owner()),
                () -> assertSame(prepared.proposedDocument(), plan.proposedDocument()),
                () -> assertSame(prepared.validatedDefinition(), plan.validatedDefinition()),
                () -> assertSame(prepared.proposedDocument().skillId(), absent.skillId()),
                () -> assertEquals(new SkillRevision(0),
                        plan.proposedDocument().revision()));
    }

    @Test
    void existingPreparedTokenProducesIdentityPreservingLatestPlan() {
        var latestRevision = 12;
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(latestRevision)));
        var prepared = prepare(SubmissionPreparationTestFixtures.passedExisting(
                SkillSubmissionInput.direct(draft), latestRevision));
        var state = assertInstanceOf(
                AuthorizedSkillState.Existing.class,
                prepared.authority().authorization().state());

        var plan = SkillSubmissionPlan.from(prepared);
        var expectedLatest = assertInstanceOf(
                SkillCommitPrecondition.ExpectedLatest.class,
                plan.precondition());

        assertAll(
                () -> assertSame(prepared.authority().authorization().owner(), plan.owner()),
                () -> assertSame(state.latestStoredRevision(), expectedLatest.latest()),
                () -> assertSame(prepared.proposedDocument(), plan.proposedDocument()),
                () -> assertSame(prepared.validatedDefinition(), plan.validatedDefinition()),
                () -> assertEquals(new SkillRevision(latestRevision + 1),
                        plan.proposedDocument().revision()));
    }

    @Test
    void repeatedConstructionUsesObjectIdentityAndPreservesDeterministicFields() {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty());
        var prepared = prepare(SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft)));

        var first = SkillSubmissionPlan.from(prepared);
        var second = SkillSubmissionPlan.from(prepared);

        assertAll(
                () -> assertNotSame(first, second),
                () -> assertNotEquals(first, second),
                () -> assertSame(first.owner(), second.owner()),
                () -> assertEquals(first.precondition(), second.precondition()),
                () -> assertSame(first.proposedDocument(), second.proposedDocument()),
                () -> assertSame(first.validatedDefinition(), second.validatedDefinition()),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSubmissionPlan.from(null)));
    }

    @Test
    void constructionAndPublicSurfaceRemainControlledAndImmutable() throws Exception {
        var constructors = Arrays.asList(SkillSubmissionPlan.class.getDeclaredConstructors());
        var from = SkillSubmissionPlan.class.getDeclaredMethod(
                "from", SubmissionPreparationCheck.Prepared.class);
        var publicDeclaredMethods = Arrays.stream(SkillSubmissionPlan.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        var fields = Arrays.asList(SkillSubmissionPlan.class.getDeclaredFields());
        var declaredMethodNames = Arrays.stream(SkillSubmissionPlan.class.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(Modifier.isPublic(SkillSubmissionPlan.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(SkillSubmissionPlan.class.getModifiers())),
                () -> assertEquals(1, constructors.size()),
                () -> assertTrue(Modifier.isPrivate(constructors.getFirst().getModifiers())),
                () -> assertTrue(Modifier.isStatic(from.getModifiers())),
                () -> assertFalse(Modifier.isPublic(from.getModifiers())),
                () -> assertFalse(Modifier.isProtected(from.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(from.getModifiers())),
                () -> assertEquals(Set.of(
                                "owner",
                                "precondition",
                                "proposedDocument",
                                "validatedDefinition"),
                        publicDeclaredMethods),
                () -> assertTrue(fields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertFalse(declaredMethodNames.contains("reference")),
                () -> assertFalse(declaredMethodNames.contains("report")),
                () -> assertFalse(declaredMethodNames.contains("equals")),
                () -> assertFalse(declaredMethodNames.contains("hashCode")),
                () -> assertFalse(declaredMethodNames.contains("toString")));
    }

    private static SubmissionPreparationCheck.Prepared prepare(
            SubmissionAuthorityCheck.Passed authority) {
        return assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));
    }
}
