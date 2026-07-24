package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkillStoreCarrierBuilderTest {
    @Test
    void fullRebuildMatchesA2CanonicalStoreBytes() {
        var store = store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(7), 0, 4),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(6), 3));

        var carrier = carrier(store);
        var a2 = (StorePersistenceEncodeResult.Success)
                SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(store);

        assertArrayEquals(a2.blob().copyBytes(), bytes(carrier));
        assertEquals(2, carrier.historyCount());
        assertEquals(3, carrier.revisionCount());
    }

    @Test
    void prospectiveNewInsertsAtBeginningMiddleAndEndInCanonicalOrder() {
        var owner = StoreTestFixtures.ownerId(8);
        var baseStore = store(
                StoreTestFixtures.history(StoreTestFixtures.skillId(20), owner, 0),
                StoreTestFixtures.history(StoreTestFixtures.skillId(40), owner, 0));
        var base = carrier(baseStore);
        var baseBytes = bytes(base);

        for (var value : List.of(10L, 30L, 50L)) {
            var skillId = StoreTestFixtures.skillId(value);
            var plan = SubmissionPlanTestFactory.newPlan(skillId, owner);
            var prepared = prepared(SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                    base, plan));
            var prospective = prepared.prospectiveCarrier();
            var expectedStore = StoreTestFixtures.restore(baseStore.snapshot());
            assertInstanceOf(SkillStoreCommitResult.Committed.class,
                    expectedStore.commit(plan, SkillQuota.Unlimited.INSTANCE));
            var expected = carrier(expectedStore);

            assertAll(
                    () -> assertEquals(CarrierUpdateKind.NEW, prepared.kind()),
                    () -> assertEquals(
                            new SkillReference(skillId, StoreTestFixtures.revision(0)),
                            prepared.proposedReference()),
                    () -> assertEquals(3, prospective.historyCount()),
                    () -> assertEquals(
                            List.of(StoreTestFixtures.skillId(20),
                                            StoreTestFixtures.skillId(40), skillId).stream()
                                    .sorted((left, right) -> left.value().compareTo(right.value()))
                                    .toList(),
                            prospective.histories().stream()
                                    .map(EncodedHistoryIndex::skillId).toList()),
                    () -> assertArrayEquals(bytes(expected), bytes(prospective)),
                    () -> assertArrayEquals(baseBytes, bytes(base)));
        }
    }

    @Test
    void prospectiveExistingAppendsAfterSparseRevisionsAndLeavesBaseImmutable() {
        var target = StoreTestFixtures.skillId(20);
        var other = StoreTestFixtures.skillId(10);
        var owner = StoreTestFixtures.ownerId(3);
        var base = carrier(store(
                StoreTestFixtures.history(other, StoreTestFixtures.ownerId(2), 2),
                StoreTestFixtures.history(target, owner, 0, 4)));
        var baseBytes = bytes(base);
        var baseRoutes = routes(base);

        var plan = SubmissionPlanTestFactory.existingPlan(
                target, owner, StoreTestFixtures.revision(4));
        var prepared = prepared(SkillStoreCarrierBuilder.prepareProspectiveUpdate(base, plan));
        var prospective = prepared.prospectiveCarrier();
        var expectedStore = store(
                StoreTestFixtures.history(other, StoreTestFixtures.ownerId(2), 2),
                StoreTestFixtures.history(target, owner, 0, 4));
        assertInstanceOf(SkillStoreCommitResult.Committed.class,
                expectedStore.commit(plan, SkillQuota.Unlimited.INSTANCE));
        var expected = carrier(expectedStore);

        assertAll(
                () -> assertEquals(CarrierUpdateKind.EXISTING, prepared.kind()),
                () -> assertEquals(new SkillReference(target, StoreTestFixtures.revision(5)),
                        prepared.proposedReference()),
                () -> assertEquals(List.of(0, 4, 5), prospective.findHistory(target).orElseThrow()
                        .revisions().stream()
                        .map(index -> index.reference().revision().value()).toList()),
                () -> assertArrayEquals(bytes(expected), bytes(prospective)),
                () -> assertArrayEquals(baseBytes, bytes(base)),
                () -> assertEquals(baseRoutes, routes(base)),
                () -> assertNotSame(base, prospective));
    }

    @Test
    void preparedUpdateUsesBaseIdentityAndContainsOnlyReviewedState() {
        var skillId = StoreTestFixtures.skillId(1);
        var owner = StoreTestFixtures.ownerId(1);
        var store = store(StoreTestFixtures.history(skillId, owner, 0));
        var base = carrier(store);
        var structurallyEquivalentBase = carrier(store);
        var prepared = prepared(SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                base,
                SubmissionPlanTestFactory.existingPlan(
                        skillId, owner, StoreTestFixtures.revision(0))));

        assertAll(
                () -> assertTrue(prepared.isFor(base)),
                () -> assertFalse(prepared.isFor(structurallyEquivalentBase)),
                () -> assertSame(base, prepared.baseCarrier()),
                () -> assertNotSame(base, prepared.prospectiveCarrier()),
                () -> assertTrue(prepared.prospectiveCarrier()
                        .findHistory(prepared.proposedReference().skillId()).orElseThrow()
                        .findRevision(prepared.proposedReference().revision()).isPresent()),
                () -> assertEquals(Set.of(
                                "baseCarrier", "prospectiveCarrier", "proposedReference", "kind"),
                        Arrays.stream(PreparedCarrierUpdate.class.getDeclaredFields())
                                .filter(field -> !field.isSynthetic())
                                .map(field -> field.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(PreparedCarrierUpdate.class.getDeclaredMethods())
                        .noneMatch(method -> method.getName().equals("commit")
                                || method.getName().equals("publish"))));
    }

    @Test
    void prospectiveBuilderClassifiesCarrierBaseMismatchesAsInvariants() {
        var existingId = StoreTestFixtures.skillId(1);
        var absentId = StoreTestFixtures.skillId(2);
        var owner = StoreTestFixtures.ownerId(1);
        var base = carrier(store(StoreTestFixtures.history(existingId, owner, 0, 4)));

        assertAll(
                () -> assertInvariant(
                        CarrierInvariantException.Code.EXPECTED_ABSENT_BASE_PRESENT,
                        () -> SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                                base, SubmissionPlanTestFactory.newPlan(existingId, owner))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.EXPECTED_LATEST_BASE_ABSENT,
                        () -> SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                                base,
                                SubmissionPlanTestFactory.existingPlan(
                                        absentId, owner, StoreTestFixtures.revision(0)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.EXPECTED_LATEST_OWNER_MISMATCH,
                        () -> SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                                base,
                                SubmissionPlanTestFactory.existingPlan(
                                        existingId,
                                        StoreTestFixtures.ownerId(99),
                                        StoreTestFixtures.revision(4)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.EXPECTED_LATEST_BASE_MISMATCH,
                        () -> SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                                base,
                                SubmissionPlanTestFactory.existingPlan(
                                        existingId, owner, StoreTestFixtures.revision(3)))));
    }

    @Test
    void reclaimFilterRetainsSelectedSparseRoutesWithoutDocumentEncoding() {
        var firstId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var thirdId = StoreTestFixtures.skillId(3);
        var owner = StoreTestFixtures.ownerId(8);
        var base = carrier(store(
                StoreTestFixtures.history(firstId, owner, 0, 2, 5),
                StoreTestFixtures.history(secondId, owner, 1, 3),
                StoreTestFixtures.history(thirdId, owner, 7)));
        var baseBytes = bytes(base);
        var postReclaim = StoreTestFixtures.snapshot(
                StoreTestFixtures.history(firstId, owner, 0, 5),
                StoreTestFixtures.history(secondId, owner, 3),
                StoreTestFixtures.history(thirdId, owner, 7));

        var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(base, postReclaim);
        var expected = carrier(StoreTestFixtures.restore(postReclaim));

        assertAll(
                () -> assertArrayEquals(bytes(expected), bytes(filtered)),
                () -> assertEquals(routes(expected), routes(filtered)),
                () -> assertEquals(expected.totalHistoryBlobBytes(),
                        filtered.totalHistoryBlobBytes()),
                () -> assertEquals(expected.totalRevisionBlobBytes(),
                        filtered.totalRevisionBlobBytes()),
                () -> assertTrue(filtered.storeByteCount() < base.storeByteCount()),
                () -> assertArrayEquals(baseBytes, bytes(base)),
                () -> assertEquals(List.of(0, 5), filtered.findHistory(firstId).orElseThrow()
                        .revisions().stream()
                        .map(index -> index.reference().revision().value()).toList()),
                () -> assertEquals(List.of(3), filtered.findHistory(secondId).orElseThrow()
                        .revisions().stream()
                        .map(index -> index.reference().revision().value()).toList()),
                () -> assertArrayEquals(
                        historyBytes(base, thirdId), historyBytes(filtered, thirdId)));
    }

    @Test
    void reclaimFilterAcceptsTheImmediateSnapshotFromACompletedDomainReclaim() {
        var firstId = StoreTestFixtures.skillId(11);
        var secondId = StoreTestFixtures.skillId(12);
        var owner = StoreTestFixtures.ownerId(4);
        var liveStore = store(
                StoreTestFixtures.history(firstId, owner, 0, 1, 3),
                StoreTestFixtures.history(secondId, owner, 2, 4));
        var base = carrier(liveStore);

        assertInstanceOf(
                SkillReclaimResult.Completed.class,
                liveStore.reclaim(SkillRetentionRootSnapshot.fromCompleteRoots(List.of())));
        var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(base, liveStore.snapshot());
        var rebuilt = carrier(liveStore);

        assertAll(
                () -> assertArrayEquals(bytes(rebuilt), bytes(filtered)),
                () -> assertEquals(routes(rebuilt), routes(filtered)),
                () -> assertEquals(List.of(
                                new SkillReference(firstId, StoreTestFixtures.revision(3)),
                                new SkillReference(secondId, StoreTestFixtures.revision(4))),
                        routes(filtered)));
    }

    @Test
    void reclaimNoRemovalIsByteEquivalentAndDoesNotMutateBase() {
        var store = store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 0, 4),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(2), 3));
        var base = carrier(store);
        var before = bytes(base);

        var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(base, store.snapshot());

        assertAll(
                () -> assertArrayEquals(before, bytes(filtered)),
                () -> assertArrayEquals(before, bytes(base)),
                () -> assertEquals(routes(base), routes(filtered)));
    }

    @Test
    void reclaimRejectsHistoryOwnerRouteOrderAndLatestMismatches() {
        var skillId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var owner = StoreTestFixtures.ownerId(1);
        var base = carrier(store(
                StoreTestFixtures.history(skillId, owner, 0, 2, 5),
                StoreTestFixtures.history(secondId, owner, 0)));

        assertAll(
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_HISTORY_SET_MISMATCH,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        StoreTestFixtures.history(skillId, owner, 0, 5)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_HISTORY_SET_MISMATCH,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        StoreTestFixtures.history(secondId, owner, 0),
                                        StoreTestFixtures.history(skillId, owner, 0, 5)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_OWNER_MISMATCH,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        StoreTestFixtures.history(
                                                skillId, StoreTestFixtures.ownerId(99), 0, 5),
                                        StoreTestFixtures.history(secondId, owner, 0)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_EMPTY_HISTORY,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        new SkillHistorySnapshot(skillId, owner, List.of()),
                                        StoreTestFixtures.history(secondId, owner, 0)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_ROUTE_NOT_IN_BASE,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        StoreTestFixtures.history(skillId, owner, 0, 99),
                                        StoreTestFixtures.history(secondId, owner, 0)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_LATEST_MISSING,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        StoreTestFixtures.history(skillId, owner, 0, 2),
                                        StoreTestFixtures.history(secondId, owner, 0)))),
                () -> assertInvariant(
                        CarrierInvariantException.Code.RECLAIM_REVISION_ORDER_INVALID,
                        () -> SkillStoreCarrierBuilder.filterAfterReclaim(
                                base,
                                StoreTestFixtures.snapshot(
                                        new SkillHistorySnapshot(
                                                skillId,
                                                owner,
                                                List.of(
                                                        StoreTestFixtures.revisionSnapshot(skillId, 2),
                                                        StoreTestFixtures.revisionSnapshot(skillId, 0),
                                                        StoreTestFixtures.revisionSnapshot(skillId, 5))),
                                        StoreTestFixtures.history(secondId, owner, 0)))));
    }

    private static PreparedCarrierUpdate prepared(CarrierUpdateResult result) {
        return ((CarrierUpdateResult.Prepared) result).update();
    }

    private static EncodedSkillStoreCarrier carrier(SkillDefinitionStore store) {
        return ((CarrierBuildResult.Success) SkillStoreCarrierBuilder.rebuild(store)).carrier();
    }

    private static SkillDefinitionStore store(SkillHistorySnapshot... histories) {
        return StoreTestFixtures.restore(StoreTestFixtures.snapshot(histories));
    }

    private static byte[] bytes(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }

    private static List<SkillReference> routes(EncodedSkillStoreCarrier carrier) {
        return carrier.histories().stream()
                .flatMap(history -> history.revisions().stream())
                .map(EncodedRevisionIndex::reference)
                .toList();
    }

    private static byte[] historyBytes(
            EncodedSkillStoreCarrier carrier,
            SkillId skillId) {
        var history = carrier.findHistory(skillId).orElseThrow();
        var bytes = new byte[history.byteLength()];
        carrier.historySlice(history).copyInto(bytes, 0);
        return bytes;
    }

    private static void assertInvariant(
            CarrierInvariantException.Code expected,
            Runnable operation) {
        var failure = assertThrows(CarrierInvariantException.class, operation::run);
        assertEquals(expected, failure.code());
    }
}
