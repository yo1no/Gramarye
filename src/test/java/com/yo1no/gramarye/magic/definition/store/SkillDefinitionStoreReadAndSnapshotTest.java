package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillDefinitionStoreReadAndSnapshotTest {
    @Test
    void emptyStoreAndEmptySnapshotAreLegal() {
        var owner = StoreTestFixtures.ownerId(1);
        var skillId = StoreTestFixtures.skillId(1);
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var empty = new SkillDefinitionStore();
        var snapshot = empty.snapshot();
        var restored = StoreTestFixtures.restore(snapshot);

        assertAll(
                () -> assertTrue(empty.find(reference).isEmpty()),
                () -> assertTrue(empty.latestReference(skillId).isEmpty()),
                () -> assertTrue(empty.ownerOf(skillId).isEmpty()),
                () -> assertEquals(0, empty.committedSkillCount(owner)),
                () -> assertTrue(snapshot.histories().isEmpty()),
                () -> assertTrue(restored.snapshot().histories().isEmpty()));
    }

    @Test
    void exactReadsUseSparseHistoryAndDeriveLatestFromMaximumKey() {
        var skillId = StoreTestFixtures.skillId(7);
        var owner = StoreTestFixtures.ownerId(11);
        var snapshot = StoreTestFixtures.snapshot(StoreTestFixtures.history(skillId, owner, 5, 0, 2));
        var store = StoreTestFixtures.restore(snapshot);
        var revisionTwo = snapshot.histories().getFirst().revisions().get(2).document();

        assertAll(
                () -> assertSame(revisionTwo,
                        store.find(new SkillReference(skillId, StoreTestFixtures.revision(2))).orElseThrow()),
                () -> assertTrue(store.find(
                                new SkillReference(skillId, StoreTestFixtures.revision(1)))
                        .isEmpty()),
                () -> assertEquals(
                        new SkillReference(skillId, StoreTestFixtures.revision(5)),
                        store.latestReference(skillId).orElseThrow()),
                () -> assertSame(owner, store.ownerOf(skillId).orElseThrow()),
                () -> assertEquals(1, store.committedSkillCount(owner)));
    }

    @Test
    void aHistoryContainingOnlyARevisionOtherThanZeroIsLegal() {
        var skillId = StoreTestFixtures.skillId(8);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(3), 5)));

        assertEquals(
                new SkillReference(skillId, StoreTestFixtures.revision(5)),
                store.latestReference(skillId).orElseThrow());
    }

    @Test
    void ownerCountCountsDistinctHistoriesRatherThanRevisions() {
        var owner = StoreTestFixtures.ownerId(20);
        var otherOwner = StoreTestFixtures.ownerId(21);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(StoreTestFixtures.skillId(1), owner, 0, 2, 5),
                StoreTestFixtures.history(StoreTestFixtures.skillId(2), owner, 0),
                StoreTestFixtures.history(StoreTestFixtures.skillId(3), otherOwner, 0, 1)));

        assertAll(
                () -> assertEquals(2, store.committedSkillCount(owner)),
                () -> assertEquals(1, store.committedSkillCount(otherOwner)),
                () -> assertEquals(0, store.committedSkillCount(StoreTestFixtures.ownerId(99))));
    }

    @Test
    void snapshotOrderingIsCanonicalAndIndependentOfInputOrder() {
        var owner = StoreTestFixtures.ownerId(1);
        var skillOne = StoreTestFixtures.skillId(1);
        var skillTwo = StoreTestFixtures.skillId(2);
        var skillThree = StoreTestFixtures.skillId(3);
        var input = StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillThree, owner, 5, 0, 2),
                StoreTestFixtures.history(skillOne, owner, 9, 1),
                StoreTestFixtures.history(skillTwo, owner, 4));
        var store = StoreTestFixtures.restore(input);
        var differentlyOrderedStore = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillTwo, owner, 4),
                StoreTestFixtures.history(skillThree, owner, 2, 5, 0),
                StoreTestFixtures.history(skillOne, owner, 1, 9)));

        var first = store.snapshot();
        var second = store.snapshot();
        var differentlyOrdered = differentlyOrderedStore.snapshot();
        assertAll(
                () -> assertEquals(List.of(skillOne, skillTwo, skillThree),
                        first.histories().stream().map(SkillHistorySnapshot::skillId).toList()),
                () -> assertEquals(List.of(0, 2, 5),
                        first.histories().get(2).revisions().stream()
                                .map(entry -> entry.revision().value())
                                .toList()),
                () -> assertEquals(
                        snapshotShape(first),
                        snapshotShape(second)),
                () -> assertEquals(
                        snapshotShape(first),
                        snapshotShape(differentlyOrdered)),
                () -> assertEquals(
                        snapshotShape(first),
                        snapshotShape(StoreTestFixtures.restore(first).snapshot())));
    }

    @Test
    void historyOrderingUsesUuidNaturalOrderRatherThanUuidStringOrder() {
        var negativeMostSignificantBits = new SkillId(new UUID(Long.MIN_VALUE, 0));
        var nonNegativeMostSignificantBits = new SkillId(new UUID(0, 0));
        var owner = StoreTestFixtures.ownerId(2);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(nonNegativeMostSignificantBits, owner, 0),
                StoreTestFixtures.history(negativeMostSignificantBits, owner, 0)));

        assertAll(
                () -> assertTrue(negativeMostSignificantBits.value()
                        .compareTo(nonNegativeMostSignificantBits.value()) < 0),
                () -> assertTrue(negativeMostSignificantBits.value().toString()
                        .compareTo(nonNegativeMostSignificantBits.value().toString()) > 0),
                () -> assertEquals(
                        List.of(negativeMostSignificantBits, nonNegativeMostSignificantBits),
                        store.snapshot().histories().stream()
                                .map(SkillHistorySnapshot::skillId)
                                .toList()));
    }

    @Test
    void snapshotCollectionsAreDetachedImmutableAndDocumentsAreSafelyShared() {
        var skillId = StoreTestFixtures.skillId(30);
        var owner = StoreTestFixtures.ownerId(30);
        var document = StoreTestFixtures.document(skillId, 0);
        var revisionEntries = new ArrayList<SkillRevisionSnapshot>();
        revisionEntries.add(new SkillRevisionSnapshot(StoreTestFixtures.revision(0), document));
        var history = new SkillHistorySnapshot(skillId, owner, revisionEntries);
        var histories = new ArrayList<SkillHistorySnapshot>();
        histories.add(history);
        var input = new SkillDefinitionStoreSnapshot(histories);

        revisionEntries.clear();
        histories.clear();
        var store = StoreTestFixtures.restore(input);
        var output = store.snapshot();
        var outputDocument = output.histories().getFirst().revisions().getFirst().document();

        assertAll(
                () -> assertEquals(1, input.histories().size()),
                () -> assertEquals(1, input.histories().getFirst().revisions().size()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> input.histories().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> input.histories().getFirst().revisions().clear()),
                () -> assertSame(document, outputDocument),
                () -> assertSame(document,
                        store.find(new SkillReference(skillId, StoreTestFixtures.revision(0))).orElseThrow()),
                () -> assertNotSame(input.histories(), output.histories()));
    }

    @Test
    void privateRestoreConstructorDefensivelyCopiesTheOuterMap() throws Exception {
        var skillId = StoreTestFixtures.skillId(31);
        var owner = StoreTestFixtures.ownerId(31);
        var document = StoreTestFixtures.document(skillId, 0);
        var revisions = new TreeMap<SkillRevision, com.yo1no.gramarye.magic.definition.document.SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        revisions.put(StoreTestFixtures.revision(0), document);
        var source = new HashMap<SkillId, StoredSkillHistory>();
        source.put(skillId, new StoredSkillHistory(owner, revisions));
        var constructor = SkillDefinitionStore.class.getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);

        var store = constructor.newInstance(source);
        source.clear();

        assertAll(
                () -> assertSame(document, store.find(
                                new SkillReference(skillId, StoreTestFixtures.revision(0)))
                        .orElseThrow()),
                () -> assertSame(owner, store.ownerOf(skillId).orElseThrow()));
    }

    @Test
    void readApisRejectNullAndExposeNoMutableTraversalSurface() {
        var store = new SkillDefinitionStore();
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> store.find(null)),
                () -> assertThrows(NullPointerException.class, () -> store.latestReference(null)),
                () -> assertThrows(NullPointerException.class, () -> store.ownerOf(null)),
                () -> assertThrows(NullPointerException.class, () -> store.committedSkillCount(null)));
    }

    private static List<String> snapshotShape(SkillDefinitionStoreSnapshot snapshot) {
        return snapshot.histories().stream()
                .map(history -> history.skillId().value() + ":" + history.revisions().stream()
                        .map(entry -> Integer.toString(entry.revision().value()))
                        .toList())
                .toList();
    }
}
