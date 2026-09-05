package com.yo1no.gramarye.magic.network;

import java.util.List;
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

    enum Submission { SUBMITTED }

    private final P7ServerSessionService sessions;
    private final P7ServerAccess access;
    private final P7ServerLifecycleCoordinator lifecycle;
    private final ManaObservation manaObservation;
    private final Transport transport;

    P7AuthoritativeSyncService(P7ServerSessionService sessions, P7ServerAccess access,
            P7ServerLifecycleCoordinator lifecycle, ManaObservation manaObservation) {
        this(sessions, access, lifecycle, manaObservation,
                (actor, payload) -> actor.connection.send(payload));
    }

    P7AuthoritativeSyncService(P7ServerSessionService sessions, P7ServerAccess access,
            P7ServerLifecycleCoordinator lifecycle, ManaObservation manaObservation, Transport transport) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.access = Objects.requireNonNull(access, "access");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.manaObservation = Objects.requireNonNull(manaObservation, "manaObservation");
        this.transport = Objects.requireNonNull(transport, "transport");
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
        if (actor == null) {
            return true;
        }
        var state = sessions.currentSession(identity).orElseThrow().syncState();
        if (state.mana().exhausted() || state.cooldown().exhausted()) {
            lifecycle.terminate(server, actor, identity);
            return true;
        }
        if (!state.due(tick) || !sessions.consumeSyncWork(server, tick)) {
            return false;
        }
        // Both immutable full values are validated before either submission.
        var balance = manaObservation.observe(actor);
        if (balance < -1 || balance > 1_000_000_000L) {
            throw new P7SemanticInvariantException("invalid mana observation");
        }
        var mana = new PlayerManaSyncPayload(new PlayerManaSnapshot(state.mana().value(),
                balance == -1 ? PlayerManaSnapshot.Availability.UNAVAILABLE : PlayerManaSnapshot.Availability.AVAILABLE,
                balance == -1 ? 0 : balance));
        var cooldown = new SkillCooldownSyncPayload(new SkillCooldownSnapshot(state.cooldown().value(), List.of()));
        submit(server, actor, identity, mana);
        state = state.manaSubmitted();
        sessions.updateSync(server, identity, state);
        submit(server, actor, identity, cooldown);
        state = state.cooldownSubmitted(tick);
        sessions.updateSync(server, identity, state);
        if (state.mana().exhausted() || state.cooldown().exhausted()) {
            lifecycle.terminate(server, actor, identity);
        }
        return true;
    }

    private Submission submit(MinecraftServer server, ServerPlayer actor,
            P7SessionIdentity identity, CustomPacketPayload payload) {
        try {
            transport.submit(actor, payload);
            // SUBMITTED_TO_CURRENT_CONNECTION is not remote delivery or application.
            return Submission.SUBMITTED;
        } catch (RuntimeException | Error primary) {
            lifecycle.submissionFailed(server, actor, identity, primary);
            throw primary;
        }
    }

    private ServerPlayer currentActor(MinecraftServer server, P7SessionIdentity identity) {
        if (sessions.currentSession(identity).isEmpty()) {
            return null;
        }
        var actor = access.currentPlayer(server, identity.authenticatedPlayerId());
        return actor != null && access.currentConnectedPlayer(server, actor, identity.authenticatedPlayerId())
                ? actor : null;
    }

    private void requireServerThread(MinecraftServer server) {
        if (!access.sameThread(server)) {
            throw new P7SemanticInvariantException("submission requires the server thread");
        }
    }
}
