package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.Test;

/** Actual core-owner/task/handler tests; platform contexts here are typed components, not authentication proof. */
final class P7D3CoreOwnershipTest {
    private static final UUID PLAYER = new UUID(0, 903);

    @Test
    void lifecycleRejectsUnstartedStoppedAndOldGenerationWithoutResettingAccounting() {
        var owner = new P7PendingPermitOwner();
        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE, owner.acquire(PLAYER, 1, 1).outcome());
        var generation = owner.startServer();
        assertThrows(P7SemanticInvariantException.class, owner::startServer);
        var old = owner.acquire(PLAYER, 1, generation).permit().orElseThrow();
        assertEquals(1, owner.stopAll());
        assertEquals(0, owner.stopAll());
        assertEquals(generation + 1, owner.captureServerGeneration());
        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE,
                owner.acquire(PLAYER, 1, generation + 1).outcome());
        var next = owner.startServer();
        assertEquals(P7PendingPermitOwner.AcquireOutcome.STALE_GENERATION,
                owner.acquire(PLAYER, 1, generation).outcome());
        var current = owner.acquire(PLAYER, 1, next).permit().orElseThrow();
        assertEquals(0, owner.invalidateSession(new P7SessionIdentity(PLAYER, 1, generation)));
        old.releaseAfterTask();
        old.releaseAfterEnqueueFailure();
        assertEquals(1, owner.serverPending());
        assertFalse(current.released());
        current.release();
    }

    @Test
    void maximumGenerationCleansOwnedPermitsBeforeSealingExhaustion() throws Exception {
        var owner = new P7PendingPermitOwner();
        var field = P7PendingPermitOwner.class.getDeclaredField("serverGeneration");
        field.setAccessible(true);
        field.setLong(owner, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, owner.startServer());
        var permit = owner.acquire(PLAYER, 1, Long.MAX_VALUE).permit().orElseThrow();
        assertTrue(permit.tryStartTask());
        assertThrows(P7SemanticInvariantException.class, owner::stopAll);
        assertTrue(permit.released());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertEquals(Long.MAX_VALUE, owner.captureServerGeneration());
        permit.releaseAfterTask();
        assertEquals(0, owner.stopAll());
        assertThrows(P7SemanticInvariantException.class, owner::startServer);
        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE,
                owner.acquire(PLAYER, 1, Long.MAX_VALUE).outcome());
    }

    @Test
    void taskRejectsDifferentPlayerEpochOrGenerationBeforeTakingPermitOwnership() {
        var owner = started();
        var permit = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
        for (var identity : new P7SessionIdentity[] {
                new P7SessionIdentity(new UUID(0, 904), 1, 1),
                new P7SessionIdentity(PLAYER, 2, 1),
                new P7SessionIdentity(PLAYER, 1, 2)}) {
            assertThrows(P7SemanticInvariantException.class, () -> new P7ServerDispatchTask(
                    new P7QueuedCastIntent(identity, intent()), ignored -> {}, permit));
            assertEquals(1, owner.serverPending());
            assertFalse(permit.released());
        }
        permit.release();
        assertThrows(P7SemanticInvariantException.class, permit::tryStartTask);
        assertThrows(P7SemanticInvariantException.class, permit::releaseAfterEnqueueFailure);
    }

    @Test
    void failedPartialSubmissionCancelsOnlyItsNotYetStartedTaskAndKeepsPrimary() {
        for (Throwable failure : new Throwable[] {new IllegalStateException(), new AssertionError()}) {
            var owner = started();
            var context = new Context(false, failure);
            var calls = new int[1];
            var source = new Source(context.connection);
            var composition = composition(source, owner, ignored -> calls[0]++);
            var observed = assertThrows(failure.getClass(), () -> P7CastIntentNetworkHandler.handleAuthenticated(
                    new CastIntentPayload(intent()), PLAYER, context, composition));
            assertSame(failure, observed);
            assertEquals(1, context.enqueueCalls);
            assertEquals(0, owner.serverPending());
            context.task.run(); // Original submission retained the task before throwing.
            assertEquals(0, calls[0]);
            assertEquals(0, owner.serverPending());
        }
    }

    @Test
    void actualInlineEnqueueFailureDoesNotDoubleReleaseOrReplaceDispatchPrimary() {
        for (Throwable failure : new Throwable[] {new IllegalStateException(), new AssertionError()}) {
            var owner = started();
            var context = new Context(true, null);
            var calls = new int[1];
            var composition = composition(new Source(context.connection), owner, ignored -> {
                calls[0]++;
                fail(failure);
            });
            var observed = assertThrows(failure.getClass(), () -> P7CastIntentNetworkHandler.handleAuthenticated(
                    new CastIntentPayload(intent()), PLAYER, context, composition));
            assertSame(failure, observed);
            assertEquals(0, observed.getSuppressed().length);
            assertEquals(1, calls[0]);
            assertEquals(0, owner.serverPending());
            assertThrows(P7SemanticInvariantException.class, context.task::run);
        }
    }

    @Test
    void sameCaptureRecheckFailureReleasesPermitWithoutRefreshOrEnqueue() {
        for (boolean throwing : new boolean[] {false, true}) {
            var owner = started();
            var context = new Context(false, null);
            var source = new Source(context.connection);
            source.current = false;
            var failure = new AssertionError();
            source.recheckFailure = throwing ? failure : null;
            var composition = composition(source, owner, ignored -> { throw new AssertionError(); });
            if (throwing) {
                assertSame(failure, assertThrows(AssertionError.class, () -> P7CastIntentNetworkHandler.handleAuthenticated(
                        new CastIntentPayload(intent()), PLAYER, context, composition)));
            } else {
                P7CastIntentNetworkHandler.handleAuthenticated(new CastIntentPayload(intent()), PLAYER, context, composition);
            }
            assertEquals(1, source.captures);
            assertEquals(1, source.rechecks);
            assertEquals(0, context.enqueueCalls);
            assertEquals(0, owner.serverPending());
        }
    }

    @Test
    void returnedFutureDoesNotProveExecutionAndStartedTaskKeepsReleaseOwnership() {
        var owner = started();
        var context = new Context(false, null);
        var calls = new int[1];
        P7CastIntentNetworkHandler.handleAuthenticated(new CastIntentPayload(intent()), PLAYER, context,
                composition(new Source(context.connection), owner, ignored -> calls[0]++));
        assertEquals(0, calls[0]);
        assertEquals(1, owner.serverPending());
        context.task.run();
        assertEquals(1, calls[0]);
        assertEquals(0, owner.serverPending());

        var permit = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
        assertTrue(permit.tryStartTask());
        permit.releaseAfterEnqueueFailure();
        assertEquals(1, owner.serverPending());
        permit.releaseAfterTask();
        permit.releaseAfterEnqueueFailure();
        assertThrows(P7SemanticInvariantException.class, permit::releaseAfterTask);
        assertThrows(P7SemanticInvariantException.class, permit::release);
    }

    @Test
    void cleanupSecondaryNeverReplacesTheOriginalDispatchError() {
        var owner = started();
        var permit = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
        var failure = new AssertionError();
        var task = new P7ServerDispatchTask(new P7QueuedCastIntent(new P7SessionIdentity(PLAYER, 1, 1), intent()),
                ignored -> {
                    permit.releaseAfterTask(); // Deliberate illegal cleanup ownership to exercise the secondary path.
                    throw failure;
                }, permit);
        assertSame(failure, assertThrows(AssertionError.class, task::run));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0] instanceof P7SemanticInvariantException);
        assertEquals(0, owner.serverPending());
    }

    @Test
    void captureResultCannotCarryAuthorityOnARejectedOutcome() {
        var identity = new P7SessionIdentity(PLAYER, 1, 1);
        for (var outcome : P7ConnectionEpochSnapshotSource.CaptureOutcome.values()) {
            if (outcome == P7ConnectionEpochSnapshotSource.CaptureOutcome.CAPTURED) {
                assertThrows(P7SemanticInvariantException.class, () ->
                        new P7ConnectionEpochSnapshotSource.CaptureResult(outcome, Optional.empty()));
            } else {
                assertThrows(P7SemanticInvariantException.class, () ->
                        new P7ConnectionEpochSnapshotSource.CaptureResult(outcome, Optional.of(identity)));
            }
        }
    }

    @Test
    void acquirePublicationFaultRestoresOnlyTheNewPermitAndPriorAccounting() throws Exception {
        for (boolean existing : new boolean[] {false, true}) {
            for (boolean after : new boolean[] {false, true}) {
                for (Throwable primary : new Throwable[] {new IllegalStateException(), new AssertionError()}) {
                    var owner = started();
                    var prior = existing ? owner.acquire(PLAYER, 1, 1).permit().orElseThrow() : null;
                    var counts = new FaultMap<Integer>(after, primary);
                    counts.putAll(fieldMap(owner, "perPlayerPending"));
                    replaceField(owner, "perPlayerPending", counts);
                    assertSame(primary, assertThrows(primary.getClass(), () -> owner.acquire(PLAYER, 1, 1)));
                    assertEquals(existing ? 1 : 0, owner.serverPending());
                    assertEquals(existing ? 1 : 0, owner.playerPending(PLAYER));
                    assertEquals(existing ? 1 : 0, fieldMap(owner, "activePermitsByPlayer").size());
                    if (prior != null) { assertFalse(prior.released()); prior.release(); }
                    var next = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
                    next.release();
                    assertEquals(0, owner.serverPending());
                }
            }
        }
        for (boolean after : new boolean[] {false, true}) {
            var owner = started();
            var primary = new AssertionError();
            replaceField(owner, "activePermitsByPlayer", new FaultMap<Set<P7PendingPermit>>(after, primary));
            assertSame(primary, assertThrows(AssertionError.class, () -> owner.acquire(PLAYER, 1, 1)));
            assertEquals(0, owner.serverPending());
            assertEquals(0, owner.trackedPlayerCount());
            assertTrue(fieldMap(owner, "activePermitsByPlayer").isEmpty());
            owner.acquire(PLAYER, 1, 1).permit().orElseThrow().release();
        }
    }

    @Test
    void existingIdentitySetMutationFailureDoesNotStealEarlierWork() throws Exception {
        for (boolean after : new boolean[] {false, true}) {
            var owner = started();
            var prior = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
            Map<UUID, Set<P7PendingPermit>> sets = fieldMap(owner, "activePermitsByPlayer");
            var delegate = sets.get(PLAYER);
            var primary = new AssertionError();
            sets.put(PLAYER, new AbstractSet<>() {
                private boolean failOnce = true;
                public Iterator<P7PendingPermit> iterator() { return delegate.iterator(); }
                public int size() { return delegate.size(); }
                public boolean add(P7PendingPermit value) {
                    if (!failOnce) { return delegate.add(value); }
                    failOnce = false;
                    if (after) { delegate.add(value); }
                    throw primary;
                }
            });
            assertSame(primary, assertThrows(AssertionError.class, () -> owner.acquire(PLAYER, 1, 1)));
            assertEquals(Set.of(prior), delegate);
            assertEquals(1, owner.serverPending());
            assertEquals(1, owner.playerPending(PLAYER));
            assertFalse(prior.released());
            prior.release();
            assertEquals(0, owner.serverPending());
        }
    }

    @Test
    void rollbackFailurePreservesPrimaryAttemptsIndexCleanupAndSealsAdmission() throws Exception {
        var owner = started();
        var primary = new AssertionError();
        var secondary = new IllegalStateException();
        var counts = new FaultMap<Integer>(true, primary);
        counts.removeFailure = secondary;
        replaceField(owner, "perPlayerPending", counts);
        assertSame(primary, assertThrows(AssertionError.class, () -> owner.acquire(PLAYER, 1, 1)));
        assertEquals(1, primary.getSuppressed().length);
        assertSame(secondary, primary.getSuppressed()[0]);
        assertTrue(fieldMap(owner, "activePermitsByPlayer").isEmpty());
        assertEquals(0, owner.serverPending());
        assertEquals(1, owner.playerPending(PLAYER)); // Explicitly incomplete, never claimed clean.
        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE,
                owner.acquire(PLAYER, 1, 1).outcome());
        assertThrows(P7SemanticInvariantException.class, owner::stopAll);
        assertThrows(P7SemanticInvariantException.class, owner::startServer);
    }

    @Test
    void failedStopSealsTaskStartBeforeGenerationCanAdvance() throws Exception {
        var owner = started();
        var permit = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
        var primary = new AssertionError();
        Map<UUID, Set<P7PendingPermit>> original = fieldMap(owner, "activePermitsByPlayer");
        var failing = new HashMap<UUID, Set<P7PendingPermit>>(original) {
            private static final long serialVersionUID = 1L;
            @Override public java.util.Collection<Set<P7PendingPermit>> values() { throw primary; }
        };
        replaceField(owner, "activePermitsByPlayer", failing);
        assertSame(primary, assertThrows(AssertionError.class, owner::stopAll));
        assertEquals(1, owner.captureServerGeneration());
        assertEquals(1, owner.serverPending());
        assertFalse(permit.released()); // The failed stop is not classified as cleanup success.
        assertFalse(permit.tryStartTask());
        var dispatches = new int[1];
        new P7ServerDispatchTask(new P7QueuedCastIntent(new P7SessionIdentity(PLAYER, 1, 1), intent()),
                ignored -> dispatches[0]++, permit).run();
        assertEquals(0, dispatches[0]);
        assertThrows(P7SemanticInvariantException.class, owner::stopAll);
        assertThrows(P7SemanticInvariantException.class, owner::startServer);
        permit.releaseAfterEnqueueFailure();
        assertEquals(0, owner.serverPending());
    }

    @Test
    void failedRollbackSealsEarlierNotYetStartedWorkWithoutStealingItsCleanup() throws Exception {
        var owner = started();
        var prior = owner.acquire(PLAYER, 1, 1).permit().orElseThrow();
        var primary = new AssertionError();
        var secondary = new IllegalStateException();
        var counts = new FaultMap<Integer>(true, primary);
        counts.putAll(fieldMap(owner, "perPlayerPending"));
        counts.replaceFailure = secondary;
        replaceField(owner, "perPlayerPending", counts);
        assertSame(primary, assertThrows(AssertionError.class, () -> owner.acquire(PLAYER, 1, 1)));
        assertSame(secondary, primary.getSuppressed()[0]);
        assertEquals(1, owner.serverPending());
        assertEquals(1, owner.playerPending(PLAYER));
        assertFalse(prior.released());
        assertFalse(prior.tryStartTask());
        assertEquals(P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE,
                owner.acquire(PLAYER, 1, 1).outcome());
        prior.releaseAfterEnqueueFailure();
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.stopAll());
        assertThrows(P7SemanticInvariantException.class, owner::startServer);
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<UUID, T> fieldMap(P7PendingPermitOwner owner, String name) throws Exception {
        var field = P7PendingPermitOwner.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(owner);
    }

    private static void replaceField(P7PendingPermitOwner owner, String name, Object value) throws Exception {
        var field = P7PendingPermitOwner.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    /** Controlled collection boundaries only; this does not simulate actual JVM resource exhaustion. */
    private static final class FaultMap<V> extends HashMap<UUID, V> {
        private static final long serialVersionUID = 1L;
        private final boolean after;
        private Throwable failure;
        private RuntimeException removeFailure;
        private RuntimeException replaceFailure;
        private FaultMap(boolean after, Throwable failure) { this.after = after; this.failure = failure; }
        @Override public V put(UUID key, V value) {
            var primary = failure;
            failure = null;
            if (primary == null) { return super.put(key, value); }
            if (after) { super.put(key, value); }
            fail(primary);
            throw new AssertionError("unreachable");
        }
        @Override public V remove(Object key) {
            if (removeFailure != null) { throw removeFailure; }
            return super.remove(key);
        }
        @Override public V replace(UUID key, V value) {
            var previous = super.replace(key, value);
            if (replaceFailure != null) { throw replaceFailure; }
            return previous;
        }
    }

    private static P7PendingPermitOwner started() {
        var owner = new P7PendingPermitOwner();
        owner.startServer();
        return owner;
    }

    private static CastIntent intent() {
        return new CastIntent(1, 0, CastInputKind.CAST, 0, null, null);
    }

    private static P7NetworkComposition composition(P7ConnectionEpochSnapshotSource source,
            P7PendingPermitOwner owner, P7ServerIntentDispatchPort dispatch) {
        return new P7NetworkComposition(source, owner, dispatch, new P7ClientMirrorDispatchPort() {
            public long captureDispatchGeneration(Connection connection, ICommonPacketListener listener) { return 0; }
            public void onIntentAcknowledgement(long generation, IntentAcknowledgement value) { throw new AssertionError(); }
            public void onPlayerManaSnapshot(long generation, PlayerManaSnapshot value) { throw new AssertionError(); }
            public void onSkillCooldownSnapshot(long generation, SkillCooldownSnapshot value) { throw new AssertionError(); }
        });
    }

    private static void fail(Throwable failure) {
        if (failure instanceof RuntimeException runtime) { throw runtime; }
        if (failure instanceof Error error) { throw error; }
    }

    private static final class Source implements P7ConnectionEpochSnapshotSource {
        private final Connection connection;
        private final P7SessionIdentity identity = new P7SessionIdentity(PLAYER, 1, 1);
        private boolean current = true;
        private Error recheckFailure;
        private int captures;
        private int rechecks;
        private Source(Connection connection) { this.connection = connection; }
        public CaptureResult captureAuthenticatedSession(UUID playerId, Connection connection) {
            assertEquals(PLAYER, playerId);
            assertSame(this.connection, connection);
            captures++;
            return CaptureResult.captured(identity);
        }
        public boolean isCurrentCapture(P7SessionIdentity identity, Connection connection) {
            assertSame(this.identity, identity);
            assertSame(this.connection, connection);
            rechecks++;
            if (recheckFailure != null) { throw recheckFailure; }
            return current;
        }
    }

    private static final class Context implements IPayloadContext {
        private final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        private final boolean inline;
        private final Throwable submissionFailure;
        private Runnable task;
        private int enqueueCalls;
        private Context(boolean inline, Throwable submissionFailure) {
            this.inline = inline;
            this.submissionFailure = submissionFailure;
        }
        public Connection connection() { return connection; }
        public ICommonPacketListener listener() { throw new AssertionError(); }
        public Player player() { throw new AssertionError(); }
        public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
        public void handle(CustomPacketPayload payload) { throw new AssertionError(); }
        public void finishCurrentTask(ConfigurationTask.Type type) { throw new AssertionError(); }
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            enqueueCalls++;
            this.task = task;
            if (inline) { task.run(); }
            fail(submissionFailure);
            return CompletableFuture.completedFuture(null);
        }
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) { throw new AssertionError(); }
    }
}
