package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillDefinitionStoreReclaimTest {
    @Test
    void nullAndNonCompleteRootStatesRejectBeforeAnyStoreOrPinMutation() throws Exception {
        var skillId = StoreTestFixtures.skillId(1);
        var store = store(StoreTestFixtures.history(
                skillId, StoreTestFixtures.ownerId(1), 0, 1));
        var pin = store.pin(reference(skillId, 0)).orElseThrow();
        var historiesBefore = historyState(store);
        var pinsBefore = pinState(store);

        assertThrows(NullPointerException.class, () -> store.reclaim(null));
        assertRejected(
                store.reclaim(SkillRetentionRootSnapshot.Incomplete.INSTANCE),
                SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE);
        assertRejected(
                store.reclaim(SkillRetentionRootSnapshot.Truncated.INSTANCE),
                SkillReclaimFailure.TruncatedRootSnapshot.INSTANCE);
        assertRejected(
                store.reclaim(new SkillRetentionRootSnapshot.OverLimit(65_537, 65_536)),
                new SkillReclaimFailure.RootCapacityExceeded(65_537, 65_536));

        assertEquals(historiesBefore, historyState(store));
        assertEquals(pinsBefore, pinState(store));
        pin.close();
    }

    @Test
    void firstMissingExternalRootInRawOrderFailsClosedBeforeDeduplication() throws Exception {
        var skillId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var thirdId = StoreTestFixtures.skillId(3);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(
                StoreTestFixtures.history(skillId, owner, 0, 1),
                StoreTestFixtures.history(secondId, owner, 0, 4),
                StoreTestFixtures.history(thirdId, owner, 7));
        var pin = store.pin(reference(secondId, 0)).orElseThrow();
        var missingSkill = reference(StoreTestFixtures.skillId(99), 0);
        var firstMissingRevision = reference(skillId, 7);
        var equalMissingRevision = reference(skillId, 7);
        var historiesBefore = historyState(store);
        var pinsBefore = pinState(store);

        var result = store.reclaim(complete(
                firstMissingRevision, equalMissingRevision, missingSkill));
        var failure = assertInstanceOf(
                SkillReclaimFailure.MissingExternalRoot.class,
                assertInstanceOf(SkillReclaimResult.Rejected.class, result).failure());

        assertSame(firstMissingRevision, failure.reference());
        assertEquals(historiesBefore, historyState(store));
        assertEquals(pinsBefore, pinState(store));
        assertEquals(
                missingSkill,
                assertInstanceOf(
                                SkillReclaimFailure.MissingExternalRoot.class,
                                assertInstanceOf(
                                                SkillReclaimResult.Rejected.class,
                                                store.reclaim(complete(missingSkill)))
                                        .failure())
                        .reference());
        assertEquals(historiesBefore, historyState(store));
        assertEquals(pinsBefore, pinState(store));
        assertTrue(store.find(missingSkill).isEmpty());
        pin.close();
    }

    @Test
    void implicitLatestIsTheOnlyUnconditionalRootAndQuotaIdentityDoesNotChange() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(StoreTestFixtures.history(skillId, owner, 0, 1, 2));

        var completed = completed(store.reclaim(complete()));

        assertEquals(new SkillReclaimReport(1, 3, 1, 2), completed.report());
        assertTrue(store.find(reference(skillId, 0)).isEmpty());
        assertTrue(store.find(reference(skillId, 1)).isEmpty());
        assertTrue(store.find(reference(skillId, 2)).isPresent());
        assertEquals(reference(skillId, 2), store.latestReference(skillId).orElseThrow());
        assertEquals(owner, store.ownerOf(skillId).orElseThrow());
        assertEquals(1, store.committedSkillCount(owner));
    }

    @Test
    void externalRootsProtectOnlyExactReferencesAndDuplicatesDoNotChangeSemantics() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var root = reference(skillId, 1);
        var latest = reference(skillId, 2);
        var first = store(StoreTestFixtures.history(skillId, owner, 0, 1, 2));
        var duplicate = store(StoreTestFixtures.history(skillId, owner, 0, 1, 2));

        var firstResult = completed(first.reclaim(complete(root)));
        var duplicateResult = completed(duplicate.reclaim(complete(root, root, latest, root)));

        assertEquals(new SkillReclaimReport(1, 3, 1, 1), firstResult.report());
        assertEquals(firstResult.report(), duplicateResult.report());
        assertEquals(snapshotShape(first.snapshot()), snapshotShape(duplicate.snapshot()));
        assertTrue(first.find(reference(skillId, 0)).isEmpty());
        assertTrue(first.find(root).isPresent());
        assertTrue(first.find(latest).isPresent());
    }

    @Test
    void activePinsAndExternalRootsFormAnExactUnionWithoutChangingPinCounts() throws Exception {
        var skillId = StoreTestFixtures.skillId(1);
        var store = store(StoreTestFixtures.history(
                skillId, StoreTestFixtures.ownerId(1), 0, 1, 2));
        var external = reference(skillId, 0);
        var pinned = reference(skillId, 1);
        var pin = store.pin(pinned).orElseThrow();
        var pinsBefore = pinState(store);

        var completed = completed(store.reclaim(complete(external)));

        assertEquals(new SkillReclaimReport(1, 3, 0, 0), completed.report());
        assertEquals(pinsBefore, pinState(store));
        assertTrue(store.find(external).isPresent());
        assertTrue(store.find(pinned).isPresent());
        assertTrue(store.find(reference(skillId, 2)).isPresent());
        pin.close();
    }

    @Test
    void multiplePinsProtectUntilTheLastHandleCloses() throws Exception {
        var skillId = StoreTestFixtures.skillId(1);
        var old = reference(skillId, 1);
        var store = store(StoreTestFixtures.history(
                skillId, StoreTestFixtures.ownerId(1), 0, 1, 2));
        var first = store.pin(old).orElseThrow();
        var second = store.pin(old).orElseThrow();

        completed(store.reclaim(complete()));
        assertTrue(store.find(old).isPresent());
        assertEquals(2, pinState(store).get(old));

        first.close();
        assertEquals(new SkillReclaimReport(1, 2, 0, 0),
                completed(store.reclaim(complete())).report());
        assertTrue(store.find(old).isPresent());
        assertEquals(1, pinState(store).get(old));

        second.close();
        assertEquals(new SkillReclaimReport(1, 2, 1, 1),
                completed(store.reclaim(complete())).report());
        assertTrue(store.find(old).isEmpty());
        assertEquals(Map.of(), pinState(store));
    }

    @Test
    void multiHistoryPlanningReplacesOnlyChangedHistoriesAndReportsExactCounts()
            throws Exception {
        var firstId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var thirdId = StoreTestFixtures.skillId(3);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(
                StoreTestFixtures.history(firstId, owner, 0, 1, 2),
                StoreTestFixtures.history(secondId, owner, 0, 4),
                StoreTestFixtures.history(thirdId, owner, 7));
        var oldSnapshot = store.snapshot();
        var historiesBefore = historyState(store);

        var completed = completed(store.reclaim(complete(reference(firstId, 1))));
        var historiesAfter = historyState(store);

        assertEquals(new SkillReclaimReport(3, 6, 2, 2), completed.report());
        assertNotSame(historiesBefore.get(firstId), historiesAfter.get(firstId));
        assertNotSame(historiesBefore.get(secondId), historiesAfter.get(secondId));
        assertSame(historiesBefore.get(thirdId), historiesAfter.get(thirdId));
        assertEquals(
                List.of("1:0", "1:1", "1:2", "2:0", "2:4", "3:7"),
                snapshotShape(oldSnapshot));
        assertEquals(List.of("1:1", "1:2", "2:4", "3:7"),
                snapshotShape(store.snapshot()));
    }

    @Test
    void emptyStoreAndUnchangedHistoryCompleteWithZeroReclaim() throws Exception {
        var empty = new SkillDefinitionStore();
        assertEquals(
                new SkillReclaimReport(0, 0, 0, 0),
                completed(empty.reclaim(complete())).report());

        var skillId = StoreTestFixtures.skillId(1);
        var single = store(StoreTestFixtures.history(
                skillId, StoreTestFixtures.ownerId(1), 9));
        var before = historyState(single).get(skillId);
        assertEquals(
                new SkillReclaimReport(1, 1, 0, 0),
                completed(single.reclaim(complete())).report());
        assertSame(before, historyState(single).get(skillId));
    }

    @Test
    void snapshotsExcludePinsAndExternalRootsAndRemainDetachedAcrossReclaim() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var original = store(StoreTestFixtures.history(skillId, owner, 0, 1));
        var pin = original.pin(reference(skillId, 0)).orElseThrow();
        var oldSnapshot = original.snapshot();
        var withoutRoots = StoreTestFixtures.restore(oldSnapshot);
        var withRoot = StoreTestFixtures.restore(oldSnapshot);

        completed(withoutRoots.reclaim(complete()));
        completed(withRoot.reclaim(complete(reference(skillId, 0))));

        assertTrue(withoutRoots.find(reference(skillId, 0)).isEmpty());
        assertTrue(withRoot.find(reference(skillId, 0)).isPresent());
        assertEquals(Map.of(), uncheckedPinState(withoutRoots));
        assertEquals(Map.of(), uncheckedPinState(withRoot));
        assertTrue(original.find(reference(skillId, 0)).isPresent());
        assertEquals(List.of("1:0", "1:1"), snapshotShape(oldSnapshot));
        assertEquals(List.of("1:1"), snapshotShape(withoutRoots.snapshot()));

        var rootProtectedSnapshot = withRoot.snapshot();
        var restoredWithoutPersistentRoot = StoreTestFixtures.restore(rootProtectedSnapshot);
        completed(restoredWithoutPersistentRoot.reclaim(complete()));
        assertTrue(restoredWithoutPersistentRoot.find(reference(skillId, 0)).isEmpty());
        pin.close();
    }

    @Test
    void sparseReclaimThenCommitUsesLatestSuccessorWithoutReusingGaps() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(StoreTestFixtures.history(skillId, owner, 0, 1, 2));

        completed(store.reclaim(complete()));
        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                skillId, owner, StoreTestFixtures.revision(2)),
                        SkillQuota.Unlimited.INSTANCE));

        assertEquals(reference(skillId, 3), committed.committed());
        assertTrue(store.find(reference(skillId, 0)).isEmpty());
        assertTrue(store.find(reference(skillId, 1)).isEmpty());
        assertTrue(store.find(reference(skillId, 2)).isPresent());
        assertTrue(store.find(reference(skillId, 3)).isPresent());
    }

    @Test
    void pinningFormerLatestAcrossCommitProtectsItUntilClose() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var oldLatest = reference(skillId, 0);
        var store = store(StoreTestFixtures.history(skillId, owner, 0));
        var pin = store.pin(oldLatest).orElseThrow();

        assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                skillId, owner, StoreTestFixtures.revision(0)),
                        SkillQuota.Unlimited.INSTANCE));
        assertEquals(new SkillReclaimReport(1, 2, 0, 0),
                completed(store.reclaim(complete())).report());
        assertTrue(store.find(oldLatest).isPresent());

        pin.close();
        assertEquals(new SkillReclaimReport(1, 2, 1, 1),
                completed(store.reclaim(complete())).report());
        assertTrue(store.find(oldLatest).isEmpty());
        assertEquals(reference(skillId, 1), store.latestReference(skillId).orElseThrow());
    }

    @Test
    void reclaimReleasesRevisionCapacityWithoutReusingRevisionNumbers() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var revisions = StoreTestFixtures.revisionSnapshots(skillId, 128);
        var store = store(new SkillHistorySnapshot(skillId, owner, revisions));
        var plan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(127));

        assertInstanceOf(
                SkillStoreCommitResult.CapacityRejected.class,
                store.commit(plan, SkillQuota.Unlimited.INSTANCE));
        assertEquals(
                new SkillReclaimReport(1, 128, 1, 127),
                completed(store.reclaim(complete())).report());
        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(plan, SkillQuota.Unlimited.INSTANCE));

        assertEquals(reference(skillId, 128), committed.committed());
        assertTrue(store.find(reference(skillId, 0)).isEmpty());
        assertEquals(reference(skillId, 128), store.latestReference(skillId).orElseThrow());
    }

    @Test
    void reclaimDoesNotReleaseDistinctSkillQuota() {
        var firstId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(StoreTestFixtures.history(firstId, owner, 0, 1));

        completed(store.reclaim(complete()));
        var result = store.commit(
                SubmissionPlanTestFactory.newPlan(secondId, owner),
                new SkillQuota.Limited(1));

        assertInstanceOf(SkillStoreCommitResult.QuotaRejected.class, result);
        assertEquals(1, store.committedSkillCount(owner));
        assertEquals(owner, store.ownerOf(firstId).orElseThrow());
    }

    @Test
    void reclaimReleasesGlobalRevisionCapacityWithoutDeletingHistories() {
        var targetId = StoreTestFixtures.skillId(1);
        var targetOwner = StoreTestFixtures.ownerId(1);
        var histories = StoreTestFixtures.historySnapshots(
                255,
                1_000,
                index -> StoreTestFixtures.ownerId(1_000L + index),
                128);
        histories.add(StoreTestFixtures.history(targetId, targetOwner, 0));
        var fillerId = StoreTestFixtures.skillId(500);
        histories.add(new SkillHistorySnapshot(
                fillerId,
                StoreTestFixtures.ownerId(500),
                StoreTestFixtures.revisionSnapshots(fillerId, 127)));
        var store = StoreTestFixtures.restore(new SkillDefinitionStoreSnapshot(histories));
        var plan = SubmissionPlanTestFactory.existingPlan(
                targetId, targetOwner, StoreTestFixtures.revision(0));

        var rejected = assertInstanceOf(
                SkillStoreCommitResult.CapacityRejected.class,
                store.commit(plan, SkillQuota.Unlimited.INSTANCE));
        assertEquals(SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS, rejected.scope());

        assertEquals(
                new SkillReclaimReport(257, 32_768, 256, 32_511),
                completed(store.reclaim(complete())).report());
        assertEquals(257, store.snapshot().histories().size());
        assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(plan, SkillQuota.Unlimited.INSTANCE));
        assertEquals(reference(targetId, 1), store.latestReference(targetId).orElseThrow());
    }

    private static SkillRetentionRootSnapshot complete(SkillReference... references) {
        return SkillRetentionRootSnapshot.fromCompleteRoots(Arrays.asList(references));
    }

    private static SkillDefinitionStore store(SkillHistorySnapshot... histories) {
        return StoreTestFixtures.restore(StoreTestFixtures.snapshot(histories));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, StoreTestFixtures.revision(revision));
    }

    private static SkillReclaimResult.Completed completed(SkillReclaimResult result) {
        return assertInstanceOf(SkillReclaimResult.Completed.class, result);
    }

    private static void assertRejected(
            SkillReclaimResult result,
            SkillReclaimFailure expected) {
        assertEquals(
                expected,
                assertInstanceOf(SkillReclaimResult.Rejected.class, result).failure());
    }

    private static Map<?, ?> historyState(SkillDefinitionStore store)
            throws ReflectiveOperationException {
        return fieldState(store, "histories");
    }

    private static Map<?, ?> pinState(SkillDefinitionStore store)
            throws ReflectiveOperationException {
        return fieldState(store, "activePinCounts");
    }

    private static Map<?, ?> fieldState(SkillDefinitionStore store, String name)
            throws ReflectiveOperationException {
        Field field = SkillDefinitionStore.class.getDeclaredField(name);
        field.setAccessible(true);
        Map<?, ?> state = (Map<?, ?>) field.get(store);
        return Map.copyOf(state);
    }

    private static Map<?, ?> uncheckedPinState(SkillDefinitionStore store) {
        try {
            return pinState(store);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static List<String> snapshotShape(SkillDefinitionStoreSnapshot snapshot) {
        var shape = new ArrayList<String>();
        for (var history : snapshot.histories()) {
            for (var revision : history.revisions()) {
                shape.add(history.skillId().value().getLeastSignificantBits()
                        + ":" + revision.revision().value());
            }
        }
        return shape;
    }
}
