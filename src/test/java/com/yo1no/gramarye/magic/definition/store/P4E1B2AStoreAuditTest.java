package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P4E1B2AStoreAuditTest {
    @Test
    void playerAndJournalDuplicatesUseOneLookupAndAcceptNonlatestRevision() {
        var ownerId = 101L;
        var skillId = StoreTestFixtures.skillId(11);
        var nonlatest = reference(skillId, 0);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        skillId, StoreTestFixtures.ownerId(ownerId), 0, 1)));
        var fixture = new RawFixture();
        fixture.addDisk(ownerId, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(nonlatest), Claim.equipped(3, nonlatest));
        fixture.addJournal(nonlatest);
        var lookups = new ArrayList<SkillId>();
        var observations = new ArrayList<P4E1StoreHistoryObservation.Present>();

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), id -> {
                lookups.add(id);
                var observation = store.observeExactHistoryForRootAudit(id);
                if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                    observations.add(present);
                }
                return observation;
            });

            var valid = assertInstanceOf(P4E1GroupedStoreAudit.RawOutcome.Valid.class, result);
            assertEquals(1, valid.distinctSkillIdCount());
            assertEquals(List.of(skillId), lookups);
            assertEquals(1, observations.size());
            assertThrows(IllegalStateException.class,
                    () -> observations.getFirst().contains(nonlatest));
        } finally {
            fixture.discard();
        }
    }

    @Test
    void lookupOrderIsFirstOccurrenceAndIncludesEveryAbsentDistinctRoute() {
        var a = reference(StoreTestFixtures.skillId(31), 0);
        var b = reference(StoreTestFixtures.skillId(32), 0);
        var c = reference(StoreTestFixtures.skillId(33), 0);
        var fixture = new RawFixture();
        fixture.addDisk(301, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(b), Claim.equipped(2, a), Claim.latest(b));
        fixture.addJournal(c, a);
        var lookups = new ArrayList<SkillId>();

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                lookups.add(skillId);
                return P4E1StoreHistoryObservation.Absent.INSTANCE;
            });

            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(List.of(b.skillId(), a.skillId(), c.skillId()), lookups);
        } finally {
            fixture.discard();
        }
    }

    @Test
    void firstPlayerAbsentHistoryWinsBeforeAStillInvalidJournalTarget() {
        var playerReference = reference(StoreTestFixtures.skillId(34), 0);
        var journalReference = reference(StoreTestFixtures.skillId(35), 0);
        var fixture = new RawFixture();
        fixture.addDisk(341, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(playerReference));
        fixture.addJournal(journalReference);

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(
                    fixture.input(), ignored -> P4E1StoreHistoryObservation.Absent.INSTANCE);
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var reconciliation = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(P4E1GroupedStoreAudit.ReconciliationReason.STORE_REFERENCE_MISSING,
                    reconciliation.reason());
            assertEquals(P4E1GroupedStoreAudit.Disposition.DEFERRED_OFFLINE,
                    reconciliation.disposition());
            assertEquals(0, reconciliation.globalOrdinal());
            assertEquals(1, reconciliation.staleObservedAtLeast());
            assertEquals(playerReference, reconciliation.reference());
        } finally {
            fixture.discard();
        }
    }

    @Test
    void duplicateAbsentRouteIsLookedUpOnceAndLaterDomainClaimIsNotClassified() {
        var stale = reference(StoreTestFixtures.skillId(345), 0);
        var later = reference(StoreTestFixtures.skillId(346), 0);
        var laterOwner = StoreTestFixtures.ownerId(346);
        var laterRevisions = new java.util.TreeMap<
                SkillRevision, com.yo1no.gramarye.magic.definition.document.SkillDocument>(
                java.util.Comparator.comparingInt(SkillRevision::value));
        laterRevisions.put(
                StoreTestFixtures.revision(0),
                StoreTestFixtures.document(later.skillId(), 0));
        var laterHistory = new StoredSkillHistory(laterOwner, laterRevisions);
        var poisonedLater = new P4E1StoreHistoryObservation.Present(
                StoreTestFixtures.skillId(999_346), laterHistory);
        var fixture = new RawFixture();
        fixture.addDisk(345, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(stale), Claim.equipped(0, stale));
        fixture.addDisk(346, P4E1GlobalSourceCapture.SourceKind.DISK_OLD,
                Claim.latest(later));
        var lookups = new ArrayList<SkillId>();

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                lookups.add(skillId);
                return skillId.equals(stale.skillId())
                        ? P4E1StoreHistoryObservation.Absent.INSTANCE
                        : poisonedLater;
            });
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var reconciliation = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(stale, reconciliation.reference());
            assertEquals(List.of(stale.skillId(), later.skillId()), lookups,
                    "duplicate absent claims must share one lookup while all distinct IDs are observed");
            assertThrows(IllegalStateException.class,
                    () -> poisonedLater.ownerMatches(laterOwner),
                    "the later observation must be cleaned without being domain-classified");
        } finally {
            fixture.discard();
        }
    }

    @Test
    void sameOwnerMissingRevisionWinsInRawOrderBeforeLaterOwnerMismatch() {
        var firstOwner = 351L;
        var firstSkill = StoreTestFixtures.skillId(351);
        var firstMissing = reference(firstSkill, 9);
        var secondSkill = StoreTestFixtures.skillId(1);
        var secondReference = reference(secondSkill, 0);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        firstSkill, StoreTestFixtures.ownerId(firstOwner), 0),
                StoreTestFixtures.history(
                        secondSkill, StoreTestFixtures.ownerId(999), 0)));
        var fixture = new RawFixture();
        fixture.addDisk(firstOwner, P4E1GlobalSourceCapture.SourceKind.DISK_OLD,
                Claim.latest(firstMissing));
        fixture.addDisk(352, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(secondReference));

        var retained = new ArrayList<P4E1StoreHistoryObservation.Present>();
        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                var observation = store.observeExactHistoryForRootAudit(skillId);
                if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                    retained.add(present);
                }
                return observation;
            });
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var reconciliation = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(P4E1GroupedStoreAudit.ReconciliationReason.STORE_REFERENCE_MISSING,
                    reconciliation.reason());
            assertEquals(firstMissing, reconciliation.reference());
            assertEquals(0, reconciliation.globalOrdinal(),
                    "raw order, not SkillId/map order, must select the terminal");
            assertThrows(IllegalStateException.class,
                    () -> retained.getFirst().contains(firstMissing),
                    "player terminal must clear opaque history observations");
        } finally {
            fixture.discard();
        }
    }

    @Test
    void secondPassUsesGlobalRawOrderRatherThanDistinctRouteGroups() {
        var ownerId = 355L;
        var routeA = StoreTestFixtures.skillId(355);
        var routeB = StoreTestFixtures.skillId(356);
        var validA = reference(routeA, 0);
        var staleB = reference(routeB, 0);
        var laterStaleA = reference(routeA, 9);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        routeA, StoreTestFixtures.ownerId(ownerId), 0)));
        var fixture = new RawFixture();
        fixture.addDisk(ownerId, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(validA),
                Claim.equipped(1, staleB),
                Claim.equipped(2, laterStaleA));

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(
                    fixture.input(), store::observeExactHistoryForRootAudit);
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var reconciliation = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(staleB, reconciliation.reference());
            assertEquals(1, reconciliation.globalOrdinal(),
                    "global raw order must beat the earlier distinct route's later stale claim");
        } finally {
            fixture.discard();
        }
    }

    @Test
    void successLooksUpEveryDistinctRouteOnceInFirstOccurrenceOrder() {
        var ownerA = 361L;
        var ownerB = 362L;
        var a = reference(StoreTestFixtures.skillId(361), 0);
        var b = reference(StoreTestFixtures.skillId(362), 1);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        a.skillId(), StoreTestFixtures.ownerId(ownerA), 0),
                StoreTestFixtures.history(
                        b.skillId(), StoreTestFixtures.ownerId(ownerB), 0, 1)));
        var fixture = new RawFixture();
        fixture.addDisk(ownerB, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(b), Claim.equipped(1, b));
        fixture.addDisk(ownerA, P4E1GlobalSourceCapture.SourceKind.DISK_OLD,
                Claim.latest(a));
        fixture.addJournal(b, a);
        var lookups = new ArrayList<SkillId>();

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                lookups.add(skillId);
                return store.observeExactHistoryForRootAudit(skillId);
            });
            var valid = assertInstanceOf(P4E1GroupedStoreAudit.RawOutcome.Valid.class, result);
            assertEquals(2, valid.distinctSkillIdCount());
            assertEquals(List.of(b.skillId(), a.skillId()), lookups);
        } finally {
            fixture.discard();
        }
    }

    @Test
    void rawPlayerOrderWinsAndOwnerMismatchPrecedesMissingRevision() {
        var route = StoreTestFixtures.skillId(41);
        var missingRevision = reference(route, 7);
        var laterJournal = reference(StoreTestFixtures.skillId(42), 0);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(route, StoreTestFixtures.ownerId(999), 0)));
        var fixture = new RawFixture();
        fixture.addDisk(401, P4E1GlobalSourceCapture.SourceKind.DISK_OLD,
                Claim.latest(missingRevision));
        fixture.addJournal(laterJournal);

        var retained = new ArrayList<P4E1StoreHistoryObservation.Present>();
        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                var observation = store.observeExactHistoryForRootAudit(skillId);
                if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                    retained.add(present);
                }
                return observation;
            });
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var reconciliation = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.ReconciliationRequired.class,
                    terminal.result());
            assertEquals(P4E1GroupedStoreAudit.ReconciliationReason.STORE_OWNER_MISMATCH,
                    reconciliation.reason());
            assertEquals(P4E1GroupedStoreAudit.Disposition.DEFERRED_OFFLINE,
                    reconciliation.disposition());
            assertEquals(0, reconciliation.globalOrdinal());
            assertEquals(1, reconciliation.staleObservedAtLeast());
            assertEquals(missingRevision, reconciliation.reference());
            assertThrows(IllegalStateException.class,
                    () -> retained.getFirst().contains(missingRevision),
                    "owner-mismatch terminal must clear opaque history observations");
        } finally {
            fixture.discard();
        }
    }

    @Test
    void validPlayerThenMissingJournalMapsToJournalTargetInvalid() {
        var ownerId = 501L;
        var playerRoute = StoreTestFixtures.skillId(51);
        var playerReference = reference(playerRoute, 0);
        var journalReference = reference(StoreTestFixtures.skillId(52), 0);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        playerRoute, StoreTestFixtures.ownerId(ownerId), 0)));
        var fixture = new RawFixture();
        fixture.addDisk(ownerId, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                Claim.latest(playerReference));
        fixture.addJournal(journalReference);

        var retained = new ArrayList<P4E1StoreHistoryObservation.Present>();
        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                var observation = store.observeExactHistoryForRootAudit(skillId);
                if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                    retained.add(present);
                }
                return observation;
            });
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var incomplete = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.Incomplete.class, terminal.result());
            assertEquals(P4E1GroupedStoreAudit.IncompleteReason.JOURNAL_TARGET_INVALID,
                    incomplete.reason());
            assertEquals(Optional.of(journalReference), incomplete.reference());
            assertEquals(1, incomplete.globalOrdinal());
            assertThrows(IllegalStateException.class,
                    () -> retained.getFirst().contains(playerReference),
                    "journal terminal must clear every previously observed history");
        } finally {
            fixture.discard();
        }
    }

    @Test
    void presentJournalHistoryWithoutExactRevisionMapsToJournalTargetInvalid() {
        var journalRoute = StoreTestFixtures.skillId(55);
        var missingRevision = reference(journalRoute, 1);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        journalRoute, StoreTestFixtures.ownerId(550), 0)));
        var fixture = new RawFixture();
        fixture.addJournal(missingRevision);
        var retained = new ArrayList<P4E1StoreHistoryObservation.Present>();

        try {
            var result = P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                var present = assertInstanceOf(
                        P4E1StoreHistoryObservation.Present.class,
                        store.observeExactHistoryForRootAudit(skillId));
                retained.add(present);
                return present;
            });
            var terminal = assertInstanceOf(
                    P4E1GroupedStoreAudit.RawOutcome.Terminal.class, result);
            var incomplete = assertInstanceOf(
                    P4E1GroupedStoreAudit.Result.Incomplete.class, terminal.result());
            assertEquals(P4E1GroupedStoreAudit.IncompleteReason.JOURNAL_TARGET_INVALID,
                    incomplete.reason());
            assertEquals(Optional.of(missingRevision), incomplete.reference());
            assertThrows(IllegalStateException.class,
                    () -> retained.getFirst().contains(missingRevision));
        } finally {
            fixture.discard();
        }
    }

    @Test
    void lookupRuntimeAndErrorStopAtThrowingDistinctIdAndClearPriorHistory() {
        var a = StoreTestFixtures.skillId(61);
        var b = StoreTestFixtures.skillId(62);
        var c = StoreTestFixtures.skillId(63);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(a, StoreTestFixtures.ownerId(601), 0)));

        for (var failure : List.<Throwable>of(
                new ExpectedRuntimeException(),
                new ExpectedError(),
                new ExpectedOutOfMemoryError())) {
            var fixture = new RawFixture();
            fixture.addDisk(601, P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                    Claim.latest(reference(a, 0)),
                    Claim.latest(reference(b, 0)),
                    Claim.latest(reference(c, 0)));
            var lookups = new ArrayList<SkillId>();
            var retained = new ArrayList<P4E1StoreHistoryObservation.Present>();
            try {
                var thrown = assertThrows(failure.getClass(), () ->
                        P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                            lookups.add(skillId);
                            if (skillId.equals(b)) {
                                if (failure instanceof RuntimeException runtime) {
                                    throw runtime;
                                }
                                throw (Error) failure;
                            }
                            var present = assertInstanceOf(
                                    P4E1StoreHistoryObservation.Present.class,
                                    store.observeExactHistoryForRootAudit(skillId));
                            retained.add(present);
                            return present;
                        }));
                assertSame(failure, thrown);
                assertEquals(List.of(a, b), lookups);
                assertThrows(IllegalStateException.class,
                        () -> retained.getFirst().ownerMatches(StoreTestFixtures.ownerId(601)));
            } finally {
                fixture.discard();
            }
        }
    }

    @Test
    void rawMetadataMismatchFailsBeforeAnyStoreLookup() {
        var fixture = new RawFixture();
        var reference = reference(StoreTestFixtures.skillId(71), 0);
        fixture.addPlayerWithRawSourceIndex(701, 2, reference);
        var calls = new int[1];

        try {
            assertThrows(IllegalStateException.class, () ->
                    P4E1GroupedStoreAudit.auditRaw(fixture.input(), skillId -> {
                        calls[0]++;
                        return P4E1StoreHistoryObservation.Absent.INSTANCE;
                    }));
            assertEquals(0, calls[0]);
        } finally {
            fixture.discard();
        }
    }

    @Test
    void sourceDispositionMappingIsExhaustiveAndJournalHasNoDisposition() {
        assertEquals(P4E1GroupedStoreAudit.Disposition.ONLINE,
                P4E1GroupedStoreAudit.dispositionForSource(
                        P4E1GlobalSourceCapture.SourceKind.ONLINE));
        assertEquals(P4E1GroupedStoreAudit.Disposition.DEFERRED_INTEGRATED,
                P4E1GroupedStoreAudit.dispositionForSource(
                        P4E1GlobalSourceCapture.SourceKind.INTEGRATED_RUNTIME_SNAPSHOT));
        assertEquals(P4E1GroupedStoreAudit.Disposition.DEFERRED_OFFLINE,
                P4E1GroupedStoreAudit.dispositionForSource(
                        P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY));
        assertEquals(P4E1GroupedStoreAudit.Disposition.DEFERRED_OFFLINE,
                P4E1GroupedStoreAudit.dispositionForSource(
                        P4E1GlobalSourceCapture.SourceKind.DISK_OLD));
        assertThrows(IllegalStateException.class, () ->
                P4E1GroupedStoreAudit.dispositionForSource(
                        P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, new SkillRevision(revision));
    }

    private record Claim(P4E1RawClaimBuffer.ClaimKind kind, int slot, SkillReference reference) {
        private Claim {
            java.util.Objects.requireNonNull(kind, "kind");
            java.util.Objects.requireNonNull(reference, "reference");
        }

        private static Claim latest(SkillReference reference) {
            return new Claim(P4E1RawClaimBuffer.ClaimKind.PLAYER_LATEST, -1, reference);
        }

        private static Claim equipped(int slot, SkillReference reference) {
            return new Claim(P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED, slot, reference);
        }
    }

    private static final class RawFixture {
        private final P4E1AuditBudget budget = P4E1TestBudgets.create();
        private final P4E1RawClaimBuffer claims = new P4E1RawClaimBuffer();
        private final ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources = new ArrayList<>();

        private void addDisk(
                long playerId,
                P4E1GlobalSourceCapture.SourceKind kind,
                Claim... entries) {
            var sourceIndex = sources.size();
            var reservation = reserve(sourceIndex, entries.length);
            for (var entry : entries) {
                append(reservation, entry);
            }
            reservation.finish();
            sources.add(new P4E1GlobalSourceCapture.SourceEntry(
                    P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                    kind,
                    Optional.of(new UUID(0L, playerId)),
                    reservation.claimStart(),
                    reservation.expectedCount(),
                    P4E1GlobalSourceCapture.SourceWitness.Disk.INSTANCE));
        }

        private void addJournal(SkillReference... references) {
            var sourceIndex = sources.size();
            var reservation = reserve(sourceIndex, references.length);
            for (var reference : references) {
                reservation.appendJournal(reference);
            }
            reservation.finish();
            sources.add(new P4E1GlobalSourceCapture.SourceEntry(
                    P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                    P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL,
                    Optional.empty(),
                    reservation.claimStart(),
                    reservation.expectedCount(),
                    new P4E1GlobalSourceCapture.SourceWitness.Journal(
                            new JournalTargetAuditProof.AuditedExisting(
                                    PendingAttachmentJournal.empty()))));
        }

        private void addPlayerWithRawSourceIndex(
                long playerId, int rawSourceIndex, SkillReference reference) {
            var reservation = reserve(rawSourceIndex, 1);
            reservation.appendLatest(reference);
            reservation.finish();
            sources.add(new P4E1GlobalSourceCapture.SourceEntry(
                    P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT,
                    P4E1GlobalSourceCapture.SourceKind.DISK_PRIMARY,
                    Optional.of(new UUID(0L, playerId)),
                    reservation.claimStart(),
                    reservation.expectedCount(),
                    P4E1GlobalSourceCapture.SourceWitness.Disk.INSTANCE));
        }

        private P4E1GroupedStoreAudit.RawInput input() {
            var online = 0;
            var integrated = 0;
            var primary = 0;
            var old = 0;
            var journal = 0;
            var playerRootClaims = 0;
            var journalRootClaims = 0;
            for (var source : sources) {
                switch (source.kind()) {
                    case ONLINE -> {
                        online++;
                        playerRootClaims += source.claimCount();
                    }
                    case INTEGRATED_RUNTIME_SNAPSHOT -> {
                        integrated++;
                        playerRootClaims += source.claimCount();
                    }
                    case DISK_PRIMARY -> {
                        primary++;
                        playerRootClaims += source.claimCount();
                    }
                    case DISK_OLD -> {
                        old++;
                        playerRootClaims += source.claimCount();
                    }
                    case PENDING_JOURNAL -> {
                        journal++;
                        journalRootClaims += source.claimCount();
                    }
                }
            }
            var summary = new P4E1GlobalSourceCapture.Summary(
                    sources.size() - journal,
                    journal,
                    online,
                    integrated,
                    primary,
                    old,
                    playerRootClaims,
                    journalRootClaims,
                    claims.size());
            return new P4E1GroupedStoreAudit.RawInput(claims, sources, summary);
        }

        private P4E1RawClaimBuffer.Reservation reserve(int sourceIndex, int count) {
            return assertInstanceOf(
                    P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                    claims.reserve(budget, sourceIndex, count)).reservation();
        }

        private static void append(P4E1RawClaimBuffer.Reservation reservation, Claim claim) {
            switch (claim.kind()) {
                case PLAYER_LATEST -> reservation.appendLatest(claim.reference());
                case PLAYER_EQUIPPED ->
                        reservation.appendEquipped(claim.slot(), claim.reference());
                case JOURNAL_TARGET -> throw new IllegalArgumentException("not a player claim");
            }
        }

        private void discard() {
            claims.discard();
            sources.clear();
        }
    }

    private static final class ExpectedRuntimeException extends RuntimeException {
    }

    private static final class ExpectedError extends Error {
    }

    private static final class ExpectedOutOfMemoryError extends OutOfMemoryError {
    }
}
