package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.OptionalLong;

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
        private static final P7NetworkComposition INSTANCE = new P7NetworkComposition(
                ignored -> OptionalLong.empty(),
                new P7PendingPermitOwner(),
                ignored -> {},
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
