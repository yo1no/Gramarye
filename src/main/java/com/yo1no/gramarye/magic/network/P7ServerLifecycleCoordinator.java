package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sole server-thread sync/lifecycle owner; retained work consists only of scalar identities. */
final class P7ServerLifecycleCoordinator {
    private final P7ServerSessionService sessions;
    private final P7ServerAccess access;
    private final P7PendingPermitOwner permits;
    private final P7ReloadAdmissionGate reloadGate;
    private final P7Diagnostics diagnostics;
    private final Set<P7SessionIdentity> reconciliation = new LinkedHashSet<>();
    private final P7AuthoritativeSyncService sync;
    private long drainTick = -1;
    private int processedThisTick;
    private boolean stopped = true;

    P7ServerLifecycleCoordinator(P7ServerSessionService sessions, P7ServerAccess access,
            P7PendingPermitOwner permits, P7ReloadAdmissionGate reloadGate,
            P7Diagnostics diagnostics, P7AuthoritativeSyncService.ManaObservation manaObservation) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = Objects.requireNonNull(access, "access");
        this.permits = Objects.requireNonNull(permits, "permits");
        this.reloadGate = Objects.requireNonNull(reloadGate, "reloadGate");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.sync = new P7AuthoritativeSyncService(sessions, access, this, manaObservation);
    }

    void accept(P7ServerIntentResult result) {
        sync.accept(result);
    }

    void onLoginReady(MinecraftServer server, ServerPlayer actor) {
        requireServerThread(server);
        Objects.requireNonNull(actor, "actor");
        if (stopped || !sessions.isCurrentServer(server)
                || !access.currentConnectedPlayer(server, actor, actor.getUUID())) {
            throw new P7SemanticInvariantException("login actor is not current");
        }
        switch (sessions.open(server, actor)) {
            case OPENED -> requestSync(server, actor.getUUID());
            case ALREADY_ACTIVE -> { }
            case CAPACITY_REJECTED, EPOCH_EXHAUSTED -> {
                observe(server, actor.getUUID(), P7IntentFailureReason.SERVER_BUSY);
                access.disconnectCurrent(server, actor);
            }
            case INTERNAL_FAULT -> throw new P7SemanticInvariantException("P7 login unavailable");
        }
    }

    void onLoginReady(MinecraftServer server, ServerPlayer actor, MetadataContinuation receipt,
            P7ServerAuthorizationBoundary.LoginReadyPort port) {
        requireServerThread(server);
        Objects.requireNonNull(actor, "actor");
        if (stopped || !sessions.isCurrentServer(server)
                || !access.currentConnectedPlayer(server, actor, actor.getUUID())
                || !receipt.loginActor(port, actor)) {
            throw new P7SemanticInvariantException("login actor is not current");
        }
        long opened = receipt.openedSession(port);
        if (opened > 0) {
            // A known opened session is resumed, never re-opened or inferred by UUID alone.
            var identity = new P7SessionIdentity(actor.getUUID(), opened,
                    receipt.openedServerGeneration(port));
            if (!sessions.matchesCurrentActor(server, identity, actor)) {
                throw new P7SemanticInvariantException("metadata session is no longer current");
            }
            requestSync(server, actor.getUUID());
            return;
        }
        if (sessions.currentIdentity(server, actor.getUUID()).isPresent()) {
            throw new P7SemanticInvariantException("unproven existing metadata session");
        }
        receipt.sessionStarted(port);
        switch (sessions.open(server, actor)) {
            case OPENED -> {
                var identity = sessions.currentIdentity(server, actor.getUUID()).orElseThrow();
                receipt.sessionOpened(port, identity.connectionEpoch(), identity.serverGeneration());
                requestSync(server, actor.getUUID());
            }
            case CAPACITY_REJECTED, EPOCH_EXHAUSTED -> {
                observe(server, actor.getUUID(), P7IntentFailureReason.SERVER_BUSY);
                access.disconnectCurrent(server, actor);
            }
            case ALREADY_ACTIVE, INTERNAL_FAULT ->
                    throw new P7SemanticInvariantException("P7 metadata login unavailable");
        }
    }

    void onDisconnect(MinecraftServer server, ServerPlayer actor) {
        requireServerThread(server);
        sessions.closeForActor(server, actor).ifPresent(this::clearOwnedState);
    }

    void requestSync(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        if (!stopped && sessions.isCurrentServer(server)) {
            var identity = sessions.currentIdentity(server, playerId).orElse(null);
            if (identity == null) { return; }
            if (!reconciliation.contains(identity)
                    && reconciliation.size() == P7NetworkBounds.MAX_RELOAD_RECONCILIATION_QUEUE) {
                throw new P7SemanticInvariantException("reconciliation capacity exceeded");
            }
            reconciliation.add(identity);
        }
    }

    void onReloadComplete(MinecraftServer server) {
        requireServerThread(server);
        if (stopped || !sessions.isCurrentServer(server)) {
            return;
        }
        if (!reloadGate.beginReconciliation(server)) {
            observe(server, null, P7IntentFailureReason.RELOAD_IN_PROGRESS);
        }
        for (var playerId : sessions.activePlayerIds(server)) {
            requestSync(server, playerId);
        }
    }

    void tick(MinecraftServer server) {
        requireServerThread(server);
        if (stopped || !sessions.isCurrentServer(server)) {
            return;
        }
        var tick = access.authoritativeTick(server);
        if (tick < 0 || tick < drainTick) {
            throw new P7SemanticInvariantException("lifecycle tick regressed");
        }
        if (tick != drainTick) {
            drainTick = tick;
            processedThisTick = 0;
        }
        for (var playerId : sessions.activePlayerIds(server)) {
            var identity = sessions.currentIdentity(server, playerId).orElseThrow();
            if (sessions.currentSession(identity).orElseThrow().syncState().due(tick)) {
                requestSync(server, playerId);
            }
        }
        // Snapshot only bounded scalar IDs: terminal cleanup may remove from the sole set.
        for (var identity : java.util.List.copyOf(reconciliation)) {
            if (!sessions.isCurrentServer(server)) { return; }
            if (processedThisTick == P7NetworkBounds.MAX_RELOAD_RECONCILIATION_PER_TICK) {
                break;
            }
            processedThisTick++;
            if (sessions.currentSession(identity).isEmpty()) {
                reconciliation.remove(identity);
                continue;
            }
            if (sync.fullSync(server, identity, tick)) {
                reconciliation.remove(identity);
            }
        }
        if (reconciliation.isEmpty() && sessions.isCurrentServer(server)) {
            reloadGate.open(server);
        }
    }

    void terminate(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity) {
        requireServerThread(server);
        finishInvalidated(server, actor, identity);
    }

    void finishInvalidated(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity) {
        requireServerThread(server);
        var connection = ownedActorConnection(server, actor, identity);
        if (connection == null || !sessions.closeSession(server, identity)) { return; }
        clearOwnedState(identity);
        disconnectExact(server, actor, identity, connection);
    }

    void submissionFailed(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, Throwable primary) {
        // Each stage runs once even when an earlier cleanup stage fails. No Throwable is retained.
        Connection connection = null;
        try {
            connection = ownedActorConnection(server, actor, identity);
            if (connection != null) { sessions.closeSession(server, identity); }
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            clearQueue(identity);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            permits.invalidateSession(identity);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            disconnectExact(server, actor, identity, connection);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            if (sessions.isCurrentServer(server, identity.serverGeneration())) {
                observe(server, identity.authenticatedPlayerId(), P7IntentFailureReason.INTERNAL_SERVER_FAULT);
            }
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
    }

    void observe(MinecraftServer server, UUID playerId, P7IntentFailureReason reason) {
        requireServerThread(server);
        if (sessions.isCurrentServer(server)) {
            diagnostics.record(playerId, access.authoritativeTick(server), reason);
        }
    }

    int stop(MinecraftServer server) {
        requireServerThread(server);
        if (stopped || !sessions.isCurrentServer(server)) {
            return 0;
        }
        stopped = true;
        Throwable primary = null;
        try {
            var count = sessions.stop(server) + reconciliation.size();
            if (count > P7NetworkBounds.MAX_SERVER_STOP_CLEANUP_RECORDS) {
                throw new P7SemanticInvariantException("server cleanup bound exceeded");
            }
            return count;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            discardStoppedState(server, primary);
        }
    }

    void start(MinecraftServer server) {
        requireServerThread(server);
        if (sessions.isCurrentServer(server)) { return; }
        if (!reconciliation.isEmpty()) {
            throw new P7SemanticInvariantException("reconciliation survived stop");
        }
        sessions.start(server);
        try {
            // The new slot is empty: no authenticated capture exists until an actual open.
            reloadGate.reset(server);
            diagnostics.discard();
            drainTick = -1;
            processedThisTick = 0;
            stopped = false;
        } catch (RuntimeException | Error primary) {
            stopped = true;
            try {
                sessions.stop(server);
            } catch (RuntimeException | Error secondary) {
                suppress(primary, secondary);
            }
            discardStoppedState(server, primary);
            throw primary;
        }
    }

    int queuedCount() {
        return reconciliation.size();
    }

    private void clearOwnedState(P7SessionIdentity identity) {
        clearQueue(identity);
        permits.invalidateSession(identity);
    }

    private void clearQueue(P7SessionIdentity identity) {
        reconciliation.remove(identity);
    }

    private Connection ownedActorConnection(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity) {
        if (!sessions.isCurrentServer(server, identity.serverGeneration())
                || !identity.authenticatedPlayerId().equals(actor.getUUID())) { return null; }
        var connection = access.actorConnection(server, actor);
        var current = access.currentPlayer(server, identity.authenticatedPlayerId());
        return connection != null && (current == null || current == actor)
                && sessions.isCurrentCapture(identity, connection) ? connection : null;
    }

    private void disconnectExact(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity,
            Connection connection) {
        if (connection != null && sessions.isCurrentServer(server, identity.serverGeneration())
                && sessions.currentIdentity(server, identity.authenticatedPlayerId()).isEmpty()
                && access.actorConnection(server, actor) == connection) {
            access.disconnectCurrent(server, actor);
        }
    }

    private void discardStoppedState(MinecraftServer server, Throwable primary) {
        Throwable failure = primary;
        try {
            reloadGate.close(server);
        } catch (RuntimeException | Error secondary) {
            if (failure == null) { failure = secondary; } else { suppress(failure, secondary); }
        }
        try {
            reconciliation.clear();
        } catch (RuntimeException | Error secondary) {
            if (failure == null) { failure = secondary; } else { suppress(failure, secondary); }
        }
        try {
            diagnostics.discard();
        } catch (RuntimeException | Error secondary) {
            if (failure == null) { failure = secondary; } else { suppress(failure, secondary); }
        }
        if (primary == null) {
            if (failure instanceof RuntimeException runtime) { throw runtime; }
            if (failure instanceof Error error) { throw error; }
        }
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            try {
                primary.addSuppressed(secondary);
            } catch (RuntimeException | Error suppressionFailure) {
                // Suppression is best effort; even its own failure cannot replace primary.
            }
        }
    }

    private void requireServerThread(MinecraftServer server) {
        if (!access.sameThread(server)) {
            throw new P7SemanticInvariantException("lifecycle requires the server thread");
        }
    }
}
