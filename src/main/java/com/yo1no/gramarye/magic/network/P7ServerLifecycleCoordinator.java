package com.yo1no.gramarye.magic.network;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sole server-thread sync/lifecycle owner; retained work consists only of UUIDs. */
final class P7ServerLifecycleCoordinator {
    private final P7ServerSessionService sessions;
    private final P7ServerAccess access;
    private final P7PendingPermitOwner permits;
    private final P7ReloadAdmissionGate reloadGate;
    private final P7Diagnostics diagnostics;
    private final Set<UUID> reconciliation = new LinkedHashSet<>();
    private final P7AuthoritativeSyncService sync;
    private long drainTick = -1;
    private int processedThisTick;
    private boolean stopped;

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
        if (stopped || !access.currentConnectedPlayer(server, actor, actor.getUUID())) {
            throw new P7SemanticInvariantException("login actor is not current");
        }
        switch (sessions.open(server, actor.getUUID())) {
            case OPENED -> requestSync(server, actor.getUUID());
            case ALREADY_ACTIVE -> { }
            case CAPACITY_REJECTED, EPOCH_EXHAUSTED -> {
                observe(server, actor.getUUID(), P7IntentFailureReason.SERVER_BUSY);
                access.disconnectCurrent(server, actor);
            }
            case INTERNAL_FAULT -> throw new P7SemanticInvariantException("P7 login unavailable");
        }
    }

    void onDisconnect(MinecraftServer server, ServerPlayer actor) {
        requireServerThread(server);
        var playerId = actor.getUUID();
        var current = access.currentPlayer(server, playerId);
        if (current != null && current != actor) {
            return;
        }
        var epoch = sessions.currentEpoch(playerId);
        if (epoch.isPresent()) {
            var identity = new P7SessionIdentity(playerId, epoch.getAsLong());
            sessions.closeSession(server, playerId, identity.connectionEpoch());
            clearOwnedState(identity);
        }
    }

    void requestSync(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        if (!stopped && sessions.currentEpoch(playerId).isPresent()) {
            if (!reconciliation.contains(playerId)
                    && reconciliation.size() == P7NetworkBounds.MAX_RELOAD_RECONCILIATION_QUEUE) {
                throw new P7SemanticInvariantException("reconciliation capacity exceeded");
            }
            reconciliation.add(playerId);
        }
    }

    void onReloadComplete(MinecraftServer server) {
        requireServerThread(server);
        if (stopped) {
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
        if (stopped) {
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
            var identity = new P7SessionIdentity(playerId, sessions.currentEpoch(playerId).orElseThrow());
            if (sessions.currentSession(identity).orElseThrow().syncState().due(tick)) {
                requestSync(server, playerId);
            }
        }
        // Snapshot only bounded scalar IDs: terminal cleanup may remove from the sole set.
        for (var playerId : java.util.List.copyOf(reconciliation)) {
            if (processedThisTick == P7NetworkBounds.MAX_RELOAD_RECONCILIATION_PER_TICK) {
                break;
            }
            processedThisTick++;
            var epoch = sessions.currentEpoch(playerId);
            if (epoch.isEmpty()) {
                reconciliation.remove(playerId);
                continue;
            }
            if (sync.fullSync(server, new P7SessionIdentity(playerId, epoch.getAsLong()), tick)) {
                reconciliation.remove(playerId);
            }
        }
        if (reconciliation.isEmpty()) {
            reloadGate.open(server);
        }
    }

    void terminate(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity) {
        requireServerThread(server);
        sessions.closeSession(server, identity.authenticatedPlayerId(), identity.connectionEpoch());
        finishInvalidated(server, actor, identity);
    }

    void finishInvalidated(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity) {
        requireServerThread(server);
        clearOwnedState(identity);
        disconnectExact(server, actor, identity);
    }

    void submissionFailed(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, Throwable primary) {
        // Each stage runs once even when an earlier cleanup stage fails. No Throwable is retained.
        try {
            sessions.closeSession(server, identity.authenticatedPlayerId(), identity.connectionEpoch());
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            clearQueue(identity);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            permits.invalidateSession(identity.authenticatedPlayerId(), identity.connectionEpoch());
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            disconnectExact(server, actor, identity);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
        try {
            observe(server, identity.authenticatedPlayerId(), P7IntentFailureReason.INTERNAL_SERVER_FAULT);
        } catch (RuntimeException | Error secondary) {
            suppress(primary, secondary);
        }
    }

    void observe(MinecraftServer server, UUID playerId, P7IntentFailureReason reason) {
        requireServerThread(server);
        diagnostics.record(playerId, access.authoritativeTick(server), reason);
    }

    int stop(MinecraftServer server) {
        requireServerThread(server);
        if (stopped) {
            return 0;
        }
        stopped = true;
        reloadGate.close(server);
        var count = sessions.stop(server);
        count += reconciliation.size();
        reconciliation.clear();
        count += permits.stopAll();
        diagnostics.discard();
        if (count > P7NetworkBounds.MAX_SERVER_STOP_CLEANUP_RECORDS) {
            throw new P7SemanticInvariantException("server cleanup bound exceeded");
        }
        return count;
    }

    void start(MinecraftServer server) {
        requireServerThread(server);
        if (!reconciliation.isEmpty()) {
            throw new P7SemanticInvariantException("reconciliation survived stop");
        }
        sessions.start(server);
        reloadGate.reset(server);
        diagnostics.discard();
        drainTick = -1;
        processedThisTick = 0;
        stopped = false;
    }

    int queuedCount() {
        return reconciliation.size();
    }

    private void clearOwnedState(P7SessionIdentity identity) {
        clearQueue(identity);
        permits.invalidateSession(identity.authenticatedPlayerId(), identity.connectionEpoch());
    }

    private void clearQueue(P7SessionIdentity identity) {
        var currentEpoch = sessions.currentEpoch(identity.authenticatedPlayerId());
        if (currentEpoch.isEmpty() || currentEpoch.getAsLong() == identity.connectionEpoch()) {
            reconciliation.remove(identity.authenticatedPlayerId());
        }
    }

    private void disconnectExact(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity) {
        var currentEpoch = sessions.currentEpoch(identity.authenticatedPlayerId());
        if (currentEpoch.isEmpty() || currentEpoch.getAsLong() == identity.connectionEpoch()) {
            access.disconnectCurrent(server, actor);
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
