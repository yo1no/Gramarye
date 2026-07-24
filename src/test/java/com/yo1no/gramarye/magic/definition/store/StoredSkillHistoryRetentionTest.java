package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class StoredSkillHistoryRetentionTest {
    @Test
    void retainingEveryRevisionReturnsTheOriginalHistory() {
        var history = history(0, 2, 5);
        assertSame(history, history.retainRevisions(Set.of(revision(0), revision(2), revision(5))));
    }

    @Test
    void retainingSubsetBuildsSortedImmutableHistoryWithSharedTruthValues() {
        var history = history(0, 2, 5);
        var retained = history.retainRevisions(Set.of(revision(5), revision(0)));

        assertEquals(java.util.List.of(revision(0), revision(5)),
                retained.revisions().keySet().stream().toList());
        assertSame(history.owner(), retained.owner());
        assertSame(history.revisions().get(revision(0)), retained.revisions().get(revision(0)));
        assertSame(history.revisions().get(revision(5)), retained.revisions().get(revision(5)));
        assertEquals(java.util.List.of(revision(0), revision(2), revision(5)),
                history.revisions().keySet().stream().toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> retained.revisions().put(
                        revision(6), StoreTestFixtures.document(StoreTestFixtures.skillId(1), 6)));
    }

    @Test
    void helperRejectsNullEmptyMissingAndLatestOmission() {
        var history = history(0, 2, 5);
        var withNull = new HashSet<SkillRevision>();
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> history.retainRevisions(null));
        assertThrows(IllegalArgumentException.class, () -> history.retainRevisions(Set.of()));
        assertThrows(NullPointerException.class, () -> history.retainRevisions(withNull));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.retainRevisions(Set.of(revision(0), revision(7))));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.retainRevisions(Set.of(
                        revision(0), revision(2), revision(5), revision(7))));
        assertThrows(
                IllegalArgumentException.class,
                () -> history.retainRevisions(Set.of(revision(0), revision(2))));
    }

    private static StoredSkillHistory history(int... revisionValues) {
        var skillId = StoreTestFixtures.skillId(1);
        var revisions = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        for (var value : revisionValues) {
            revisions.put(revision(value), StoreTestFixtures.document(skillId, value));
        }
        return new StoredSkillHistory(StoreTestFixtures.ownerId(1), revisions);
    }

    private static SkillRevision revision(int value) {
        return StoreTestFixtures.revision(value);
    }
}
