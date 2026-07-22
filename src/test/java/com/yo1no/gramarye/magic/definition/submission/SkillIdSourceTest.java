package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillIdSourceTest {
    @Test
    void injectableFakeCanReturnDistinctServerSideCandidates() {
        var expected = List.of(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                SubmissionAuthorityTestFixtures.OTHER_SKILL_ID);
        var candidates = new ArrayDeque<>(expected);
        SkillIdSource source = candidates::removeFirst;

        assertEquals(expected, List.of(source.nextSkillId(), source.nextSkillId()));
    }

    @Test
    void repeatedCandidateIsNotRetriedOrReservedByTheC2Port() {
        SkillId repeated = SubmissionAuthorityTestFixtures.SKILL_ID;
        SkillIdSource source = () -> repeated;

        assertSame(source.nextSkillId(), source.nextSkillId());
    }
}
