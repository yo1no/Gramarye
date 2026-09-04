package com.yo1no.gramarye.magic.network;

import java.util.Objects;

final class P7NetworkComposition {
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
        private static final P7ServerIntentResultSink RESULT_SINK = ignored -> {};
        private static final P7ServerDisconnectPort DISCONNECT_PORT =
                (server, actor) -> SERVER_ACCESS.disconnectCurrent(server, actor);
        private static final P7ServerAuthorizationDispatcher SERVER_DISPATCHER =
                new P7ServerAuthorizationDispatcher(
                        SESSION_SERVICE,
                        SERVER_ACCESS,
                        TARGET_VALIDATOR,
                        RESULT_SINK,
                        DISCONNECT_PORT);
        private static final P7NetworkComposition INSTANCE = new P7NetworkComposition(
                SESSION_SERVICE::currentEpoch,
                new P7PendingPermitOwner(),
                SERVER_DISPATCHER::dispatch,
                new P7ClientMirrorDispatchPort() {
                    @Override
                    public void onIntentAcknowledgement(
                            IntentAcknowledgement acknowledgement) {}

                    @Override
                    public void onPlayerManaSnapshot(PlayerManaSnapshot snapshot) {}

                    @Override
                    public void onSkillCooldownSnapshot(SkillCooldownSnapshot snapshot) {}
                });

        private ProductionHolder() {
            throw new AssertionError("no instances");
        }
    }
}
