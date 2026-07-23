package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class SkillDefinitionStoreCommitTest {
    private static final SkillQuota UNLIMITED = SkillQuota.Unlimited.INSTANCE;

    @Test
    void newCommitPublishesOneOwnerHistoryAndLeavesTheOldEmptySnapshotDetached() {
        var skillId = StoreTestFixtures.skillId(10_001);
        var owner = StoreTestFixtures.ownerId(20_001);
        var plan = SubmissionPlanTestFactory.newPlan(skillId, owner);
        var store = new SkillDefinitionStore();
        var before = store.snapshot();

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(plan, UNLIMITED));
        var document = plan.proposedDocument();

        assertAll(
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(0)),
                        committed.committed()),
                () -> assertSame(document, store.find(committed.committed()).orElseThrow()),
                () -> assertEquals(committed.committed(),
                        store.latestReference(skillId).orElseThrow()),
                () -> assertEquals(owner, store.ownerOf(skillId).orElseThrow()),
                () -> assertEquals(1, store.committedSkillCount(owner)),
                () -> assertTrue(before.histories().isEmpty()),
                () -> assertEquals(1, store.snapshot().histories().size()));
    }

    @Test
    void repeatedNewAndExistingPlansReturnConflictsWithoutOverwritingDocuments() {
        var newSkillId = StoreTestFixtures.skillId(10_010);
        var newOwner = StoreTestFixtures.ownerId(20_010);
        var newPlan = SubmissionPlanTestFactory.newPlan(newSkillId, newOwner);
        var store = new SkillDefinitionStore();
        var firstNew = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(newPlan, UNLIMITED));
        var afterFirstNew = store.snapshot();

        var repeatedNew = assertConflict(
                store.commit(newPlan, new SkillQuota.Limited(0)),
                SkillStoreCommitConflict.ExpectedAbsentButPresent.class);
        assertEquals(newSkillId, repeatedNew.skillId());
        assertUnchanged(store, afterFirstNew);
        assertSame(newPlan.proposedDocument(), store.find(firstNew.committed()).orElseThrow());

        var existingPlan = SubmissionPlanTestFactory.existingPlan(
                newSkillId, newOwner, StoreTestFixtures.revision(0));
        var firstExisting = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(existingPlan, new SkillQuota.Limited(0)));
        var afterFirstExisting = store.snapshot();
        var repeatedExisting = assertConflict(
                store.commit(existingPlan, UNLIMITED),
                SkillStoreCommitConflict.LatestMismatch.class);

        assertAll(
                () -> assertEquals(new SkillReference(newSkillId, StoreTestFixtures.revision(0)),
                        repeatedExisting.expected()),
                () -> assertEquals(firstExisting.committed(), repeatedExisting.observed()),
                () -> assertSame(newPlan.proposedDocument(),
                        store.find(firstNew.committed()).orElseThrow()),
                () -> assertSame(existingPlan.proposedDocument(),
                        store.find(firstExisting.committed()).orElseThrow()),
                () -> assertEquals(1, store.committedSkillCount(newOwner)));
        assertUnchanged(store, afterFirstExisting);
    }

    @Test
    void existingCommitAppendsToSparseHistoryWithoutChangingThePreCommitSnapshot() {
        var skillId = StoreTestFixtures.skillId(10_015);
        var owner = StoreTestFixtures.ownerId(20_015);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0, 3)));
        var before = store.snapshot();
        var beforeShape = snapshotShape(before);
        var beforeDocuments = documents(before);
        var plan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(3));

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(plan, UNLIMITED));
        var after = store.snapshot();

        assertAll(
                () -> assertEquals(
                        new SkillReference(skillId, StoreTestFixtures.revision(4)),
                        committed.committed()),
                () -> assertEquals(List.of(
                                StoreTestFixtures.revision(0),
                                StoreTestFixtures.revision(3)),
                        before.histories().getFirst().revisions().stream()
                                .map(SkillRevisionSnapshot::revision)
                                .toList()),
                () -> assertEquals(beforeShape, snapshotShape(before)),
                () -> assertEquals(List.of(
                                StoreTestFixtures.revision(0),
                                StoreTestFixtures.revision(3),
                                StoreTestFixtures.revision(4)),
                        after.histories().getFirst().revisions().stream()
                                .map(SkillRevisionSnapshot::revision)
                                .toList()),
                () -> assertSame(plan.proposedDocument(),
                        store.find(committed.committed()).orElseThrow()));
        for (var index = 0; index < beforeDocuments.size(); index++) {
            assertSame(beforeDocuments.get(index), documents(before).get(index));
            assertSame(beforeDocuments.get(index), documents(after).get(index));
        }
    }

    @Test
    void maxMinusOneCanCommitMaxAndAConcurrentMaxProducesLatestMismatch() {
        var skillId = StoreTestFixtures.skillId(10_016);
        var owner = StoreTestFixtures.ownerId(20_016);
        var maxMinusOne = Integer.MAX_VALUE - 1;
        var plan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(maxMinusOne));
        var successStore = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, maxMinusOne)));

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                successStore.commit(plan, UNLIMITED));
        assertEquals(Integer.MAX_VALUE, committed.committed().revision().value());
        assertSame(plan.proposedDocument(), successStore.find(committed.committed()).orElseThrow());

        var racedStore = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, Integer.MAX_VALUE)));
        var racedBefore = racedStore.snapshot();
        var conflict = assertConflict(
                racedStore.commit(plan, UNLIMITED),
                SkillStoreCommitConflict.LatestMismatch.class);
        assertAll(
                () -> assertEquals(
                        new SkillReference(skillId, StoreTestFixtures.revision(maxMinusOne)),
                        conflict.expected()),
                () -> assertEquals(
                        new SkillReference(skillId, StoreTestFixtures.revision(Integer.MAX_VALUE)),
                        conflict.observed()));
        assertUnchanged(racedStore, racedBefore);
    }

    @Test
    void existingCommitUsesOwnerBeforeLatestAndClassifiesAbsentStaleAndFutureState() {
        var skillId = StoreTestFixtures.skillId(10_020);
        var owner = StoreTestFixtures.ownerId(20_020);
        var otherOwner = StoreTestFixtures.ownerId(20_021);
        var absentStore = new SkillDefinitionStore();
        var absentPlan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(0));
        var absentBefore = absentStore.snapshot();
        var absent = assertConflict(
                absentStore.commit(absentPlan, UNLIMITED),
                SkillStoreCommitConflict.ExpectedLatestButAbsent.class);
        assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(0)), absent.expected());
        assertAll(
                () -> assertEquals(0, absentStore.committedSkillCount(owner)),
                () -> assertUnchanged(absentStore, absentBefore, skillId));

        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0, 2)));
        var before = store.snapshot();
        var stalePlan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(0));
        var stale = assertConflict(
                store.commit(stalePlan, UNLIMITED),
                SkillStoreCommitConflict.LatestMismatch.class);
        assertAll(
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(0)),
                        stale.expected()),
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(2)),
                        stale.observed()));
        assertUnchanged(store, before);

        var futurePlan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, StoreTestFixtures.revision(5));
        var future = assertConflict(
                store.commit(futurePlan, UNLIMITED),
                SkillStoreCommitConflict.LatestMismatch.class);
        assertAll(
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(5)),
                        future.expected()),
                () -> assertEquals(new SkillReference(skillId, StoreTestFixtures.revision(2)),
                        future.observed()));
        assertUnchanged(store, before);

        var wrongOwnerPlan = SubmissionPlanTestFactory.existingPlan(
                skillId, otherOwner, StoreTestFixtures.revision(0));
        var ownerRejected = assertInstanceOf(
                SkillStoreCommitResult.OwnerRejected.class,
                store.commit(wrongOwnerPlan, UNLIMITED));
        var wrongOwnerFuturePlan = SubmissionPlanTestFactory.existingPlan(
                skillId, otherOwner, StoreTestFixtures.revision(5));
        var futureOwnerRejected = assertInstanceOf(
                SkillStoreCommitResult.OwnerRejected.class,
                store.commit(wrongOwnerFuturePlan, UNLIMITED));
        assertAll(
                () -> assertEquals(skillId, ownerRejected.skillId()),
                () -> assertEquals(skillId, futureOwnerRejected.skillId()));
        assertUnchanged(store, before);
    }

    @Test
    void quotaAppliesOnlyAfterAbsentCasAndNeverBlocksExistingRevisions() {
        var skillId = StoreTestFixtures.skillId(10_030);
        var owner = StoreTestFixtures.ownerId(20_030);
        var newPlan = SubmissionPlanTestFactory.newPlan(skillId, owner);
        var emptyStore = new SkillDefinitionStore();
        var emptyBefore = emptyStore.snapshot();
        var quotaRejected = assertInstanceOf(
                SkillStoreCommitResult.QuotaRejected.class,
                emptyStore.commit(newPlan, new SkillQuota.Limited(0)));
        assertAll(
                () -> assertEquals(skillId, quotaRejected.skillId()),
                () -> assertEquals(0, quotaRejected.current()),
                () -> assertEquals(0, quotaRejected.maximum()),
                () -> assertEquals(0, emptyStore.committedSkillCount(owner)));
        assertUnchanged(emptyStore, emptyBefore, skillId);

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                emptyStore.commit(newPlan, new SkillQuota.Limited(1)));
        var existingPlan = SubmissionPlanTestFactory.existingPlan(
                skillId, owner, committed.committed().revision());
        assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                emptyStore.commit(existingPlan, new SkillQuota.Limited(0)));
        assertEquals(1, emptyStore.committedSkillCount(owner));
    }

    @Test
    void expectedAbsentCapacityAndQuotaPrecedenceIsFixed() {
        var fullOwner = StoreTestFixtures.ownerId(30_001);
        var ownerHistories = StoreTestFixtures.historySnapshots(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                30_000,
                ignored -> fullOwner,
                1);
        var ownerStore = restore(ownerHistories);
        var ownerBefore = ownerStore.snapshot();
        var ownerResult = assertCapacity(
                ownerStore.commit(
                        SubmissionPlanTestFactory.newPlan(
                                StoreTestFixtures.skillId(39_999), fullOwner),
                        new SkillQuota.Limited(0)),
                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);
        assertEquals(ownerResult.maximum(), ownerResult.current());
        assertUnchanged(ownerStore, ownerBefore);

        var globalHistories = StoreTestFixtures.historySnapshots(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                40_000,
                index -> StoreTestFixtures.ownerId(40_000L + index),
                1);
        var globalStore = restore(globalHistories);
        var globalBefore = globalStore.snapshot();
        var globalResult = assertCapacity(
                globalStore.commit(
                        SubmissionPlanTestFactory.newPlan(
                                StoreTestFixtures.skillId(49_999),
                                StoreTestFixtures.ownerId(49_999)),
                        new SkillQuota.Limited(0)),
                SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL);
        assertEquals(globalResult.maximum(), globalResult.current());
        assertUnchanged(globalStore, globalBefore);

        var allHardCapHistories = StoreTestFixtures.historySnapshots(
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                45_000,
                index -> index < MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER
                        ? fullOwner
                        : StoreTestFixtures.ownerId(145_000L + index),
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL
                        / MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL);
        var allHardCapStore = restore(allHardCapHistories);
        var allHardCapBefore = allHardCapStore.snapshot();
        var presentSkillId = allHardCapHistories.getFirst().skillId();
        var present = assertConflict(
                allHardCapStore.commit(
                        SubmissionPlanTestFactory.newPlan(presentSkillId, fullOwner),
                        new SkillQuota.Limited(0)),
                SkillStoreCommitConflict.ExpectedAbsentButPresent.class);
        assertEquals(presentSkillId, present.skillId());
        assertUnchanged(allHardCapStore, allHardCapBefore);

        var absentSkillId = StoreTestFixtures.skillId(149_999);
        var simultaneousCapacity = assertCapacity(
                allHardCapStore.commit(
                        SubmissionPlanTestFactory.newPlan(absentSkillId, fullOwner),
                        new SkillQuota.Limited(0)),
                SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);
        assertEquals(simultaneousCapacity.maximum(), simultaneousCapacity.current());
        assertUnchanged(allHardCapStore, allHardCapBefore, absentSkillId);
    }

    @Test
    void quotaAndPerSkillCapacityPrecedeGlobalRevisionCapacity() {
        var owner = StoreTestFixtures.ownerId(50_001);
        var fullRevisionHistories = StoreTestFixtures.historySnapshots(
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL
                        / MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL,
                50_000,
                ignored -> owner,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL);
        var store = restore(fullRevisionHistories);
        var before = store.snapshot();

        var existingSkillId = fullRevisionHistories.getFirst().skillId();
        var perSkillResult = assertCapacity(
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                existingSkillId,
                                owner,
                                StoreTestFixtures.revision(
                                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL - 1)),
                        UNLIMITED),
                SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL);
        assertEquals(perSkillResult.maximum(), perSkillResult.current());
        assertUnchanged(store, before);

        var newSkillId = StoreTestFixtures.skillId(59_999);
        var newOwner = StoreTestFixtures.ownerId(59_999);
        var quotaResult = assertInstanceOf(
                SkillStoreCommitResult.QuotaRejected.class,
                store.commit(
                        SubmissionPlanTestFactory.newPlan(newSkillId, newOwner),
                        new SkillQuota.Limited(0)));
        assertAll(
                () -> assertEquals(0, quotaResult.current()),
                () -> assertEquals(0, quotaResult.maximum()));
        assertUnchanged(store, before);

        var globalResult = assertCapacity(
                store.commit(
                        SubmissionPlanTestFactory.newPlan(newSkillId, newOwner),
                        UNLIMITED),
                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL);
        assertEquals(globalResult.maximum(), globalResult.current());
        assertUnchanged(store, before);
    }

    @Test
    void existingCommitCanBeRejectedByGlobalRevisionCapacityWithoutPerSkillCapacity() {
        var targetSkillId = StoreTestFixtures.skillId(60_000);
        var targetOwner = StoreTestFixtures.ownerId(60_000);
        var histories = new ArrayList<SkillHistorySnapshot>();
        histories.add(StoreTestFixtures.history(targetSkillId, targetOwner, 0));
        histories.addAll(StoreTestFixtures.historySnapshots(
                255,
                60_001,
                ignored -> StoreTestFixtures.ownerId(60_001),
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL));
        var fillerSkillId = StoreTestFixtures.skillId(60_256);
        histories.add(new SkillHistorySnapshot(
                fillerSkillId,
                StoreTestFixtures.ownerId(60_001),
                StoreTestFixtures.revisionSnapshots(
                        fillerSkillId,
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL - 1)));
        var store = restore(histories);
        var before = store.snapshot();

        var result = assertCapacity(
                store.commit(
                        SubmissionPlanTestFactory.existingPlan(
                                targetSkillId, targetOwner, StoreTestFixtures.revision(0)),
                        new SkillQuota.Limited(0)),
                SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL);

        assertEquals(result.maximum(), result.current());
        assertUnchanged(store, before);
    }

    @Test
    void commitRejectsNullInputsBeforeInspectingOrMutatingStore() {
        var store = new SkillDefinitionStore();
        var plan = SubmissionPlanTestFactory.newPlan(
                StoreTestFixtures.skillId(70_000),
                StoreTestFixtures.ownerId(70_000));
        var before = store.snapshot();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> store.commit(null, UNLIMITED)),
                () -> assertThrows(NullPointerException.class,
                        () -> store.commit(plan, null)));
        assertUnchanged(store, before);
    }

    @Test
    void storedHistoryAppendBuildsAnImmutableReplacementWithoutMutatingTheOriginal() {
        var skillId = StoreTestFixtures.skillId(80_000);
        var owner = StoreTestFixtures.ownerId(80_000);
        var revisionZero = StoreTestFixtures.revision(0);
        var revisionTwo = StoreTestFixtures.revision(2);
        var revisionThree = StoreTestFixtures.revision(3);
        var documentZero = StoreTestFixtures.document(skillId, 0);
        var documentTwo = StoreTestFixtures.document(skillId, 2);
        var documentThree = StoreTestFixtures.document(skillId, 3);
        var source = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        source.put(revisionZero, documentZero);
        source.put(revisionTwo, documentTwo);
        var original = new StoredSkillHistory(owner, source);

        var replacement = original.append(documentThree);

        assertAll(
                () -> assertNotSame(original, replacement),
                () -> assertNotSame(original.revisions(), replacement.revisions()),
                () -> assertSame(owner, replacement.owner()),
                () -> assertEquals(List.of(revisionZero, revisionTwo),
                        List.copyOf(original.revisions().keySet())),
                () -> assertEquals(List.of(revisionZero, revisionTwo, revisionThree),
                        List.copyOf(replacement.revisions().keySet())),
                () -> assertSame(documentZero, original.revisions().get(revisionZero)),
                () -> assertSame(documentTwo, original.revisions().get(revisionTwo)),
                () -> assertSame(documentZero, replacement.revisions().get(revisionZero)),
                () -> assertSame(documentTwo, replacement.revisions().get(revisionTwo)),
                () -> assertSame(documentThree, replacement.revisions().get(revisionThree)),
                () -> assertThrows(NullPointerException.class,
                        () -> original.append(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> original.append(documentTwo)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> replacement.revisions().put(
                                StoreTestFixtures.revision(4),
                                StoreTestFixtures.document(skillId, 4))));
    }

    private static SkillDefinitionStore restore(List<SkillHistorySnapshot> histories) {
        return StoreTestFixtures.restore(new SkillDefinitionStoreSnapshot(histories));
    }

    private static <T extends SkillStoreCommitConflict> T assertConflict(
            SkillStoreCommitResult result,
            Class<T> conflictType) {
        var conflict = assertInstanceOf(SkillStoreCommitResult.Conflict.class, result);
        return assertInstanceOf(conflictType, conflict.conflict());
    }

    private static SkillStoreCommitResult.CapacityRejected assertCapacity(
            SkillStoreCommitResult result,
            SkillStoreCapacityScope scope,
            int maximum) {
        var rejected = assertInstanceOf(SkillStoreCommitResult.CapacityRejected.class, result);
        assertAll(
                () -> assertEquals(scope, rejected.scope()),
                () -> assertEquals(maximum, rejected.maximum()));
        return rejected;
    }

    private static void assertUnchanged(
            SkillDefinitionStore store,
            SkillDefinitionStoreSnapshot before,
            SkillId... expectedAbsentSkillIds) {
        var after = store.snapshot();
        assertEquals(snapshotShape(before), snapshotShape(after));
        var beforeDocuments = documents(before);
        var afterDocuments = documents(after);
        assertEquals(beforeDocuments.size(), afterDocuments.size());
        for (var index = 0; index < beforeDocuments.size(); index++) {
            assertSame(beforeDocuments.get(index), afterDocuments.get(index));
        }

        var expectedOwnerCounts = new HashMap<SkillOwnerId, Integer>();
        for (var history : before.histories()) {
            var skillId = history.skillId();
            var revisions = history.revisions();
            var latestRevision = revisions.getLast().revision();
            assertEquals(history.owner(), store.ownerOf(skillId).orElseThrow());
            assertEquals(
                    new SkillReference(skillId, latestRevision),
                    store.latestReference(skillId).orElseThrow());
            for (var revision : revisions) {
                assertSame(
                        revision.document(),
                        store.find(new SkillReference(skillId, revision.revision())).orElseThrow());
            }
            expectedOwnerCounts.merge(history.owner(), 1, Integer::sum);
        }
        expectedOwnerCounts.forEach((owner, expected) ->
                assertEquals(expected, store.committedSkillCount(owner)));
        for (var skillId : expectedAbsentSkillIds) {
            assertTrue(store.ownerOf(skillId).isEmpty());
            assertTrue(store.latestReference(skillId).isEmpty());
            assertTrue(store.find(new SkillReference(
                    skillId, StoreTestFixtures.revision(0))).isEmpty());
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
