package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillStoreCommitConflictTest {
    @Test
    void conflictVocabularyHasExactlyThreeVariants() {
        assertEquals(Set.of(
                        SkillStoreCommitConflict.ExpectedAbsentButPresent.class,
                        SkillStoreCommitConflict.ExpectedLatestButAbsent.class,
                        SkillStoreCommitConflict.LatestMismatch.class),
                Set.of(SkillStoreCommitConflict.class.getPermittedSubclasses()));
    }

    @Test
    void absentAndLatestConflictsPreserveTheirTypedIdentity() {
        var skillId = StoreTestFixtures.skillId(1);
        var expected = reference(1, 4);
        var present = new SkillStoreCommitConflict.ExpectedAbsentButPresent(skillId);
        var absent = new SkillStoreCommitConflict.ExpectedLatestButAbsent(expected);

        assertAll(
                () -> assertSame(skillId, present.skillId()),
                () -> assertSame(expected, absent.expected()),
                () -> assertSame(expected.skillId(), absent.skillId()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitConflict.ExpectedAbsentButPresent(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitConflict.ExpectedLatestButAbsent(null)));
    }

    @Test
    void latestMismatchRequiresSameIdentityAndDifferentButUnorderedRevisions() {
        var expected = reference(2, 5);
        var lowerObserved = reference(2, 3);
        var higherObserved = reference(2, 7);
        var lower = new SkillStoreCommitConflict.LatestMismatch(expected, lowerObserved);
        var higher = new SkillStoreCommitConflict.LatestMismatch(expected, higherObserved);

        assertAll(
                () -> assertSame(expected, lower.expected()),
                () -> assertSame(lowerObserved, lower.observed()),
                () -> assertEquals(expected.skillId(), lower.skillId()),
                () -> assertSame(higherObserved, higher.observed()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitConflict.LatestMismatch(null, lowerObserved)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitConflict.LatestMismatch(expected, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitConflict.LatestMismatch(
                                expected, reference(3, 7))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitConflict.LatestMismatch(
                                expected, reference(2, 5))));
    }

    private static SkillReference reference(long skillId, int revision) {
        return new SkillReference(
                StoreTestFixtures.skillId(skillId),
                StoreTestFixtures.revision(revision));
    }
}
