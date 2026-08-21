package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class P4E1B2BCompleteHandoffTest {
    private static final SkillReference FIRST = new SkillReference(
            StoreTestFixtures.skillId(1), StoreTestFixtures.revision(1));
    private static final SkillReference SECOND = new SkillReference(
            StoreTestFixtures.skillId(2), StoreTestFixtures.revision(2));

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    @Test
    void oneIteratorPreservesExactOrderDuplicatesAndExhaustion() {
        var authority = new FixedAuthority(List.of(FIRST, SECOND, FIRST));
        var handoff = new P4E1CompleteRootHandoff(authority);

        assertEquals(0, authority.referenceReads);
        var iterator = handoff.iterator();
        assertThrows(IllegalStateException.class, handoff::iterator);
        assertSame(FIRST, iterator.next());
        assertSame(SECOND, iterator.next());
        assertSame(FIRST, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals(3, authority.referenceReads);

        handoff.close();
        assertEquals(1, authority.releases());
        handoff.close();
        assertEquals(1, authority.releases());
        assertThrows(IllegalStateException.class, iterator::hasNext);
    }

    @Test
    void exactMaximumTraversesOnDemandWithoutConstructingASecondInputVector() {
        var maximum = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
        var authority = new GeneratedAuthority(maximum);
        var handoff = new P4E1CompleteRootHandoff(authority);
        var iterator = handoff.iterator();

        for (var index = 0; index < maximum; index++) {
            assertSame((index & 1) == 0 ? FIRST : SECOND, iterator.next());
        }
        assertFalse(iterator.hasNext());
        assertEquals(maximum, authority.referenceReads);
        assertEquals(1, authority.iteratorIndependentBackingCount());
        handoff.close();
    }

    @Test
    void existingSnapshotFactoryRequestsOneIteratorAndCopiesOnlyAfterTheHandoffGate() {
        var maximum = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
        var authority = new GeneratedAuthority(maximum);
        var handoff = new P4E1CompleteRootHandoff(authority);

        var snapshot = assertInstanceOf(
                SkillRetentionRootSnapshot.Complete.class,
                SkillRetentionRootSnapshot.fromCompleteRoots(handoff));

        assertEquals(maximum, snapshot.roots().size());
        assertSame(FIRST, snapshot.roots().getFirst());
        assertSame(SECOND, snapshot.roots().getLast());
        assertEquals(maximum, authority.referenceReads);
        assertThrows(IllegalStateException.class, handoff::iterator);
        handoff.close();
    }

    @Test
    void auditedOwnershipTransitionsKeepTheExactSegmentedBackingIdentity() {
        var backing = new P4E1RawClaimBuffer();
        var reservation = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                backing.reserve(P4E1TestBudgets.create(), 0, 3)).reservation();
        reservation.appendLatest(FIRST);
        reservation.appendEquipped(2, SECOND);
        reservation.appendLatest(FIRST);
        reservation.finish();
        var exactIdentity = backing;

        assertEquals(P4E1RawClaimBuffer.Ownership.UNPUBLISHED_RAW, backing.ownership());
        backing.markAudited();
        assertSame(exactIdentity, backing);
        assertEquals(P4E1RawClaimBuffer.Ownership.AUDITED, backing.ownership());
        backing.markIndexed();
        assertSame(exactIdentity, backing);
        assertEquals(P4E1RawClaimBuffer.Ownership.AUDITED_INDEX, backing.ownership());

        var authority = new RawBackingAuthority(backing);
        var handoff = new P4E1CompleteRootHandoff(authority);
        var iterator = handoff.iterator();
        assertSame(FIRST, iterator.next());
        assertSame(SECOND, iterator.next());
        assertSame(FIRST, iterator.next());
        assertFalse(iterator.hasNext());
        handoff.close();
        assertSame(exactIdentity, authority.backingIdentity());
        backing.discard();
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED, backing.ownership());
    }

    @Test
    void actualPermitAndLeaseCellsUseTheIndexedBackingAndBlockCompetingWork() {
        var coordinate = new Coordinate(81);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(
                coordinate, lifecycle, FIRST, SECOND, FIRST);

        assertTrue(lifecycle.isComplete(coordinate.owner, coordinate.server));
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        var handoff = consume(coordinate, lifecycle, published.complete);
        assertTrue(lifecycle.hasActiveLease(coordinate.owner, coordinate.server));
        var activeIdentity = lifecycle.stateIdentity(coordinate.owner, coordinate.server);
        var iterator = handoff.iterator();
        assertSame(FIRST, iterator.next());

        var sourceWork = new AtomicInteger();
        assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(
                        coordinate.owner,
                        coordinate.server,
                        scope -> sourceWork.incrementAndGet()));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.invalidate(coordinate.owner, coordinate.server));
        assertEquals(0, sourceWork.get());
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertSame(activeIdentity,
                lifecycle.stateIdentity(coordinate.owner, coordinate.server));
        assertEquals(P4E1RawClaimBuffer.Ownership.AUDITED_INDEX,
                published.rawBacking.ownership());

        assertSame(SECOND, iterator.next());
        assertSame(FIRST, iterator.next());
        assertFalse(iterator.hasNext());
        handoff.close();
        assertTrue(lifecycle.isComplete(coordinate.owner, coordinate.server));
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertEquals(P4E1RawClaimBuffer.Ownership.AUDITED_INDEX,
                published.rawBacking.ownership());

        assertTrue(lifecycle.execute(coordinate.owner, coordinate.server,
                scope -> scope.finishIncomplete(coordinate.owner, coordinate.server)));
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(2L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
    }

    @Test
    void unusedCompleteIsRevokedBeforeTheNextTerminalAndCannotResurrect() {
        var coordinate = new Coordinate(82);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle, FIRST);
        var terminal = new AtomicReference<SkillRetentionRootAuditResult>();

        assertTrue(lifecycle.execute(coordinate.owner, coordinate.server, scope -> {
            assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED,
                    published.rawBacking.ownership());
            terminal.set(scope.finishIncomplete(
                    coordinate.owner,
                    coordinate.server,
                    new SkillRetentionRootAuditResult.OverLimit(
                            SkillRetentionRootAuditResult.Counter.RAW_ROOT_CLAIMS,
                            SkillRetentionRootAuditResult.Stage.RAW_ROOT_CAPTURE,
                            2L,
                            1L,
                            SkillRetentionRootAuditResult.AuditSummary.generationOnly(
                                    scope.generation()))));
        }));

        assertInstanceOf(SkillRetentionRootAuditResult.OverLimit.class, terminal.get());
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(2L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        coordinate.owner,
                        coordinate.server,
                        published.complete,
                        coordinate,
                        coordinate));
        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        coordinate.owner,
                        coordinate.server,
                        published.complete,
                        coordinate,
                        coordinate));
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
    }

    @Test
    void completeInvalidationUsesItsPrebuiltNextStateAndReturnsAcceptedGeneration()
            throws Exception {
        var coordinate = new Coordinate(822);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle, FIRST);

        var accepted = assertInstanceOf(
                SkillRetentionRootAuditService.InvalidationResult.Accepted.class,
                lifecycle.invalidate(coordinate.owner, coordinate.server));

        assertEquals(2L, accepted.generation());
        assertEquals(2L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        coordinate.owner,
                        coordinate.server,
                        published.complete,
                        coordinate,
                        coordinate));

        var root = projectRoot();
        var source = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService.java"));
        assertTrue(source.contains(
                "IndexState nextInvalidationState = reservation.generation == Long.MAX_VALUE"));
        assertTrue(source.contains("? complete.nextInvalidationState"));
    }

    @Test
    void removalForceRevokesTheActualLeaseAndANewCoordinateStartsAtNoEntry() {
        var coordinate = new Coordinate(83);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle, FIRST, SECOND);
        var handoff = consume(coordinate, lifecycle, published.complete);
        var iterator = handoff.iterator();
        assertSame(FIRST, iterator.next());

        lifecycle.remove(coordinate.owner, coordinate.server);

        assertTrue(lifecycle.isRemoved(coordinate.owner, coordinate.server));
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
        assertThrows(IllegalStateException.class, iterator::hasNext);
        assertThrows(IllegalStateException.class, handoff::close);
        assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(
                        coordinate.owner, coordinate.server, scope -> { }));

        var newServer = new Object();
        var newCoordinate = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, newServer);
        assertTrue(newCoordinate.isNoEntry(coordinate.owner, newServer));
        assertEquals(0L, newCoordinate.generation(coordinate.owner, newServer));
        assertTrue(newCoordinate.execute(
                coordinate.owner,
                newServer,
                scope -> scope.finishIncomplete(coordinate.owner, newServer)));
        assertEquals(1L, newCoordinate.generation(coordinate.owner, newServer));
    }

    @Test
    void maximumCompleteIsValidOnceThenExhaustionRevokesItWithoutSourceWork() {
        var coordinate = new Coordinate(84);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server, Long.MAX_VALUE - 1L);
        var published = publishComplete(coordinate, lifecycle, FIRST);
        var sourceWork = new AtomicInteger();

        assertEquals(Long.MAX_VALUE,
                lifecycle.generation(coordinate.owner, coordinate.server));
        assertTrue(lifecycle.isComplete(coordinate.owner, coordinate.server));
        assertFalse(lifecycle.execute(
                coordinate.owner,
                coordinate.server,
                scope -> sourceWork.incrementAndGet()));
        assertTrue(lifecycle.isExhausted(coordinate.owner, coordinate.server));
        assertEquals(0, sourceWork.get());
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());

        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        coordinate.owner,
                        coordinate.server,
                        published.complete,
                        coordinate,
                        coordinate));
        assertFalse(lifecycle.execute(
                coordinate.owner,
                coordinate.server,
                scope -> sourceWork.incrementAndGet()));
        assertEquals(0, sourceWork.get());
    }

    @Test
    void partialCloseReleasesOnceAndLeakedLeaseRejectsOtherOperations() {
        var authority = new FixedAuthority(List.of(FIRST, SECOND));
        var handoff = new P4E1CompleteRootHandoff(authority);
        var iterator = handoff.iterator();

        assertSame(FIRST, iterator.next());
        handoff.close();
        assertEquals(1, authority.releases());
        assertThrows(IllegalStateException.class, iterator::next);
        assertThrows(IllegalStateException.class, handoff::iterator);
    }

    @Test
    void lostCallChainKeepsLeaseFailClosedUntilExactForcedInvalidation() {
        var authority = new TickBoundAuthority(List.of(FIRST));
        var handoff = new P4E1CompleteRootHandoff(authority);
        var iterator = handoff.iterator();
        authority.advanceTick();

        assertThrows(IllegalStateException.class, handoff::close);
        assertEquals(0, authority.releases());
        assertThrows(IllegalStateException.class, iterator::hasNext);
        assertThrows(IllegalStateException.class, handoff::close);

        handoff.forceInvalidate(authority);
        authority.markForceCleared();
        assertThrows(IllegalStateException.class, handoff::iterator);
        assertThrows(IllegalStateException.class, handoff::close);
        assertTrue(authority.forceCleared());
        assertThrows(IllegalStateException.class,
                () -> handoff.forceInvalidate(new FixedAuthority(List.of())));
    }

    @Test
    void wrongThreadCloseClearsCursorButDoesNotReleaseTheLease() throws Exception {
        var authority = new ThreadBoundAuthority(List.of(FIRST));
        var handoff = new P4E1CompleteRootHandoff(authority);
        var iterator = handoff.iterator();
        var failures = new ArrayList<Throwable>();
        var other = new Thread(() -> {
            try {
                handoff.close();
            } catch (RuntimeException failure) {
                failures.add(failure);
            }
        });

        other.start();
        other.join();

        assertEquals(1, failures.size());
        assertTrue(failures.getFirst() instanceof IllegalStateException);
        assertEquals(0, authority.releases());
        assertThrows(IllegalStateException.class, iterator::next);
        handoff.forceInvalidate(authority);
        assertThrows(IllegalStateException.class, handoff::close);
    }

    private static PublishedComplete publishComplete(
            Coordinate coordinate,
            SkillRetentionRootAuditService.IndexLifecycle lifecycle,
            SkillReference... references) {
        var rawBacking = new P4E1RawClaimBuffer();
        var reservation = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                rawBacking.reserve(P4E1TestBudgets.create(), 0, references.length))
                .reservation();
        for (var reference : references) {
            reservation.appendLatest(reference);
        }
        reservation.finish();
        var prepared = new AtomicReference<SkillRetentionRootAuditService.PreparedComplete>();
        coordinate.bind(lifecycle);

        assertTrue(lifecycle.execute(coordinate.owner, coordinate.server, scope -> {
            rawBacking.markAudited();
            var sources = new SkillRetentionRootAuditService.PublicationSource[] {
                        new SkillRetentionRootAuditService.PublicationSource(
                                P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                                P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL,
                                Optional.empty(),
                                0,
                                0,
                                references.length)
                    };
            var publication = scope.prepareComplete(
                    coordinate.owner,
                    coordinate.server,
                    rawBacking,
                    sources,
                    Thread.currentThread(),
                    coordinate.tick,
                    verifiedSeal(),
                    completeSummary(
                            scope.generation(), references.length, distinctCount(references)));
            rawBacking.markIndexed();
            scope.publish(coordinate.owner, coordinate.server, publication);
            prepared.set(publication);
        }));
        return new PublishedComplete(rawBacking, prepared.get().result());
    }

    private static P4E1CompleteRootHandoff consume(
            Coordinate coordinate,
            SkillRetentionRootAuditService.IndexLifecycle lifecycle,
            SkillRetentionRootAuditResult.Complete complete) {
        assertSame(lifecycle, coordinate.lifecycle);
        return SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                coordinate.owner,
                coordinate.server,
                complete,
                coordinate,
                coordinate);
    }

    private static SkillRetentionRootAuditResult.AuditSummary completeSummary(
            long generation, int rawRootCount, int distinctCount) {
        return new SkillRetentionRootAuditResult.AuditSummary(
                OptionalLong.of(generation),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(rawRootCount),
                OptionalInt.of(rawRootCount),
                OptionalInt.of(distinctCount),
                OptionalInt.of(rawRootCount),
                OptionalInt.of(1));
    }

    private static int distinctCount(SkillReference[] references) {
        var distinct = 0;
        for (var index = 0; index < references.length; index++) {
            var first = true;
            for (var prior = 0; prior < index; prior++) {
                if (references[index].equals(references[prior])) {
                    first = false;
                    break;
                }
            }
            if (first) {
                distinct++;
            }
        }
        return distinct;
    }

    private static P4E1FinalFreshness.FreshnessSeal verifiedSeal() {
        var verified = assertInstanceOf(
                P4E1FinalFreshness.VerificationResult.Verified.class,
                P4E1FinalFreshness.verify(new AlwaysCurrentFreshnessInput()));
        return verified.seal();
    }

    private record PublishedComplete(
            P4E1RawClaimBuffer rawBacking,
            SkillRetentionRootAuditResult.Complete complete) {
    }

    private static final class Coordinate
            implements SkillRetentionRootAuditService.CallChainCurrentness,
                    SkillRetentionRootAuditService.CompleteCoordinate {
        private final Object owner = new Object();
        private final Object server = new Object();
        private final Thread thread = Thread.currentThread();
        private int tick;
        private boolean serverThread = true;
        private SkillRetentionRootAuditService.IndexLifecycle lifecycle;

        private Coordinate(int tick) {
            this.tick = tick;
        }

        @Override
        public boolean sameThread(Object candidateServer) {
            return candidateServer == server
                    && serverThread
                    && Thread.currentThread() == thread;
        }

        @Override
        public int currentTick(Object candidateServer) {
            if (candidateServer != server) {
                throw new IllegalStateException("wrong server");
            }
            return tick;
        }

        @Override
        public SkillRetentionRootAuditService.IndexLifecycle requireLifecycle(
                Object candidateOwner, Object candidateServer) {
            if (candidateOwner != owner || candidateServer != server || lifecycle == null) {
                throw new IllegalStateException("wrong coordinate");
            }
            return lifecycle;
        }

        private void bind(SkillRetentionRootAuditService.IndexLifecycle candidate) {
            if (lifecycle != null && lifecycle != candidate) {
                throw new IllegalStateException("coordinate already bound");
            }
            lifecycle = candidate;
        }
    }

    private static final class AlwaysCurrentFreshnessInput implements P4E1FinalFreshness.Input {
        @Override
        public boolean serviceCurrent() {
            return true;
        }

        @Override
        public boolean serverCurrent() {
            return true;
        }

        @Override
        public boolean callChainCurrent() {
            return true;
        }

        @Override
        public boolean playerListCurrent() {
            return true;
        }

        @Override
        public boolean reservationCurrent() {
            return true;
        }

        @Override
        public boolean storeCurrent() {
            return true;
        }

        @Override
        public P4E1PendingJournalObservation.Currentness journalCurrentness() {
            return P4E1PendingJournalObservation.Currentness.CURRENT;
        }

        @Override
        public boolean inventoryCurrent() {
            return true;
        }

        @Override
        public P4E1PlayerDataDirectorySnapshot.FinalVerificationResult
                directoryCurrentness() {
            return P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.Unchanged.INSTANCE;
        }

        @Override
        public boolean onlineCurrent() {
            return true;
        }

        @Override
        public boolean integratedAndArbitrationCurrent() {
            return true;
        }

        @Override
        public boolean reservationStillCurrent() {
            return true;
        }
    }

    private static class FixedAuthority implements P4E1CompleteRootHandoff.LeaseAuthority {
        private final List<SkillReference> references;
        private boolean current = true;
        private boolean forceCleared;
        private int referenceReads;
        private int releases;

        private FixedAuthority(List<SkillReference> references) {
            this.references = List.copyOf(references);
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return current;
        }

        @Override
        public int size() {
            return references.size();
        }

        @Override
        public SkillReference referenceAt(int index) {
            referenceReads++;
            return references.get(index);
        }

        @Override
        public void release(P4E1CompleteRootHandoff handoff) {
            if (!current) {
                throw new IllegalStateException("lease lost");
            }
            releases++;
            current = false;
        }

        final int releases() {
            return releases;
        }

        final void markForceCleared() {
            forceCleared = true;
        }

        final boolean forceCleared() {
            return forceCleared;
        }
    }

    private static final class ThreadBoundAuthority extends FixedAuthority {
        private final Thread thread = Thread.currentThread();

        private ThreadBoundAuthority(List<SkillReference> references) {
            super(references);
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return Thread.currentThread() == thread && super.isCurrent(handoff);
        }
    }

    private static final class TickBoundAuthority extends FixedAuthority {
        private final int capturedTick = 71;
        private int currentTick = capturedTick;

        private TickBoundAuthority(List<SkillReference> references) {
            super(references);
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return currentTick == capturedTick && super.isCurrent(handoff);
        }

        private void advanceTick() {
            currentTick++;
        }
    }

    private static final class GeneratedAuthority
            implements P4E1CompleteRootHandoff.LeaseAuthority {
        private final int size;
        private boolean current = true;
        private int referenceReads;

        private GeneratedAuthority(int size) {
            this.size = size;
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return current;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public SkillReference referenceAt(int index) {
            referenceReads++;
            return (index & 1) == 0 ? FIRST : SECOND;
        }

        @Override
        public void release(P4E1CompleteRootHandoff handoff) {
            current = false;
        }

        private int iteratorIndependentBackingCount() {
            return 1;
        }
    }

    private static final class RawBackingAuthority
            implements P4E1CompleteRootHandoff.LeaseAuthority {
        private final P4E1RawClaimBuffer backing;
        private boolean current = true;

        private RawBackingAuthority(P4E1RawClaimBuffer backing) {
            this.backing = backing;
        }

        @Override
        public boolean isCurrent(P4E1CompleteRootHandoff handoff) {
            return current;
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public SkillReference referenceAt(int index) {
            return backing.referenceAt(index);
        }

        @Override
        public void release(P4E1CompleteRootHandoff handoff) {
            current = false;
        }

        private P4E1RawClaimBuffer backingIdentity() {
            return backing;
        }
    }
}
