package com.yo1no.gramarye.magic.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** Sole bounded owner of transient P7 server-session and admission-transition state. */
final class P7ServerSessionService {
    private final Object stateLock = new Object();
    private final Map<UUID, P7ServerSessionState> sessions = new HashMap<>();
    private final P7ServerAccess serverAccess;
    private final P7ReloadAdmissionGate reloadGate;
    private ConnectionEpochState connectionEpochState = ConnectionEpochState.initial();
    private IntentTickBudget globalBudget =
            IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
    private boolean stopping;

    P7ServerSessionService(
            P7ServerAccess serverAccess, P7ReloadAdmissionGate reloadGate) {
        this.serverAccess = Objects.requireNonNull(serverAccess, "serverAccess");
        this.reloadGate = Objects.requireNonNull(reloadGate, "reloadGate");
    }

    OptionalLong openSession(MinecraftServer server, UUID authenticatedPlayerId) {
        var result = open(server, authenticatedPlayerId);
        return result == OpenResult.OPENED ? currentEpoch(authenticatedPlayerId) : OptionalLong.empty();
    }

    enum OpenResult { OPENED, ALREADY_ACTIVE, CAPACITY_REJECTED, EPOCH_EXHAUSTED, INTERNAL_FAULT }

    OpenResult open(MinecraftServer server, UUID authenticatedPlayerId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        requireServerThread(server);
        if (!serverAccess.running(server) || stopping) {
            return OpenResult.INTERNAL_FAULT;
        }
        var authoritativeTick = serverAccess.authoritativeTick(server);
        synchronized (stateLock) {
            if (sessions.containsKey(authenticatedPlayerId)) {
                return OpenResult.ALREADY_ACTIVE;
            }
            if (sessions.size() == P7NetworkBounds.MAX_ACTIVE_SESSIONS_PER_SERVER) {
                return OpenResult.CAPACITY_REJECTED;
            }
            var allocation = connectionEpochState.allocate();
            if (!allocation.accepted()) {
                return OpenResult.EPOCH_EXHAUSTED;
            }
            var epoch = allocation.allocatedEpoch().orElseThrow();
            var identity = new P7SessionIdentity(authenticatedPlayerId, epoch);
            sessions.put(
                    authenticatedPlayerId,
                    P7ServerSessionState.initial(identity, authoritativeTick));
            connectionEpochState = allocation.nextState();
            return OpenResult.OPENED;
        }
    }

    boolean closeSession(
            MinecraftServer server, UUID authenticatedPlayerId, long expectedEpoch) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        requireServerThread(server);
        synchronized (stateLock) {
            var current = sessions.get(authenticatedPlayerId);
            if (current == null
                    || current.identity().connectionEpoch() != expectedEpoch) {
                return false;
            }
            sessions.remove(authenticatedPlayerId);
            return true;
        }
    }

    OptionalLong currentEpoch(UUID authenticatedPlayerId) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        synchronized (stateLock) {
            var current = sessions.get(authenticatedPlayerId);
            return current == null
                    ? OptionalLong.empty()
                    : OptionalLong.of(current.identity().connectionEpoch());
        }
    }

    Optional<P7ServerSessionState> currentSession(P7SessionIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        synchronized (stateLock) {
            var current = sessions.get(identity.authenticatedPlayerId());
            return current != null && current.identity().equals(identity)
                    ? Optional.of(current)
                    : Optional.empty();
        }
    }

    Optional<CastIntentAdmissionSemantics.Decision> transition(
            MinecraftServer server,
            P7SessionIdentity identity,
            long authoritativeTick,
            long receivedSequence) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(identity, "identity");
        requireServerThread(server);
        synchronized (stateLock) {
            var current = sessions.get(identity.authenticatedPlayerId());
            if (stopping || current == null || !current.identity().equals(identity)) {
                return Optional.empty();
            }
            var decision = CastIntentAdmissionSemantics.evaluate(
                    current.admissionState(),
                    globalBudget,
                    authoritativeTick,
                    receivedSequence);
            var nextSession = current.withAdmissionState(decision.nextSessionState());
            sessions.put(identity.authenticatedPlayerId(), nextSession);
            globalBudget = decision.nextGlobalBudget();
            return Optional.of(decision);
        }
    }

    boolean invalidateAfterRateLimit(MinecraftServer server, P7SessionIdentity identity) {
        return closeSession(
                server,
                identity.authenticatedPlayerId(),
                identity.connectionEpoch());
    }

    boolean admissionOpen(MinecraftServer server) {
        requireServerThread(server);
        return !stopping && reloadGate.isOpen(server);
    }

    boolean consumeSyncWork(MinecraftServer server, long tick) {
        requireServerThread(server);
        synchronized (stateLock) {
            if (stopping) {
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
            var current = sessions.get(identity.authenticatedPlayerId());
            if (current == null || !current.identity().equals(identity)) {
                throw new P7SemanticInvariantException("sync session is no longer current");
            }
            sessions.put(identity.authenticatedPlayerId(), current.withSyncState(next));
        }
    }

    List<UUID> activePlayerIds(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            return sessions.keySet().stream().sorted().toList();
        }
    }

    int stop(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            stopping = true;
            var count = sessions.size();
            sessions.clear();
            return count;
        }
    }

    void start(MinecraftServer server) {
        requireServerThread(server);
        synchronized (stateLock) {
            if (!sessions.isEmpty()) {
                throw new P7SemanticInvariantException("live sessions survived server stop");
            }
            connectionEpochState = ConnectionEpochState.initial();
            globalBudget = IntentTickBudget.initial(IntentTickBudget.Kind.GLOBAL_WORK, 0L);
            stopping = false;
        }
    }

    int activeSessionCount() {
        synchronized (stateLock) {
            return sessions.size();
        }
    }

    private void requireServerThread(MinecraftServer server) {
        if (!serverAccess.sameThread(server)) {
            throw new P7SemanticInvariantException(
                    "session mutation requires the server thread");
        }
    }
}
