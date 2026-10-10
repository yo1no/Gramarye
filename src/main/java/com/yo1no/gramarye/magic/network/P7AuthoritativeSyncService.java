package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import java.util.Objects;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** At-most-once local submission, with no delivery tracking, fallback, or retry. */
final class P7AuthoritativeSyncService {
    @FunctionalInterface
    interface ManaObservation {
        long observe(ServerPlayer actor);
    }

    @FunctionalInterface
    interface Transport {
        void submit(ServerPlayer actor, CustomPacketPayload payload);
    }

    enum Submission { SUBMITTED, NOT_CURRENT }

    private final P7ServerSessionService sessions;
    private final P7ServerAccess access;
    private final P7ServerLifecycleCoordinator lifecycle;
    private final ManaObservation manaObservation;
    private final Transport transport;
    private final P7ServerAuthorizationBoundary.SyncProjectionPort projections;

    P7AuthoritativeSyncService(P7ServerSessionService sessions, P7ServerAccess access,
            P7ServerLifecycleCoordinator lifecycle, ManaObservation manaObservation) {
        this(sessions, access, lifecycle, manaObservation,
                (actor, payload) -> actor.connection.send(payload));
    }

    P7AuthoritativeSyncService(P7ServerSessionService sessions, P7ServerAccess access,
            P7ServerLifecycleCoordinator lifecycle, ManaObservation manaObservation, Transport transport) {
        this(sessions, access, lifecycle, manaObservation, transport, P7ServerAuthorizationBoundary::prepareSync);
    }

    P7AuthoritativeSyncService(P7ServerSessionService sessions, P7ServerAccess access,
            P7ServerLifecycleCoordinator lifecycle, ManaObservation manaObservation, Transport transport,
            P7ServerAuthorizationBoundary.SyncProjectionPort projections) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = Objects.requireNonNull(access, "access");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.manaObservation = Objects.requireNonNull(manaObservation, "manaObservation");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.projections = Objects.requireNonNull(projections, "projections");
    }

    void accept(P7ServerIntentResult result) {
        Objects.requireNonNull(result, "result");
        var server = access.currentServer();
        if (server == null) {
            return;
        }
        requireServerThread(server);
        var identity = result.sessionIdentity();
        var actor = currentActor(server, identity);
        if (actor == null) {
            return;
        }
        result.failureReason().ifPresent(reason -> lifecycle.observe(server, identity.authenticatedPlayerId(), reason));
        if (result.acknowledgementCandidate().isPresent()) {
            submit(server, actor, identity,
                    new IntentAckPayload(result.acknowledgementCandidate().orElseThrow()));
        }
    }

    boolean fullSync(MinecraftServer server, P7SessionIdentity identity, long tick) {
        requireServerThread(server);
        var actor = currentActor(server, identity);
        if (actor == null) { return true; }
        var state = sessions.currentSession(identity).orElseThrow().syncState();
        if (state.sending()) { return false; }
        if ((state.mana().exhausted() && !state.manaSubmittedInCycle()) || state.cooldown().exhausted()) {
            lifecycle.terminate(server, actor, identity);
            return true;
        }
        if (!state.due(tick) || !sessions.consumeSyncWork(server, tick)) { return false; }
        var entered = state.sending(true);
        sessions.updateSync(server, identity, state, entered);
        var connection = actor.connection;
        Throwable primary = null;
        try {
            state = entered;
            boolean needsMana = !state.manaSubmittedInCycle();
            PlayerManaSyncPayload mana = null;
            if (needsMana) {
                var observation = state.initialPending()
                        ? P11NativeStorageBoundary.beginMetadataManaObservation(actor,
                                identity.connectionEpoch(), identity.serverGeneration()) : null;
                boolean normal = false;
                try {
                    var balance = manaObservation.observe(actor);
                    if (balance < -1 || balance > 1_000_000_000L) {
                        throw new P7SemanticInvariantException("invalid mana observation");
                    }
                    mana = new PlayerManaSyncPayload(new PlayerManaSnapshot(state.mana().value(),
                            balance == -1 ? PlayerManaSnapshot.Availability.UNAVAILABLE : PlayerManaSnapshot.Availability.AVAILABLE,
                            balance == -1 ? 0 : balance));
                    normal = true;
                } finally {
                    P11NativeStorageBoundary.endMetadataManaObservation(observation, normal);
                }
            }
            // Preparation may initialize/prune. Its final immutable capture includes the
            // original mana publication; neither projection getters nor validation invoke native callbacks.
            if (currentActor(server, identity) != actor || actor.connection != connection) { return false; }
            var capture = Objects.requireNonNull(projections.prepareAndCapture(server, actor), "sync capture");
            var cooldown = new SkillCooldownSyncPayload(
                    Objects.requireNonNull(capture.projection(), "sync projection").snapshot(state.cooldown().value()));
            // Both complete immutable payloads are validated before either family is submitted.
            if (!captureCurrent(server, actor, identity, state, connection, capture)) { return false; }
            if (needsMana) {
                if (!submitInitialFamily(server, actor, identity, mana, state.initialPending(), true)) {
                    return false;
                }
                state = commitFamily(server, actor, identity, state, true, tick);
            }
            if (!captureCurrent(server, actor, identity, state, connection, capture)) { return false; }
            if (!submitInitialFamily(server, actor, identity, cooldown, state.initialPending(), false)) {
                return false;
            }
            state = commitFamily(server, actor, identity, state, false, tick);
            if (state.mana().exhausted() || state.cooldown().exhausted()) {
                lifecycle.terminate(server, actor, identity);
                return true;
            }
            return captureCurrent(server, actor, identity, state, connection, capture);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            // Unknown preparation/observation/submission never becomes a retryable fresh capture.
            lifecycle.submissionFailed(server, actor, identity, failure);
            throw failure;
        } finally {
            finishAttempt(server, actor, identity, primary);
        }
    }

    private boolean captureCurrent(MinecraftServer server, ServerPlayer actor, P7SessionIdentity identity,
            P7ServerSyncState state, Object connection, P7ServerAuthorizationBoundary.SyncCapture capture) {
        return currentActor(server, identity) == actor && actor.connection == connection
                && sessions.currentSession(identity).orElseThrow().syncState() == state
                && capture.isCurrent()
                && currentActor(server, identity) == actor && actor.connection == connection
                && sessions.currentSession(identity).orElseThrow().syncState() == state;
    }

    private void finishAttempt(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, Throwable primary) {
        try {
            if (!sessions.isCurrentServer(server, identity.serverGeneration())) { return; }
            var current = sessions.currentSession(identity);
            if (current.isPresent() && current.orElseThrow().syncState().sending()) {
                var state = current.orElseThrow().syncState();
                sessions.updateSync(server, identity, state, state.sending(false));
            }
        } catch (RuntimeException | Error cleanupFailure) {
            lifecycle.submissionFailed(server, actor, identity, primary == null ? cleanupFailure : primary);
            if (primary == null) { throw cleanupFailure; }
        }
    }

    private boolean submitInitialFamily(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, CustomPacketPayload payload, boolean initial, boolean mana) {
        if (currentActor(server, identity) != actor) { return false; }
        if (initial) {
            P11NativeStorageBoundary.metadataInitialSync(actor, identity.connectionEpoch(), identity.serverGeneration(),
                    mana ? MetadataInitialStage.MANA_STARTED : MetadataInitialStage.COOLDOWN_STARTED);
        }
        try {
            if (submit(server, actor, identity, payload) != Submission.SUBMITTED) { return false; }
        } catch (RuntimeException | Error primary) {
            if (initial) {
                P11NativeStorageBoundary.metadataInitialSync(actor, identity.connectionEpoch(), identity.serverGeneration(),
                        mana ? MetadataInitialStage.MANA_FAILED : MetadataInitialStage.COOLDOWN_FAILED);
            }
            throw primary;
        }
        if (initial) {
            P11NativeStorageBoundary.metadataInitialSync(actor, identity.connectionEpoch(), identity.serverGeneration(),
                    mana ? MetadataInitialStage.MANA_SUBMITTED : MetadataInitialStage.COOLDOWN_SUBMITTED);
        }
        return true;
    }

    private P7ServerSyncState commitFamily(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, P7ServerSyncState state, boolean mana, long tick) {
        try {
            var next = mana ? state.manaSubmitted() : state.cooldownSubmitted(tick);
            sessions.updateSync(server, identity, state, next);
            return next;
        } catch (RuntimeException | Error primary) {
            // A submitted packet cannot be replayed because bookkeeping threw afterwards.
            lifecycle.submissionFailed(server, actor, identity, primary);
            throw primary;
        }
    }

    private Submission submit(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, CustomPacketPayload payload) {
        try {
            if (currentActor(server, identity) != actor) { return Submission.NOT_CURRENT; }
            transport.submit(actor, payload);
            // SUBMITTED_TO_CURRENT_CONNECTION is not remote delivery or application.
            return Submission.SUBMITTED;
        } catch (RuntimeException | Error primary) {
            lifecycle.submissionFailed(server, actor, identity, primary);
            throw primary;
        }
    }

    private ServerPlayer currentActor(MinecraftServer server, P7SessionIdentity identity) {
        if (!sessions.isCurrentServer(server, identity.serverGeneration())
                || sessions.currentSession(identity).isEmpty()) {
            return null;
        }
        var actor = access.currentPlayer(server, identity.authenticatedPlayerId());
        return actor != null && sessions.matchesCurrentActor(server, identity, actor)
                ? actor : null;
    }

    private void requireServerThread(MinecraftServer server) {
        if (!access.sameThread(server)) {
            throw new P7SemanticInvariantException("submission requires the server thread");
        }
    }
}
