package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillDefinitionStorePinTest {
    private static final SkillQuota UNLIMITED = SkillQuota.Unlimited.INSTANCE;

    @Test
    void existingExactRevisionReturnsOpenHandleWithoutChangingStoreTruth() {
        var skillId = StoreTestFixtures.skillId(100_001);
        var owner = StoreTestFixtures.ownerId(200_001);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0, 2)));
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var document = store.find(reference).orElseThrow();
        var before = store.snapshot();

        var handle = store.pin(reference).orElseThrow();

        assertAll(
                () -> assertSame(reference, handle.reference()),
                () -> assertFalse(handle.isClosed()),
                () -> assertSame(document, store.find(reference).orElseThrow()),
                () -> assertEquals(
                        new SkillReference(skillId, StoreTestFixtures.revision(2)),
                        store.latestReference(skillId).orElseThrow()),
                () -> assertSame(owner, store.ownerOf(skillId).orElseThrow()),
                () -> assertEquals(1, store.committedSkillCount(owner)));
        assertPinCounts(store, Map.of(reference, 1));
        assertSnapshotEquivalent(before, store.snapshot());

        handle.close();
        assertTrue(handle.isClosed());
        assertPinCounts(store, Map.of());
        assertSnapshotEquivalent(before, store.snapshot());
    }

    @Test
    void missingReferencesReturnEmptyWithoutCreatingGhostCounts() {
        var skillId = StoreTestFixtures.skillId(100_002);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_002), 0, 2)));
        var missingSkill = new SkillReference(
                StoreTestFixtures.skillId(100_003), StoreTestFixtures.revision(0));
        var missingRevision = new SkillReference(skillId, StoreTestFixtures.revision(1));
        var before = store.snapshot();

        assertAll(
                () -> assertTrue(store.pin(missingSkill).isEmpty()),
                () -> assertTrue(store.pin(missingRevision).isEmpty()),
                () -> assertThrows(NullPointerException.class, () -> store.pin(null)));
        assertNoActivePin(store, missingSkill);
        assertNoActivePin(store, missingRevision);
        assertPinCounts(store, Map.of());
        assertSnapshotEquivalent(before, store.snapshot());

        var emptyStore = new SkillDefinitionStore();
        assertTrue(emptyStore.pin(missingSkill).isEmpty());
        assertPinCounts(emptyStore, Map.of());
    }

    @Test
    void multipleHandlesCountIndependentlyAndRemoveTheEntryAfterTheLastClose() {
        var skillId = StoreTestFixtures.skillId(100_004);
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_004), 0)));

        var first = store.pin(reference).orElseThrow();
        var second = store.pin(reference).orElseThrow();
        assertPinCounts(store, Map.of(reference, 2));

        first.close();
        assertAll(
                () -> assertTrue(first.isClosed()),
                () -> assertFalse(second.isClosed()));
        assertPinCounts(store, Map.of(reference, 1));
        assertDoesNotThrow(second::close);
        assertTrue(second.isClosed());
        assertPinCounts(store, Map.of());
        assertNoActivePin(store, reference);
    }

    @Test
    void differentReferencesHaveIndependentCounts() {
        var skillId = StoreTestFixtures.skillId(100_005);
        var firstReference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var secondReference = new SkillReference(skillId, StoreTestFixtures.revision(2));
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_005), 0, 2)));
        var first = store.pin(firstReference).orElseThrow();
        var second = store.pin(secondReference).orElseThrow();
        assertPinCounts(store, Map.of(firstReference, 1, secondReference, 1));

        first.close();
        assertPinCounts(store, Map.of(secondReference, 1));
        assertNoActivePin(store, firstReference);
        assertDoesNotThrow(second::close);
        assertPinCounts(store, Map.of());
        assertNoActivePin(store, secondReference);
    }

    @Test
    void closeIsIdempotentAndFailedReleaseLeavesHandleOpen() {
        var skillId = StoreTestFixtures.skillId(100_006);
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_006), 0)));
        var real = store.pin(reference).orElseThrow();

        real.close();
        assertAll(
                () -> assertDoesNotThrow(real::close),
                () -> assertTrue(real.isClosed()));

        var orphan = new SkillRevisionPin(store, reference);
        assertAll(
                () -> assertThrows(IllegalStateException.class, orphan::close),
                () -> assertFalse(orphan.isClosed()));
    }

    @Test
    void handleConstructorAndReleaseHelperRejectNullComponents() {
        var store = new SkillDefinitionStore();
        var reference = new SkillReference(
                StoreTestFixtures.skillId(100_012), StoreTestFixtures.revision(0));

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillRevisionPin(null, reference)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillRevisionPin(store, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> store.releasePin(null)));
    }

    @Test
    void checkedIncrementRejectsInvalidAndExhaustedCountsWithoutWrapping() {
        assertAll(
                () -> assertEquals(1, SkillDefinitionStore.checkedIncrementPinCount(0)),
                () -> assertEquals(42, SkillDefinitionStore.checkedIncrementPinCount(41)),
                () -> assertEquals(Integer.MAX_VALUE,
                        SkillDefinitionStore.checkedIncrementPinCount(Integer.MAX_VALUE - 1)),
                () -> assertThrows(IllegalStateException.class,
                        () -> SkillDefinitionStore.checkedIncrementPinCount(Integer.MAX_VALUE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SkillDefinitionStore.checkedIncrementPinCount(-1)));
    }

    @Test
    void handleReleasesOnlyItsOriginatingStore() {
        var skillId = StoreTestFixtures.skillId(100_007);
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var snapshot = StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_007), 0));
        var firstStore = StoreTestFixtures.restore(snapshot);
        var secondStore = StoreTestFixtures.restore(snapshot);
        var firstHandle = firstStore.pin(reference).orElseThrow();
        var secondHandle = secondStore.pin(reference).orElseThrow();

        firstHandle.close();
        assertPinCounts(firstStore, Map.of());
        assertPinCounts(secondStore, Map.of(reference, 1));
        assertNoActivePin(firstStore, reference);
        assertDoesNotThrow(secondHandle::close);
        assertPinCounts(secondStore, Map.of());
        assertNoActivePin(secondStore, reference);
    }

    @Test
    void snapshotAndRestoreExcludePinState() {
        var skillId = StoreTestFixtures.skillId(100_008);
        var reference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var original = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, StoreTestFixtures.ownerId(200_008), 0)));
        var before = original.snapshot();
        var handle = original.pin(reference).orElseThrow();
        var whilePinned = original.snapshot();
        var restored = StoreTestFixtures.restore(whilePinned);

        assertSnapshotEquivalent(before, whilePinned);
        assertSnapshotEquivalent(whilePinned, restored.snapshot());
        assertPinCounts(original, Map.of(reference, 1));
        assertPinCounts(restored, Map.of());
        assertNoActivePin(restored, reference);
        assertDoesNotThrow(handle::close);
        assertPinCounts(original, Map.of());
        assertNoActivePin(original, reference);
        assertSnapshotEquivalent(before, original.snapshot());
    }

    @Test
    void publicEmptyStorePathCanCommitPinAndReleaseAnExactRevision() {
        var skillId = StoreTestFixtures.skillId(100_013);
        var owner = StoreTestFixtures.ownerId(200_013);
        var store = new SkillDefinitionStore();
        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(SubmissionPlanTestFactory.newPlan(skillId, owner), UNLIMITED));

        var handle = store.pin(committed.committed()).orElseThrow();
        assertPinCounts(store, Map.of(committed.committed(), 1));
        handle.close();

        assertAll(
                () -> assertTrue(handle.isClosed()),
                () -> assertSame(
                        committed.committed(),
                        handle.reference()));
        assertPinCounts(store, Map.of());
    }

    @Test
    void newAndExistingCommitsDoNotCreateOrDisturbPins() {
        var existingSkillId = StoreTestFixtures.skillId(100_009);
        var owner = StoreTestFixtures.ownerId(200_009);
        var oldReference = new SkillReference(existingSkillId, StoreTestFixtures.revision(0));
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(existingSkillId, owner, 0)));
        var oldHandle = store.pin(oldReference).orElseThrow();

        var existingCommit = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                existingSkillId, owner, StoreTestFixtures.revision(0)),
                        UNLIMITED));
        assertPinCounts(store, Map.of(oldReference, 1));
        assertNoActivePin(store, existingCommit.committed());
        assertDoesNotThrow(oldHandle::close);
        assertPinCounts(store, Map.of());
        assertNoActivePin(store, oldReference);

        var unrelatedSkillId = StoreTestFixtures.skillId(100_010);
        var anchorHandle = store.pin(existingCommit.committed()).orElseThrow();
        var newCommit = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.newPlan(unrelatedSkillId, owner),
                        new SkillQuota.Limited(2)));

        assertPinCounts(store, Map.of(existingCommit.committed(), 1));
        assertAll(
                () -> assertEquals(2, store.committedSkillCount(owner)),
                () -> assertDoesNotThrow(anchorHandle::close));
        assertPinCounts(store, Map.of());
        assertNoActivePin(store, newCommit.committed());
        assertNoActivePin(store, existingCommit.committed());
    }

    @Test
    void pinHandlesDoNotConsumeRetainedRevisionCapacity() {
        var skillId = StoreTestFixtures.skillId(100_011);
        var owner = StoreTestFixtures.ownerId(200_011);
        var revisionCount = MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL - 1;
        var revisions = StoreTestFixtures.revisionSnapshots(skillId, revisionCount);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                new SkillHistorySnapshot(skillId, owner, revisions)));
        var pinnedReference = new SkillReference(skillId, StoreTestFixtures.revision(0));
        var pin = store.pin(pinnedReference).orElseThrow();
        assertPinCounts(store, Map.of(pinnedReference, 1));

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                skillId, owner, StoreTestFixtures.revision(revisionCount - 1)),
                        UNLIMITED));

        assertAll(
                () -> assertEquals(revisionCount, committed.committed().revision().value()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL,
                        store.snapshot().histories().getFirst().revisions().size()),
                () -> assertDoesNotThrow(pin::close));
        assertPinCounts(store, Map.of());
        assertNoActivePin(store, pinnedReference);
        assertNoActivePin(store, committed.committed());
    }

    private static void assertNoActivePin(
            SkillDefinitionStore store,
            SkillReference reference) {
        var orphan = new SkillRevisionPin(store, reference);
        assertAll(
                () -> assertThrows(IllegalStateException.class, orphan::close),
                () -> assertFalse(orphan.isClosed()));
    }

    private static void assertPinCounts(
            SkillDefinitionStore store,
            Map<SkillReference, Integer> expected) {
        try {
            var field = SkillDefinitionStore.class.getDeclaredField("activePinCounts");
            field.setAccessible(true);
            assertEquals(expected, field.get(store));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect active pin counts", exception);
        }
    }

    private static void assertSnapshotEquivalent(
            SkillDefinitionStoreSnapshot expected,
            SkillDefinitionStoreSnapshot actual) {
        assertEquals(snapshotShape(expected), snapshotShape(actual));
        var expectedDocuments = documents(expected);
        var actualDocuments = documents(actual);
        assertEquals(expectedDocuments.size(), actualDocuments.size());
        for (var index = 0; index < expectedDocuments.size(); index++) {
            assertSame(expectedDocuments.get(index), actualDocuments.get(index));
        }
    }

    private static List<String> snapshotShape(SkillDefinitionStoreSnapshot snapshot) {
        return snapshot.histories().stream()
                .flatMap(history -> history.revisions().stream().map(revision ->
                        history.skillId() + "|" + history.owner() + "|" + revision.revision()))
                .toList();
    }

    private static List<SkillDocument> documents(SkillDefinitionStoreSnapshot snapshot) {
        return snapshot.histories().stream()
                .flatMap(history -> history.revisions().stream())
                .map(SkillRevisionSnapshot::document)
                .toList();
    }
}
