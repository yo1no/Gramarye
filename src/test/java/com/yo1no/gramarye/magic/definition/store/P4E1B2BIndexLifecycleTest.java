package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class P4E1B2BIndexLifecycleTest {
    @Test
    void noEntryBaselineAndEveryAcceptedTerminalConsumeExactlyOneGeneration() {
        var owner = new Object();
        var server = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(owner, server);

        assertTrue(lifecycle.isNoEntry(owner, server));
        assertEquals(0L, lifecycle.generation(owner, server));
        var baselineIdentity = lifecycle.stateIdentity(owner, server);

        for (var expected = 1L; expected <= 4L; expected++) {
            var exactExpected = expected;
            assertTrue(lifecycle.execute(owner, server, scope -> {
                assertEquals(exactExpected, scope.generation());
                assertTrue(scope.isCurrent(owner, server));
                scope.finishIncomplete(owner, server);
            }));
            assertTrue(lifecycle.isIncomplete(owner, server));
            assertEquals(expected, lifecycle.generation(owner, server));
        }
        assertNotSame(baselineIdentity, lifecycle.stateIdentity(owner, server));
    }

    @Test
    void actualNonCompleteTerminalVariantsPublishOnlyTheReservedFallback() {
        var owner = new Object();
        var server = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(owner, server);
        var terminals = new SkillRetentionRootAuditResult[] {
            new SkillRetentionRootAuditResult.Incomplete(
                    SkillRetentionRootAuditResult.IncompleteReason.STORE_UNAVAILABLE,
                    SkillRetentionRootAuditResult.Diagnostic.simple(
                            SkillRetentionRootAuditResult.Stage.STORE_REFERENCE_OWNER_AUDIT),
                    SkillRetentionRootAuditResult.AuditSummary.generationOnly(1L)),
            new SkillRetentionRootAuditResult.OverLimit(
                    SkillRetentionRootAuditResult.Counter.RAW_ROOT_CLAIMS,
                    SkillRetentionRootAuditResult.Stage.RAW_ROOT_CAPTURE,
                    4L,
                    3L,
                    SkillRetentionRootAuditResult.AuditSummary.generationOnly(2L)),
            new SkillRetentionRootAuditResult.ReconciliationRequired(
                    SkillRetentionRootAuditResult.ReconciliationReason.STORE_OWNER_MISMATCH,
                    SkillRetentionRootAuditResult.Disposition.ONLINE,
                    1,
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    SkillRetentionRootAuditResult.AuditSummary.generationOnly(3L)),
            new SkillRetentionRootAuditResult.Incomplete(
                    SkillRetentionRootAuditResult.IncompleteReason
                            .ONLINE_SOURCE_FRESHNESS_LOST,
                    SkillRetentionRootAuditResult.Diagnostic.simple(
                            SkillRetentionRootAuditResult.Stage.FINAL_FRESHNESS),
                    SkillRetentionRootAuditResult.AuditSummary.generationOnly(4L))
        };

        for (var index = 0; index < terminals.length; index++) {
            var expected = terminals[index];
            var observed = new AtomicReference<SkillRetentionRootAuditResult>();
            assertTrue(lifecycle.execute(owner, server, scope -> observed.set(
                    scope.finishIncomplete(owner, server, expected))));
            assertSame(expected, observed.get());
            assertTrue(lifecycle.isIncomplete(owner, server));
            assertEquals(index + 1L, lifecycle.generation(owner, server));
            assertEquals(index + 1L,
                    observed.get().summary().indexGeneration().orElseThrow());
        }

        assertInstanceOf(SkillRetentionRootAuditResult.Incomplete.class, terminals[0]);
        assertInstanceOf(SkillRetentionRootAuditResult.OverLimit.class, terminals[1]);
        assertInstanceOf(
                SkillRetentionRootAuditResult.ReconciliationRequired.class, terminals[2]);
        assertInstanceOf(SkillRetentionRootAuditResult.Incomplete.class, terminals[3]);
    }

    @Test
    void preparedCompleteRuntimeCannotPartiallyPublishAndMapsAtTheSameGeneration() {
        var owner = new Object();
        var server = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(owner, server);
        var backing = rawBacking();
        var prepared = new AtomicReference<SkillRetentionRootAuditService.PreparedComplete>();
        var failure = new IllegalStateException("publication sentinel");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(owner, server, scope -> {
                    backing.markAudited();
                    var sources =
                            new SkillRetentionRootAuditService.PublicationSource[] {
                                journalSource(backing.size())
                            };
                    prepared.set(scope.prepareComplete(
                            owner,
                            server,
                            backing,
                            sources,
                            Thread.currentThread(),
                            31,
                            verifiedSeal(),
                            SkillRetentionRootAuditResult.AuditSummary.generationOnly(
                                    scope.generation())));
                    throw failure;
                })));

        assertTrue(lifecycle.isIncomplete(owner, server));
        assertEquals(1L, lifecycle.generation(owner, server));
        assertEquals(P4E1RawClaimBuffer.Ownership.AUDITED, backing.ownership());
        var mapped = lifecycle.mapReservedRuntime(owner, server, 1L, failure);
        assertEquals(
                SkillRetentionRootAuditResult.IncompleteReason.INTERNAL_RUNTIME_FAILURE,
                mapped.reason());
        assertEquals(1L, mapped.summary().indexGeneration().orElseThrow());

        var coordinate = new FixedCoordinate(owner, server, lifecycle, 31);
        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        owner,
                        server,
                        prepared.get().result(),
                        coordinate,
                        coordinate));
        assertThrows(IllegalStateException.class,
                () -> SkillRetentionRootAuditService.consumeCompleteAtCoordinate(
                        owner,
                        server,
                        prepared.get().result(),
                        coordinate,
                        coordinate));
        backing.discard();
        assertEquals(P4E1RawClaimBuffer.Ownership.DISCARDED, backing.ownership());
    }

    @Test
    void errorAndOomeEscapeByIdentityAndLeaveTheReservedIncompleteGeneration() {
        var errorOwner = new Object();
        var errorServer = new Object();
        var errorLifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                errorOwner, errorServer);
        var error = new AssertionError("sentinel");

        assertSame(error, assertThrows(AssertionError.class,
                () -> errorLifecycle.execute(errorOwner, errorServer, scope -> {
                    assertEquals(1L, scope.generation());
                    throw error;
                })));
        assertTrue(errorLifecycle.isIncomplete(errorOwner, errorServer));
        assertEquals(1L, errorLifecycle.generation(errorOwner, errorServer));

        var oomeOwner = new Object();
        var oomeServer = new Object();
        var oomeLifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                oomeOwner, oomeServer);
        var oome = new OutOfMemoryError("sentinel");
        assertSame(oome, assertThrows(OutOfMemoryError.class,
                () -> oomeLifecycle.execute(oomeOwner, oomeServer, scope -> {
                    assertEquals(1L, scope.generation());
                    throw oome;
                })));
        assertTrue(oomeLifecycle.isIncomplete(oomeOwner, oomeServer));
        assertEquals(1L, oomeLifecycle.generation(oomeOwner, oomeServer));
    }

    @Test
    void priorAuthorityDiscardRuntimeMapsOnlyAfterSameGenerationFallback() {
        var owner = new Object();
        var server = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(owner, server);
        var sourceWork = new AtomicInteger();
        var failure = new IllegalStateException("discard failure");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(
                        owner,
                        server,
                        scope -> sourceWork.incrementAndGet(),
                        () -> {
                            throw failure;
                        })));
        assertEquals(0, sourceWork.get());
        assertTrue(lifecycle.isIncomplete(owner, server));
        assertEquals(1L, lifecycle.generation(owner, server));

        var mapped = lifecycle.mapReservedRuntime(owner, server, 1L, failure);
        assertEquals(
                SkillRetentionRootAuditResult.IncompleteReason.INTERNAL_RUNTIME_FAILURE,
                mapped.reason());
        assertEquals(SkillRetentionRootAuditResult.Stage.INDEX_PUBLICATION,
                mapped.diagnostic().stage());
        assertEquals(failure.getClass().getName(),
                mapped.diagnostic().exceptionClassName());
        assertEquals(1L, mapped.summary().indexGeneration().orElseThrow());
    }

    @Test
    void maxMinusOneAdvancesToMaxThenExhaustsWithoutRunningSourceWork() {
        var owner = new Object();
        var server = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(
                owner, server, Long.MAX_VALUE - 1L);
        var sourceWork = new AtomicInteger();

        assertTrue(lifecycle.execute(owner, server, scope -> {
            sourceWork.incrementAndGet();
            assertEquals(Long.MAX_VALUE, scope.generation());
        }));
        assertEquals(Long.MAX_VALUE, lifecycle.generation(owner, server));
        var maximumIncomplete = lifecycle.stateIdentity(owner, server);

        assertFalse(lifecycle.execute(owner, server, scope -> sourceWork.incrementAndGet()));
        assertEquals(1, sourceWork.get());
        assertTrue(lifecycle.isExhausted(owner, server));
        assertEquals(Long.MAX_VALUE, lifecycle.generation(owner, server));
        assertNotSame(maximumIncomplete, lifecycle.stateIdentity(owner, server));
        var exhaustedIdentity = lifecycle.stateIdentity(owner, server);

        assertFalse(lifecycle.execute(owner, server, scope -> sourceWork.incrementAndGet()));
        assertSame(exhaustedIdentity, lifecycle.stateIdentity(owner, server));
        assertEquals(1, sourceWork.get());
        lifecycle.invalidate(owner, server);
        assertSame(exhaustedIdentity, lifecycle.stateIdentity(owner, server));
        assertEquals(Long.MAX_VALUE, lifecycle.generation(owner, server));

        var otherOwner = new Object();
        var otherServer = new Object();
        var other = new SkillRetentionRootAuditService.IndexLifecycle(
                otherOwner, otherServer, Long.MAX_VALUE);
        assertFalse(other.execute(
                otherOwner, otherServer, scope -> sourceWork.incrementAndGet()));
        assertNotSame(exhaustedIdentity, other.stateIdentity(otherOwner, otherServer));
    }

    @Test
    void invalidationReentrancyRemovalAndTwoCoordinatesUseTheSameStateEngine() {
        var firstOwner = new Object();
        var firstServer = new Object();
        var secondOwner = new Object();
        var secondServer = new Object();
        var first = new SkillRetentionRootAuditService.IndexLifecycle(
                firstOwner, firstServer);
        var second = new SkillRetentionRootAuditService.IndexLifecycle(
                secondOwner, secondServer);

        first.invalidate(firstOwner, firstServer);
        assertEquals(1L, first.generation(firstOwner, firstServer));
        assertEquals(0L, second.generation(secondOwner, secondServer));
        assertNotSame(
                first.stateIdentity(firstOwner, firstServer),
                second.stateIdentity(secondOwner, secondServer));

        assertTrue(first.execute(firstOwner, firstServer, scope -> {
            assertEquals(2L, scope.generation());
            assertThrows(IllegalStateException.class,
                    () -> first.execute(firstOwner, firstServer, nested -> {
                        throw new AssertionError("reentrant source work ran");
                    }));
            assertEquals(2L, scope.generation());
        }));
        assertEquals(2L, first.generation(firstOwner, firstServer));

        first.invalidate(firstOwner, firstServer);
        assertEquals(3L, first.generation(firstOwner, firstServer));
        first.remove(firstOwner, firstServer);
        assertTrue(first.isRemoved(firstOwner, firstServer));
        assertThrows(IllegalStateException.class,
                () -> first.execute(firstOwner, firstServer, scope -> { }));
        assertEquals(0L, second.generation(secondOwner, secondServer));
    }

    @Test
    void wrongOwnerOrServerCannotReadOrMutateTheBoundCoordinate() {
        var owner = new Object();
        var server = new Object();
        var wrongOwner = new Object();
        var wrongServer = new Object();
        var lifecycle = new SkillRetentionRootAuditService.IndexLifecycle(owner, server);
        var sourceWork = new AtomicInteger();
        var initialState = lifecycle.stateIdentity(owner, server);

        assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(
                        wrongOwner, server, scope -> sourceWork.incrementAndGet()));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.execute(
                        owner, wrongServer, scope -> sourceWork.incrementAndGet()));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.invalidate(wrongOwner, server));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.remove(owner, wrongServer));
        assertThrows(IllegalStateException.class,
                () -> lifecycle.generation(wrongOwner, server));

        assertEquals(0, sourceWork.get());
        assertEquals(0L, lifecycle.generation(owner, server));
        assertSame(initialState, lifecycle.stateIdentity(owner, server));
        assertTrue(lifecycle.isNoEntry(owner, server));

        assertTrue(lifecycle.execute(owner, server, scope -> {
            var reservedState = lifecycle.stateIdentity(owner, server);
            assertThrows(IllegalStateException.class,
                    () -> scope.finishIncomplete(wrongOwner, server));
            assertThrows(IllegalStateException.class,
                    () -> scope.isCurrent(owner, wrongServer));
            assertSame(reservedState, lifecycle.stateIdentity(owner, server));
            assertTrue(scope.isCurrent(owner, server));
            scope.finishIncomplete(owner, server);
        }));
        assertEquals(1L, lifecycle.generation(owner, server));
    }

    @Test
    void truncatedOnlineFirstExcessPublishesNoFakeExactObservedSummary() throws Exception {
        var budget = P4E1TestBudgets.create();
        assertTrue(budget.checkpointSingle(
                P4E1AuditCounter.RELEVANT_RECORDS,
                P4E1AuditStage.RELEVANT_RECORDS,
                1L).isEmpty());
        var maximum = budget.maximum(P4E1AuditCounter.RELEVANT_RECORDS);

        var terminal = P4E1GlobalSourceCapture.onlineRelevantCapacityFailure(
                budget, Math.addExact(maximum, 2L));

        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                terminal.failure().code());
        assertEquals(P4E1AuditCounter.RELEVANT_RECORDS,
                terminal.failure().counter().orElseThrow());
        assertEquals(P4E1AuditStage.RELEVANT_RECORDS,
                terminal.failure().stage());
        assertEquals(maximum + 1L, terminal.failure().observedAtLeast());
        assertEquals(P4E1GlobalSourceCapture.ObservedSummary.empty(),
                terminal.observedSummary());
        assertEquals(1L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));

        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E1GlobalSourceCapture.java"));
        var onlineCapture = source.indexOf("var onlineCapture = captureOnlineIdentities(");
        var integrated = source.indexOf(
                "var integrated = P4E1IntegratedSnapshotTraversal.captureForGlobal(",
                onlineCapture);
        var firstExcess = source.indexOf(
                "if (onlineCapture.relevantCapacityGuaranteed())", integrated);
        var arbitration = source.indexOf("var selected = arbitrate(", firstExcess);
        assertTrue(onlineCapture >= 0
                && integrated > onlineCapture
                && firstExcess > integrated
                && arbitration > firstExcess);
    }

    @Test
    void completeAuthorityShellConsumesBeforeValidationAndNeverLeaksIntoDiagnostics() {
        var authority = new SkillRetentionRootAuditResult.CompleteAuthority() { };
        var complete = SkillRetentionRootAuditResult.complete(
                SkillRetentionRootAuditResult.AuditSummary.generationOnly(7L), authority);

        assertSame(authority, complete.claimAuthority());
        assertThrows(IllegalStateException.class, complete::claimAuthority);
        assertEquals("Complete[summary="
                + SkillRetentionRootAuditResult.AuditSummary.generationOnly(7L) + "]",
                complete.toString());
    }

    @Test
    void permitBindingUsesConsumeFirstExactCoordinatesAndClearsAllReferences() {
        var service = new Object();
        var server = new Object();
        var thread = Thread.currentThread();
        var state = new Object();
        var binding = new SkillRetentionRootAuditService.PermitBinding(
                service, server, thread, 41, 7L);
        binding.bindState(state, 7L);

        assertThrows(IllegalStateException.class, () -> binding.requireService(service));
        binding.claim();
        binding.requireService(service);
        binding.requireServer(server);
        binding.requireThread(thread);
        binding.requireTick(41);
        binding.requireState(state, 7L);
        assertThrows(IllegalStateException.class, binding::claim);

        binding.clearAfterClaim();
        assertTrue(binding.referencesCleared());
        assertThrows(IllegalStateException.class, () -> binding.requireState(state, 7L));
        assertThrows(IllegalStateException.class, binding::claim);
    }

    @Test
    void everyPermitCoordinateMisuseIsConsumedAndCannotBeRetried() {
        for (var misuse : PermitMisuse.values()) {
            var service = new Object();
            var server = new Object();
            var thread = Thread.currentThread();
            var state = new Object();
            var binding = new SkillRetentionRootAuditService.PermitBinding(
                    service, server, thread, 51, 9L);
            binding.bindState(state, 9L);
            if (misuse == PermitMisuse.REVOKED) {
                binding.revoke();
            }
            binding.claim();

            assertThrows(IllegalStateException.class, () -> {
                switch (misuse) {
                    case SERVICE -> binding.requireService(new Object());
                    case SERVER -> binding.requireServer(new Object());
                    case THREAD -> binding.requireThread(new Thread());
                    case TICK -> binding.requireTick(52);
                    case STATE -> binding.requireState(new Object(), 9L);
                    case GENERATION -> binding.requireState(state, 10L);
                    case REVOKED -> binding.requireState(state, 9L);
                }
            });
            binding.clearAfterClaim();
            assertTrue(binding.referencesCleared());
            assertThrows(IllegalStateException.class, binding::claim);
        }
    }

    @Test
    void productionOwnerUsesIdentityIndexAndWeakExactStoppedTombstones() throws Exception {
        var root = projectRoot();
        var source = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "SkillRetentionRootAuditService.java"));

        assertTrue(source.contains(
                "IdentityHashMap<MinecraftServer, IndexSlot> index = new IdentityHashMap<>()"));
        assertTrue(source.contains("slot = new IndexSlot(this, server)"));
        assertTrue(source.contains(
                "lifecycle = new IndexLifecycle(ownerIdentity, serverIdentity)"));
        assertFalse(source.contains("IndexLifecycle()"));
        assertFalse(source.contains("IndexLifecycle(long initialGeneration)"));
        assertFalse(source.contains("private final PermitCell permit;"));
        assertTrue(source.contains("WeakReference<PermitCell> permitRegistration"));
        assertTrue(source.contains("extends WeakReference<MinecraftServer>"));
        assertTrue(source.contains("ReferenceQueue<MinecraftServer>"));
        assertFalse(source.contains("WeakHashMap"));
        var tombstone = source.indexOf(
                "stoppedServers.add(new StoppedServerRef(server, stoppedQueue))");
        var removal = source.indexOf("var slot = index.remove(server)", tombstone);
        assertTrue(tombstone >= 0 && removal > tombstone);
        var completeCleanup = source.substring(
                source.indexOf("private static void discardAuthority(IndexState state)"),
                source.indexOf("private static void discardForRemoval(IndexState state)"));
        assertTrue(completeCleanup.contains("complete.permitRegistration"));
        assertTrue(completeCleanup.contains("registration.get()"));
        assertTrue(completeCleanup.contains("permit.revoke()"));
        assertTrue(completeCleanup.contains("registration.clear()"));
        assertTrue(completeCleanup.contains("finally"));
        assertTrue(completeCleanup.contains("complete.backing.discard()"));
        var removalCleanup = source.substring(
                source.indexOf("private static void discardForRemoval(IndexState state)"),
                source.indexOf("private void expungeStoppedServers()"));
        assertTrue(removalCleanup.contains("active.lease.revoke()"));
        assertTrue(removalCleanup.contains("finally"));
        assertTrue(removalCleanup.contains("active.backing.discard()"));
        assertTrue(source.contains("handoff.forceInvalidate(this)"));

        var retainedIndex = source.substring(
                source.indexOf("private sealed interface IndexState"),
                source.indexOf("/** Consume-first exact-coordinate authority"));
        for (var forbiddenWitness : new String[] {
            "P4E1AuditedCapture",
            "P4E1GlobalSourceCapture.SourceEntry",
            "P4E1PlayerDataDirectorySnapshot",
            "P4E1PendingJournalObservation",
            "ServerPlayer",
            "PlayerList",
            "SavedData",
            "CompoundTag",
            "java.nio.file.Path",
            "SkillReference[]",
            "List<SkillReference>"
        }) {
            assertFalse(retainedIndex.contains(forbiddenWitness), forbiddenWitness);
        }
        assertTrue(retainedIndex.contains("private P4E1RawClaimBuffer claims;"));
        assertTrue(retainedIndex.contains("private IndexedSource[] sources;"));
        assertTrue(source.contains(
                "var backing = new IndexedBacking(expectedBacking, indexedSources);"));
        assertTrue(source.contains(
                "scope.publish(this, server, published);"));
    }

    @Test
    void witnessCleanupRethrowsFirstRuntimeBeforeBackingCanBecomeIndexed() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E1AuditedCapture.java"));
        var release = source.substring(
                source.indexOf("void releaseBacking("),
                source.indexOf("void discard()", source.indexOf("void releaseBacking(")));
        var cleanup = source.substring(
                source.indexOf("private void discardWitnesses()"),
                source.indexOf("private void clearReferences()"));

        assertTrue(release.indexOf("discardWitnesses()")
                < release.indexOf("claims.markIndexed()"));
        assertTrue(cleanup.contains("RuntimeException firstFailure = null"));
        assertTrue(cleanup.contains("if (firstFailure == null)"));
        assertTrue(cleanup.contains("throw firstFailure"));
        assertFalse(cleanup.contains("catch (Error"));
        assertFalse(cleanup.contains("catch (OutOfMemoryError"));
        assertFalse(cleanup.contains("catch (Throwable"));
    }

    private static P4E1RawClaimBuffer rawBacking() {
        var backing = new P4E1RawClaimBuffer();
        var reservation = assertInstanceOf(
                P4E1RawClaimBuffer.ReservationResult.Reserved.class,
                backing.reserve(P4E1TestBudgets.create(), 0, 1)).reservation();
        reservation.appendLatest(new com.yo1no.gramarye.magic.definition.document.SkillReference(
                StoreTestFixtures.skillId(10), StoreTestFixtures.revision(10)));
        reservation.finish();
        return backing;
    }

    private static SkillRetentionRootAuditService.PublicationSource journalSource(
            int claimCount) {
        return new SkillRetentionRootAuditService.PublicationSource(
                P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL,
                P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL,
                Optional.empty(),
                0,
                0,
                claimCount);
    }

    private static P4E1FinalFreshness.FreshnessSeal verifiedSeal() {
        var verified = assertInstanceOf(
                P4E1FinalFreshness.VerificationResult.Verified.class,
                P4E1FinalFreshness.verify(new AlwaysCurrentFreshnessInput()));
        return verified.seal();
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

    private static final class FixedCoordinate
            implements SkillRetentionRootAuditService.CallChainCurrentness,
                    SkillRetentionRootAuditService.CompleteCoordinate {
        private final Object owner;
        private final Object server;
        private final SkillRetentionRootAuditService.IndexLifecycle lifecycle;
        private final int tick;

        private FixedCoordinate(
                Object owner,
                Object server,
                SkillRetentionRootAuditService.IndexLifecycle lifecycle,
                int tick) {
            this.owner = owner;
            this.server = server;
            this.lifecycle = lifecycle;
            this.tick = tick;
        }

        @Override
        public boolean sameThread(Object candidateServer) {
            return candidateServer == server;
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
            if (candidateOwner != owner || candidateServer != server) {
                throw new IllegalStateException("wrong coordinate");
            }
            return lifecycle;
        }
    }

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

    private enum PermitMisuse {
        SERVICE,
        SERVER,
        THREAD,
        TICK,
        STATE,
        GENERATION,
        REVOKED
    }
}
