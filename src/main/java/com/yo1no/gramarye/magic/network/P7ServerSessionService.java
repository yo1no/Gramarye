package com.yo1no.gramarye.magic.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Sole bounded owner of transient P7 sessions and their authenticated native bindings. */
final class P7ServerSessionService {
    private final Object stateLock = new Object();
    private final Map<UUID, BoundSession> sessions = new HashMap<>();
    private final P7ServerAccess serverAccess;
    private final P7ReloadAdmissionGate reloadGate;
    private final P7PendingPermitOwner permits;
    private ConnectionEpochState connectionEpochState = ConnectionEpochState.initial();
    private IntentTickBudget globalBudget =
            IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
    private MinecraftServer activeServer;
    // Snapshot of the sole permit owner's generation, never independently incremented.
    private long publishedGeneration;
    private boolean stopping = true;
    private boolean failedStop;

    private record BoundSession(P7ServerSessionState state, Connection connection) {
        private BoundSession {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(connection, "connection");
        }

        private BoundSession withState(P7ServerSessionState next) {
            return new BoundSession(next, connection);
        }
    }

    P7ServerSessionService(P7ServerAccess serverAccess, P7ReloadAdmissionGate reloadGate,
            P7PendingPermitOwner permits) {
        this.serverAccess = Objects.requireNonNull(serverAccess, "serverAccess");
        this.reloadGate = Objects.requireNonNull(reloadGate, "reloadGate");
        this.permits = Objects.requireNonNull(permits, "permits");
    }

    enum OpenResult { OPENED, ALREADY_ACTIVE, CAPACITY_REJECTED, EPOCH_EXHAUSTED, INTERNAL_FAULT }

    OpenResult open(MinecraftServer server, ServerPlayer actor) {
        requireServerThread(server);
        Objects.requireNonNull(actor, "actor");
        var playerId = actor.getUUID();
        var connection = serverAccess.actorConnection(server, actor);
        if (connection == null || !serverAccess.running(server)
                || !serverAccess.currentConnectedPlayer(server, actor, playerId)) {
            return OpenResult.INTERNAL_FAULT;
        }
        var tick = serverAccess.authoritativeTick(server);
        synchronized (stateLock) {
            if (!currentServerUnderLock(server)) {
                return OpenResult.INTERNAL_FAULT;
            }
            var existing = sessions.get(playerId);
            if (existing != null) {
                return existing.connection() == connection
                        ? OpenResult.ALREADY_ACTIVE : OpenResult.INTERNAL_FAULT;
            }
            if (sessions.size() == P7NetworkBounds.MAX_ACTIVE_SESSIONS_PER_SERVER) {
                return OpenResult.CAPACITY_REJECTED;
            }
            var allocation = connectionEpochState.allocate();
            if (!allocation.accepted()) {
                return OpenResult.EPOCH_EXHAUSTED;
            }
            var identity = new P7SessionIdentity(
                    playerId, allocation.allocatedEpoch().orElseThrow(), publishedGeneration);
            // Consume before publication: a failed map write cannot reuse this identity.
            connectionEpochState = allocation.nextState();
            sessions.put(playerId, new BoundSession(P7ServerSessionState.initial(identity, tick), connection));
            return OpenResult.OPENED;
        }
    }

    P7ConnectionEpochSnapshotSource.CaptureResult captureAuthenticatedSession(
            UUID playerId, Connection connection) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(connection, "connection");
        synchronized (stateLock) {
            if (stopping || activeServer == null) {
                return P7ConnectionEpochSnapshotSource.CaptureResult.rejected(
                        P7ConnectionEpochSnapshotSource.CaptureOutcome.SERVER_UNAVAILABLE);
            }
            var current = sessions.get(playerId);
            if (current == null) {
                return P7ConnectionEpochSnapshotSource.CaptureResult.rejected(
                        P7ConnectionEpochSnapshotSource.CaptureOutcome.NO_SESSION);
            }
            if (current.connection() != connection) {
                return P7ConnectionEpochSnapshotSource.CaptureResult.rejected(
                        P7ConnectionEpochSnapshotSource.CaptureOutcome.CONNECTION_MISMATCH);
            }
            if (current.state().identity().serverGeneration() != publishedGeneration) {
                throw new P7SemanticInvariantException("published session generation differs");
            }
            return P7ConnectionEpochSnapshotSource.CaptureResult.captured(current.state().identity());
        }
    }

    boolean isCurrentCapture(P7SessionIdentity identity, Connection connection) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(connection, "connection");
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            return current != null && current.connection() == connection;
        }
    }

    Optional<P7SessionIdentity> currentIdentity(MinecraftServer server, UUID playerId) {
        requireServerThread(server);
        Objects.requireNonNull(playerId, "playerId");
        synchronized (stateLock) {
            var current = sessions.get(playerId);
            return currentServerUnderLock(server) && current != null
                    ? Optional.of(current.state().identity()) : Optional.empty();
        }
    }

    boolean matchesCurrentActor(MinecraftServer server, P7SessionIdentity identity, ServerPlayer actor) {
        requireServerThread(server);
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(actor, "actor");
        var connection = serverAccess.actorConnection(server, actor);
        if (connection == null || !serverAccess.running(server)
                || !serverAccess.currentConnectedPlayer(server, actor, identity.authenticatedPlayerId())) {
            return false;
        }
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            return currentServerUnderLock(server) && current != null && current.connection() == connection;
        }
    }

    Optional<P7SessionIdentity> closeForActor(MinecraftServer server, ServerPlayer actor) {
        requireServerThread(server);
        Objects.requireNonNull(actor, "actor");
        var playerId = actor.getUUID();
        // Logout may have closed C; native listener identity, not connectedness, applies.
        var connection = serverAccess.actorConnection(server, actor);
        var rosterActor = serverAccess.currentPlayer(server, playerId);
        synchronized (stateLock) {
            var current = sessions.get(playerId);
            if (!currentServerUnderLock(server) || connection == null || current == null
                    || current.connection() != connection) {
                return Optional.empty();
            }
            if (rosterActor != null && rosterActor != actor) {
                return Optional.empty();
            }
            sessions.remove(playerId);
            return Optional.of(current.state().identity());
        }
    }

    boolean closeSession(MinecraftServer server, P7SessionIdentity identity) {
        requireServerThread(server);
        Objects.requireNonNull(identity, "identity");
        synchronized (stateLock) {
            if (!currentServerUnderLock(server) || currentUnderLock(identity) == null) {
                return false;
            }
            sessions.remove(identity.authenticatedPlayerId());
            return true;
        }
    }

    Optional<P7ServerSessionState> currentSession(P7SessionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            return current == null ? Optional.empty() : Optional.of(current.state());
        }
    }

    Optional<CastIntentAdmissionSemantics.Decision> transition(MinecraftServer server,
            P7SessionIdentity identity, long authoritativeTick, long receivedSequence) {
        requireServerThread(server);
        Objects.requireNonNull(identity, "identity");
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            if (!currentServerUnderLock(server) || current == null) {
                return Optional.empty();
            }
            var decision = CastIntentAdmissionSemantics.evaluate(
                    current.state().admissionState(), globalBudget, authoritativeTick, receivedSequence);
            sessions.put(identity.authenticatedPlayerId(),
                    current.withState(current.state().withAdmissionState(decision.nextSessionState())));
            globalBudget = decision.nextGlobalBudget();
            return Optional.of(decision);
        }
    }

    boolean admissionOpen(MinecraftServer server) {
        return isCurrentServer(server) && reloadGate.isOpen(server);
    }

    boolean consumeSyncWork(MinecraftServer server, long tick) {
        requireServerThread(server);
        synchronized (stateLock) {
            if (!currentServerUnderLock(server)) {
                return false;
            }
            var decision = globalBudget.consume(tick);
            globalBudget = decision.nextState();
            return switch (decision.outcome()) {
                case ADMITTED -> true;
                case DENIED -> false;
                case INTERNAL_SERVER_FAULT -> throw new P7SemanticInvariantException("global work tick regressed");
            };
        }
    }

    void updateSync(MinecraftServer server, P7SessionIdentity identity, P7ServerSyncState next) {
        requireServerThread(server);
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            if (!currentServerUnderLock(server) || current == null) {
                throw new P7SemanticInvariantException("sync session is no longer current");
            }
            sessions.put(identity.authenticatedPlayerId(), current.withState(current.state().withSyncState(next)));
        }
    }

    void updateSync(MinecraftServer server, P7SessionIdentity identity,
            P7ServerSyncState expected, P7ServerSyncState next) {
        requireServerThread(server);
        synchronized (stateLock) {
            var current = currentUnderLock(identity);
            if (!currentServerUnderLock(server) || current == null || current.state().syncState() != expected) {
                throw new P7SemanticInvariantException("sync observation is no longer current");
            }
            sessions.put(identity.authenticatedPlayerId(), current.withState(current.state().withSyncState(next)));
        }
    }

    List<UUID> activePlayerIds(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            return currentServerUnderLock(server) ? sessions.keySet().stream().sorted().toList() : List.of();
        }
    }

    boolean isCurrentServer(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            return currentServerUnderLock(server);
        }
    }

    boolean isCurrentServer(MinecraftServer server, long expectedGeneration) {
        requireServerThread(server);
        synchronized (stateLock) {
            return currentServerUnderLock(server) && publishedGeneration == expectedGeneration;
        }
    }

    int stop(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            if (activeServer != server || stopping) {
                return 0;
            }
            stopping = true;
            var count = sessions.size();
            sessions.clear();
            // Sole nested order is stateLock -> permit monitor; neither invokes callbacks.
            failedStop = true;
            try {
                count += permits.stopAll();
                failedStop = false;
                return count;
            } finally {
                // Even a failed stop releases the native server graph, but cannot reopen.
                activeServer = null;
                publishedGeneration = 0;
            }
        }
    }

    void start(MinecraftServer server) {
        requireServerThread(server);
        if (!serverAccess.running(server)) {
            throw new P7SemanticInvariantException("P7 server is not running");
        }
        synchronized (stateLock) {
            if (activeServer != null || !stopping || failedStop || !sessions.isEmpty()) {
                throw new P7SemanticInvariantException("P7 server lifetime cannot restart");
            }
            var epochs = ConnectionEpochState.initial();
            var budget = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
            var generation = permits.startServer();
            connectionEpochState = epochs;
            globalBudget = budget;
            publishedGeneration = generation;
            activeServer = server;
            stopping = false;
        }
    }

    int activeSessionCount() {
        synchronized (stateLock) {
            return sessions.size();
        }
    }

    private BoundSession currentUnderLock(P7SessionIdentity identity) {
        var current = sessions.get(identity.authenticatedPlayerId());
        return !stopping && activeServer != null && identity.serverGeneration() == publishedGeneration
                && current != null && current.state().identity().equals(identity) ? current : null;
    }

    private boolean currentServerUnderLock(MinecraftServer server) {
        return !stopping && activeServer == server;
    }

    private void requireServerThread(MinecraftServer server) {
        if (!serverAccess.sameThread(Objects.requireNonNull(server, "server"))) {
            throw new P7SemanticInvariantException("session mutation requires the server thread");
        }
    }
}
