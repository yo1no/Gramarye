package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class P4E3LeaseTerminalTest {
    private static final SkillReference ROOT = new SkillReference(
            StoreTestFixtures.skillId(301), StoreTestFixtures.revision(7));

    @Test
    void unmarkedCloseDemotesAtTheSameGenerationAndDiscardsBacking() {
        var coordinate = new Coordinate(31);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle);
        var handoff = consume(coordinate, published.complete);

        assertTrue(lifecycle.hasActiveLease(coordinate.owner, coordinate.server));
        var activeFailure = assertThrows(
                IllegalStateException.class,
                () -> lifecycle.observeP4E3IndexTerminal(
                        coordinate.owner, coordinate.server));
        assertEquals("P4E3_INDEX_TERMINAL_NOT_AVAILABLE", activeFailure.getMessage());
        handoff.close();

        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        var terminal = lifecycle.observeP4E3IndexTerminal(
                coordinate.owner, coordinate.server);
        assertEquals(P4E2QualificationFacade.E3IndexTerminal.INCOMPLETE,
                terminal.terminal());
        assertEquals(1L, terminal.generation());
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
        handoff.close();
        assertThrows(IllegalStateException.class,
                () -> consume(coordinate, published.complete));
    }

    @Test
    void oneSourceUnchangedMarkSelectsCompleteWithoutReissuingThePermit() {
        var coordinate = new Coordinate(32);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle);
        var handoff = consume(coordinate, published.complete);

        handoff.markStoreSourceUnchanged();
        var secondMark = assertThrows(
                IllegalStateException.class, handoff::markStoreSourceUnchanged);
        assertEquals(
                "P4E1_COMPLETE_HANDOFF_SOURCE_UNCHANGED_ALREADY_MARKED",
                secondMark.getMessage());
        handoff.close();

        assertTrue(lifecycle.isComplete(coordinate.owner, coordinate.server));
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        var terminal = lifecycle.observeP4E3IndexTerminal(
                coordinate.owner, coordinate.server);
        assertEquals(P4E2QualificationFacade.E3IndexTerminal.COMPLETE_INDEX,
                terminal.terminal());
        assertEquals(1L, terminal.generation());
        assertEquals(
                P4E1RawClaimBuffer.Ownership.AUDITED_INDEX,
                published.rawBacking.ownership());
        handoff.close();
        assertThrows(IllegalStateException.class,
                () -> consume(coordinate, published.complete));

        assertTrue(lifecycle.execute(
                coordinate.owner,
                coordinate.server,
                scope -> scope.finishIncomplete(coordinate.owner, coordinate.server)));
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(2L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
    }

    @Test
    void failClosedClosePreservesAPendingThrowableIdentity() {
        var coordinate = new Coordinate(33);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle);
        var handoff = consume(coordinate, published.complete);
        var expected = new OutOfMemoryError("p4-e3-primary");

        var escaped = assertThrows(OutOfMemoryError.class, () -> {
            try {
                throw expected;
            } finally {
                handoff.close();
            }
        });

        assertSame(expected, escaped);
        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertEquals(1L, lifecycle.generation(coordinate.owner, coordinate.server));
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
    }

    @Test
    void sameGenerationDemotionAtMaximumIsLegalAndOnlyTheNextAdvanceExhausts() {
        var coordinate = new Coordinate(34);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server, Long.MAX_VALUE - 1L);
        var published = publishComplete(coordinate, lifecycle);
        var handoff = consume(coordinate, published.complete);

        handoff.close();

        assertTrue(lifecycle.isIncomplete(coordinate.owner, coordinate.server));
        assertFalse(lifecycle.isExhausted(coordinate.owner, coordinate.server));
        assertEquals(Long.MAX_VALUE,
                lifecycle.generation(coordinate.owner, coordinate.server));
        var sourceWork = new AtomicInteger();
        assertFalse(lifecycle.execute(
                coordinate.owner,
                coordinate.server,
                scope -> sourceWork.incrementAndGet()));
        assertEquals(0, sourceWork.get());
        assertTrue(lifecycle.isExhausted(coordinate.owner, coordinate.server));
        assertEquals(Long.MAX_VALUE,
                lifecycle.generation(coordinate.owner, coordinate.server));
    }

    @Test
    void serverRemovalWinsEvenAfterMarkAndForceInvalidatesTheHandoff() {
        var coordinate = new Coordinate(35);
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, coordinate.server);
        var published = publishComplete(coordinate, lifecycle);
        var handoff = consume(coordinate, published.complete);
        handoff.markStoreSourceUnchanged();

        lifecycle.remove(coordinate.owner, coordinate.server);

        assertTrue(lifecycle.isRemoved(coordinate.owner, coordinate.server));
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                published.rawBacking.ownership());
        var markFailure = assertThrows(
                IllegalStateException.class, handoff::markStoreSourceUnchanged);
        assertEquals("P4E1_COMPLETE_HANDOFF_NOT_CURRENT", markFailure.getMessage());
        var closeFailure = assertThrows(IllegalStateException.class, handoff::close);
        assertEquals("P4E1_COMPLETE_HANDOFF_FORCE_INVALIDATED", closeFailure.getMessage());
        var terminalFailure = assertThrows(
                IllegalStateException.class,
                () -> lifecycle.observeP4E3IndexTerminal(
                        coordinate.owner, coordinate.server));
        assertEquals("P4E3_INDEX_TERMINAL_NOT_AVAILABLE", terminalFailure.getMessage());

        var replacementServer = new Object();
        var replacement = new SkillRetentionRootAuditService.IndexLifecycle(
                coordinate.owner, replacementServer);
        assertTrue(replacement.isNoEntry(coordinate.owner, replacementServer));
        assertEquals(0L, replacement.generation(coordinate.owner, replacementServer));
    }

    @Test
    void markRejectsLostTickAndWrongThreadWithoutChangingTheDefaultTerminal() throws Exception {
        var tickCoordinate = new Coordinate(36);
        var tickLifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                tickCoordinate.owner, tickCoordinate.server);
        var tickPublished = publishComplete(tickCoordinate, tickLifecycle);
        var tickHandoff = consume(tickCoordinate, tickPublished.complete);
        tickCoordinate.tick++;

        var tickFailure = assertThrows(
                IllegalStateException.class, tickHandoff::markStoreSourceUnchanged);
        assertEquals("P4E1_COMPLETE_HANDOFF_NOT_CURRENT", tickFailure.getMessage());
        tickLifecycle.remove(tickCoordinate.owner, tickCoordinate.server);
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                tickPublished.rawBacking.ownership());

        var threadCoordinate = new Coordinate(37);
        var threadLifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                threadCoordinate.owner, threadCoordinate.server);
        var threadPublished = publishComplete(threadCoordinate, threadLifecycle);
        var threadHandoff = consume(threadCoordinate, threadPublished.complete);
        var failure = new AtomicReference<Throwable>();
        var otherThread = new Thread(() -> {
            try {
                threadHandoff.markStoreSourceUnchanged();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        otherThread.start();
        otherThread.join();

        var wrongThread = assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals("P4E1_COMPLETE_HANDOFF_NOT_CURRENT", wrongThread.getMessage());
        threadHandoff.close();
        assertTrue(threadLifecycle.isIncomplete(
                threadCoordinate.owner, threadCoordinate.server));
        assertEquals(
                P4E1RawClaimBuffer.Ownership.DISCARDED,
                threadPublished.rawBacking.ownership());
    }

    @Test
    void markSeamIsExactPackagePrivateNoArgumentVoid() throws Exception {
        var mark = P4E1CompleteRootHandoff.class.getDeclaredMethod(
                "markStoreSourceUnchanged");

        assertEquals(void.class, mark.getReturnType());
        assertEquals(0, mark.getParameterCount());
        assertFalse(Modifier.isPublic(mark.getModifiers()));
        assertFalse(Modifier.isProtected(mark.getModifiers()));
        assertFalse(Modifier.isPrivate(mark.getModifiers()));
    }

    private static PublishedComplete publishComplete(
            Coordinate coordinate,
            SkillRetentionRootAuditService.IndexLifecycle lifecycle) {
        coordinate.bind(lifecycle);
        var rawBacking = new P4E1RawClaimBuffer();
        var reservation = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                rawBacking.reserve(P4E1TestBudgets.create(), 0, 1)).reservation();
        reservation.appendLatest(ROOT);
        reservation.finish();
        var prepared = new AtomicReference<SkillRetentionRootAuditService.PreparedComplete>();

        assertTrue(lifecycle.execute(coordinate.owner, coordinate.server, scope -> {
            rawBacking.markAudited();
            var sources = new SkillRetentionRootAuditService.PublicationSource[] {
                    new SkillRetentionRootAuditService.PublicationSource(
                            P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                            P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL,
                            Optional.empty(),
                            0,
                            0,
                            1)
            };
            var publication = scope.prepareComplete(
                    coordinate.owner,
                    coordinate.server,
                    rawBacking,
                    sources,
                    Thread.currentThread(),
                    coordinate.tick,
                    verifiedSeal(),
                    completeSummary(scope.generation()));
            rawBacking.markIndexed();
            scope.publish(coordinate.owner, coordinate.server, publication);
            prepared.set(publication);
        }));
        return new PublishedComplete(rawBacking, prepared.get().result());
    }

    private static P4E1CompleteRootHandoff consume(
            Coordinate coordinate, SkillRetentionRootAuditResult.Complete complete) {
        return SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                coordinate.owner,
                coordinate.server,
                complete,
                coordinate,
                coordinate);
    }

    private static SkillRetentionRootAuditResult.AuditSummary completeSummary(long generation) {
        return new SkillRetentionRootAuditResult.AuditSummary(
                OptionalLong.of(generation),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(0),
                OptionalInt.of(1),
                OptionalInt.of(1),
                OptionalInt.of(1),
                OptionalInt.of(1),
                OptionalInt.of(1));
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
        private SkillRetentionRootAuditService.IndexLifecycle lifecycle;

        private Coordinate(int tick) {
            this.tick = tick;
        }

        @Override
        public boolean sameThread(Object candidateServer) {
            return candidateServer == server && Thread.currentThread() == thread;
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
        public P4E1PlayerDataDirectorySnapshot.FinalVerificationResult directoryCurrentness() {
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
}
