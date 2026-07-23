package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import org.junit.jupiter.api.Test;

class SubmissionRevisionProposerTest {
    @Test
    void newSkillProposesRevisionZero() {
        var state = new AuthorizedSkillState.New(SubmissionPreparationTestFixtures.SKILL_ID);

        var proposed = assertInstanceOf(
                SubmissionRevisionProposal.Proposed.class,
                SubmissionRevisionProposer.propose(state));

        assertEquals(new SkillRevision(0), proposed.revision());
    }

    @Test
    void existingSkillProposesTheImmediateSuccessor() {
        var zero = existing(0);
        var arbitrary = existing(41);
        var almostMaximum = existing(Integer.MAX_VALUE - 1);

        var afterZero = assertInstanceOf(
                SubmissionRevisionProposal.Proposed.class,
                SubmissionRevisionProposer.propose(zero));
        var afterArbitrary = assertInstanceOf(
                SubmissionRevisionProposal.Proposed.class,
                SubmissionRevisionProposer.propose(arbitrary));
        var afterAlmostMaximum = assertInstanceOf(
                SubmissionRevisionProposal.Proposed.class,
                SubmissionRevisionProposer.propose(almostMaximum));

        assertAll(
                () -> assertEquals(new SkillRevision(1), afterZero.revision()),
                () -> assertEquals(new SkillRevision(42), afterArbitrary.revision()),
                () -> assertEquals(
                        new SkillRevision(Integer.MAX_VALUE),
                        afterAlmostMaximum.revision()));
    }

    @Test
    void maximumRevisionExhaustsBeforeAnyAddition() {
        var state = existing(Integer.MAX_VALUE);
        var latest = state.latestStoredRevision();

        var exhausted = assertInstanceOf(
                SubmissionRevisionProposal.Exhausted.class,
                SubmissionRevisionProposer.propose(state));

        assertSame(latest, exhausted.latest());
    }

    @Test
    void repeatedProposalIsDeterministicAndDoesNotMutateState() {
        var state = existing(25);
        var before = state.latestStoredRevision();

        var first = SubmissionRevisionProposer.propose(state);
        var second = SubmissionRevisionProposer.propose(state);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertSame(before, state.latestStoredRevision()),
                () -> assertEquals(new SkillRevision(25), state.latestStoredRevision().revision()));
    }

    @Test
    void proposalModelsRejectNullAndInvalidExhaustion() {
        var nonMax = new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID, new SkillRevision(1));

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> SubmissionRevisionProposer.propose(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionRevisionProposal.Proposed(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SubmissionRevisionProposal.Exhausted(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SubmissionRevisionProposal.Exhausted(nonMax)));
    }

    private static AuthorizedSkillState.Existing existing(int revision) {
        return new AuthorizedSkillState.Existing(new SkillReference(
                SubmissionPreparationTestFixtures.SKILL_ID,
                new SkillRevision(revision)));
    }
}
