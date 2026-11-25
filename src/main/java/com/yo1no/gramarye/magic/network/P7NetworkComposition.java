package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.Optional;
import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

final class P7NetworkComposition {
    private static P6RuntimeExecutionCapability manaCapability;
    private final P7ConnectionEpochSnapshotSource connectionEpochSource;
    private final P7PendingPermitOwner pendingPermitOwner;
    private final P7ServerIntentDispatchPort serverIntentDispatchPort;
    private final P7ClientMirrorDispatchPort clientMirrorDispatchPort;

    P7NetworkComposition(
            P7ConnectionEpochSnapshotSource connectionEpochSource,
            P7PendingPermitOwner pendingPermitOwner,
            P7ServerIntentDispatchPort serverIntentDispatchPort,
            P7ClientMirrorDispatchPort clientMirrorDispatchPort) {
        this.connectionEpochSource = Objects.requireNonNull(
                connectionEpochSource, "connectionEpochSource");
        this.pendingPermitOwner = Objects.requireNonNull(
                pendingPermitOwner, "pendingPermitOwner");
        this.serverIntentDispatchPort = Objects.requireNonNull(
                serverIntentDispatchPort, "serverIntentDispatchPort");
        this.clientMirrorDispatchPort = Objects.requireNonNull(
                clientMirrorDispatchPort, "clientMirrorDispatchPort");
    }

    static P7NetworkComposition production() {
        return ProductionHolder.INSTANCE;
    }

    // Called only by the existing write-once boundary installation, before publication.
    static void bindManaCapability(P6RuntimeExecutionCapability capability) {
        Objects.requireNonNull(capability, "capability");
        if (manaCapability != null) {
            throw new P7SemanticInvariantException("mana capability already bound");
        }
        manaCapability = capability;
    }

    static void onLoginReady(MinecraftServer server, ServerPlayer actor) {
        ProductionHolder.LIFECYCLE.onLoginReady(server, actor);
    }

    static void onLoginReady(MinecraftServer server, ServerPlayer actor,
            MetadataContinuation receipt, P7ServerAuthorizationBoundary.LoginReadyPort port) {
        ProductionHolder.LIFECYCLE.onLoginReady(server, actor, receipt, port);
    }

    static Optional<P7SessionIdentity> currentV0PlayIdentity(MinecraftServer server, ServerPlayer actor) {
        if (server == null || actor == null || !server.isSameThread() || !server.usesAuthentication()
                || actor.isRemoved() || !actor.isAlive() || actor.isFakePlayer()) {
            return Optional.empty();
        }
        var sessions = ProductionHolder.SESSION_SERVICE;
        if (!sessions.admissionOpen(server)) {
            return Optional.empty();
        }
        var connection = ProductionHolder.SERVER_ACCESS.actorConnection(server, actor);
        if (connection == null || connection.isMemoryConnection() || !connection.isConnected()) {
            return Optional.empty();
        }
        var identity = sessions.currentIdentity(server, actor.getUUID());
        if (identity.isEmpty() || !sessions.matchesCurrentActor(server, identity.orElseThrow(), actor)
                || !sessions.admissionOpen(server)) {
            return Optional.empty();
        }
        return identity;
    }

    static P7ServerLifecycleCoordinator lifecycle() {
        return ProductionHolder.LIFECYCLE;
    }

    static P7ReloadAdmissionGate reloadGate() {
        return ProductionHolder.RELOAD_GATE;
    }

    static void completeNormal(MinecraftServer exactServer, ServerPlayer exactActor,
            P7SessionIdentity identity, long sequence,
            P7ServerAuthorizationBoundary.AdmissionDisposition disposition,
            P7ServerAuthorizationBoundary.NormalFeedback feedback) {
        var access = ProductionHolder.SERVER_ACCESS;
        if (exactServer == null || exactActor == null || access.currentServer() != exactServer) { return; }
        if (!access.sameThread(exactServer)) {
            throw new P7SemanticInvariantException("normal completion requires the server thread");
        }
        var sessions = ProductionHolder.SESSION_SERVICE;
        if (!sessions.isCurrentServer(exactServer, identity.serverGeneration())
                || access.currentPlayer(exactServer, identity.authenticatedPlayerId()) != exactActor
                || !sessions.matchesCurrentActor(exactServer, identity, exactActor)) { return; }
        var result = P7AdmissionDispositionMapper.fromRootDisposition(identity, sequence, disposition);
        try {
            ProductionHolder.RESULT_SINK.accept(result);
        } catch (RuntimeException cosmeticDeliveryFailure) {
            // Only this already-decided ACK delivery is optional; never replay its gameplay.
        }
        if (disposition != P7ServerAuthorizationBoundary.AdmissionDisposition.ACCEPTED) {
            normalFeedback(exactActor, feedback == null
                    ? P7ServerAuthorizationBoundary.NormalFeedback.UNAVAILABLE : feedback);
        }
    }

    static void normalFeedback(ServerPlayer actor, P7ServerAuthorizationBoundary.NormalFeedback feedback) {
        String suffix = switch (Objects.requireNonNull(feedback, "feedback")) {
            case NOT_READY -> "not_ready";
            case CAPACITY, UNAVAILABLE -> "unavailable";
            case COOLDOWN_ACTIVE -> "cooldown_active";
            case INSUFFICIENT_MANA -> "insufficient_mana";
            case RATE_LIMITED -> "rate_limited";
        };
        try {
            actor.displayClientMessage(Component.translatable("message.gramarye.cast." + suffix), true);
        } catch (RuntimeException cosmeticFailure) {
            // This try covers only vanilla text delivery; it cannot undo gameplay or replay input.
        }
    }

    P7ConnectionEpochSnapshotSource connectionEpochSource() {
        return connectionEpochSource;
    }

    P7PendingPermitOwner pendingPermitOwner() {
        return pendingPermitOwner;
    }

    P7ServerIntentDispatchPort serverIntentDispatchPort() {
        return serverIntentDispatchPort;
    }

    P7ClientMirrorDispatchPort clientMirrorDispatchPort() {
        return clientMirrorDispatchPort;
    }

    private static final class ProductionHolder {
        private static final P7ServerAccess SERVER_ACCESS = new P7ServerAccess();
        private static final P7ReloadAdmissionGate RELOAD_GATE =
                new P7ReloadAdmissionGate();
        private static final P7PendingPermitOwner PERMITS = new P7PendingPermitOwner();
        private static final P7ServerSessionService SESSION_SERVICE =
                new P7ServerSessionService(SERVER_ACCESS, RELOAD_GATE, PERMITS);
        private static final P7AdvisoryTargetValidator TARGET_VALIDATOR =
                new P7AdvisoryTargetValidator();
        private static final P7ServerLifecycleCoordinator LIFECYCLE =
                new P7ServerLifecycleCoordinator(SESSION_SERVICE, SERVER_ACCESS, PERMITS,
                        RELOAD_GATE, new P7Diagnostics(),
                        actor -> P7ManaSnapshotBridge.observeBalance(manaCapability, actor));
        private static final P7ServerIntentResultSink RESULT_SINK = LIFECYCLE::accept;
        private static final P7ServerDisconnectPort DISCONNECT_PORT =
                LIFECYCLE::finishInvalidated;
        private static final P7ServerAuthorizationDispatcher SERVER_DISPATCHER =
                new P7ServerAuthorizationDispatcher(
                        SESSION_SERVICE,
                        SERVER_ACCESS,
                        TARGET_VALIDATOR,
                        RESULT_SINK,
                        DISCONNECT_PORT);
        private static final P7NetworkComposition INSTANCE = new P7NetworkComposition(
                new P7ConnectionEpochSnapshotSource() {
                    @Override
                    public CaptureResult captureAuthenticatedSession(
                            java.util.UUID playerId, net.minecraft.network.Connection connection) {
                        return SESSION_SERVICE.captureAuthenticatedSession(playerId, connection);
                    }

                    @Override
                    public boolean isCurrentCapture(P7SessionIdentity identity,
                            net.minecraft.network.Connection connection) {
                        return SESSION_SERVICE.isCurrentCapture(identity, connection);
                    }
                },
                PERMITS,
                SERVER_DISPATCHER::dispatch,
                P7ClientMirrorDispatchFactory.production());

        private ProductionHolder() {
            throw new AssertionError("no instances");
        }
    }
}
