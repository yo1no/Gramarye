package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

    static P7ServerLifecycleCoordinator lifecycle() {
        return ProductionHolder.LIFECYCLE;
    }

    static P7ReloadAdmissionGate reloadGate() {
        return ProductionHolder.RELOAD_GATE;
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
        private static final P7ServerSessionService SESSION_SERVICE =
                new P7ServerSessionService(SERVER_ACCESS, RELOAD_GATE);
        private static final P7AdvisoryTargetValidator TARGET_VALIDATOR =
                new P7AdvisoryTargetValidator();
        private static final P7PendingPermitOwner PERMITS = new P7PendingPermitOwner();
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
                SESSION_SERVICE::currentEpoch,
                PERMITS,
                SERVER_DISPATCHER::dispatch,
                P7ClientMirrorDispatchFactory.production());

        private ProductionHolder() {
            throw new AssertionError("no instances");
        }
    }
}
