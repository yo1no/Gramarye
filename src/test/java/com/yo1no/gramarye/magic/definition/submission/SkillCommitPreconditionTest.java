package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkillCommitPreconditionTest {
    @Test
    void variantsRejectNullAndDeriveTheirSkillIdentity() {
        var latest = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(7));
        var absent = new SkillCommitPrecondition.ExpectedAbsent(
                SubmissionPreparationTestFixtures.SKILL_ID);
        var expectedLatest = new SkillCommitPrecondition.ExpectedLatest(latest);

        assertAll(
                () -> assertSame(SubmissionPreparationTestFixtures.SKILL_ID, absent.skillId()),
                () -> assertSame(latest, expectedLatest.latest()),
                () -> assertSame(SubmissionPreparationTestFixtures.SKILL_ID,
                        expectedLatest.skillId()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillCommitPrecondition.ExpectedAbsent(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillCommitPrecondition.ExpectedLatest(null)));
    }

    @Test
    void variantsUseRecordValueEquality() {
        var firstLatest = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(3));
        var equalLatest = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(3));
        var firstAbsent = new SkillCommitPrecondition.ExpectedAbsent(
                SubmissionPreparationTestFixtures.SKILL_ID);
        var equalAbsent = new SkillCommitPrecondition.ExpectedAbsent(
                SubmissionPreparationTestFixtures.SKILL_ID);
        var firstExpected = new SkillCommitPrecondition.ExpectedLatest(firstLatest);
        var equalExpected = new SkillCommitPrecondition.ExpectedLatest(equalLatest);

        assertAll(
                () -> assertEquals(firstAbsent, equalAbsent),
                () -> assertEquals(firstAbsent.hashCode(), equalAbsent.hashCode()),
                () -> assertEquals(firstExpected, equalExpected),
                () -> assertEquals(firstExpected.hashCode(), equalExpected.hashCode()));
    }

    @Test
    void factoryMapsNewAndExistingStateWithoutCopyingIdentityValues() {
        var latest = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(19));
        var newState = new AuthorizedSkillState.New(
                SubmissionPreparationTestFixtures.SKILL_ID);
        var existingState = new AuthorizedSkillState.Existing(latest);

        var absent = assertInstanceOf(
                SkillCommitPrecondition.ExpectedAbsent.class,
                SkillCommitPreconditionFactory.from(newState));
        var expectedLatest = assertInstanceOf(
                SkillCommitPrecondition.ExpectedLatest.class,
                SkillCommitPreconditionFactory.from(existingState));

        assertAll(
                () -> assertSame(newState.skillId(), absent.skillId()),
                () -> assertSame(latest, expectedLatest.latest()),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillCommitPreconditionFactory.from(null)));
    }

    @Test
    void maximumLatestIsAStandaloneCasValueAndDoesNotProposeARevision() {
        var latest = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(Integer.MAX_VALUE));
        var first = new SkillCommitPrecondition.ExpectedLatest(latest);
        var equal = new SkillCommitPrecondition.ExpectedLatest(new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(Integer.MAX_VALUE)));

        assertAll(
                () -> assertSame(latest, first.latest()),
                () -> assertSame(latest.skillId(), first.skillId()),
                () -> assertEquals(first, equal),
                () -> assertEquals(Set.of("skillId"), componentNames(
                        SkillCommitPrecondition.ExpectedAbsent.class)),
                () -> assertEquals(Set.of("latest"), componentNames(
                        SkillCommitPrecondition.ExpectedLatest.class)),
                () -> assertFalse(Arrays.stream(SkillCommitPrecondition.class.getMethods())
                        .map(method -> method.getName().toLowerCase())
                        .anyMatch(name -> name.contains("owner")
                                || name.contains("document")
                                || name.contains("proposal")
                                || name.contains("store")
                                || name.contains("codec"))),
                () -> assertFalse(Arrays.stream(first.getClass().getDeclaredMethods())
                        .map(method -> method.getName().toLowerCase())
                        .anyMatch(name -> name.contains("propose")
                                || name.contains("allocate")
                                || name.contains("nextrevision"))));
    }

    private static Set<String> componentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
    }
}
