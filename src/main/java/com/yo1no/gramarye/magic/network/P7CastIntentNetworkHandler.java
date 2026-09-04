package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class P7CastIntentNetworkHandler {
    private P7CastIntentNetworkHandler() {
        throw new AssertionError("no instances");
    }

    static void handle(
            CastIntentPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            context.disconnect(Component.literal("Invalid packet sender"));
            return;
        }
        handleAuthenticated(
                payload,
                serverPlayer.getUUID(),
                context,
                composition);
    }

    static void handleAuthenticated(
            CastIntentPayload payload,
            UUID authenticatedPlayerId,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var epochSnapshot = composition
                .connectionEpochSource()
                .currentEpoch(authenticatedPlayerId);
        if (epochSnapshot.isEmpty()) {
            return;
        }
        var connectionEpoch = epochSnapshot.getAsLong();
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException(
                    "connection epoch source returned an invalid value");
        }
        var acquisition = composition
                .pendingPermitOwner()
                .acquire(authenticatedPlayerId, connectionEpoch);
        if (acquisition.outcome() == P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY) {
            return;
        }
        var permit = acquisition.permit().orElseThrow();
        var queuedIntent = new P7QueuedCastIntent(
                authenticatedPlayerId, connectionEpoch, payload.intent());
        var task = new P7ServerDispatchTask(
                queuedIntent, composition.serverIntentDispatchPort(), permit);
        try {
            context.enqueueWork(task);
        } catch (RuntimeException | Error failure) {
            permit.release();
            throw failure;
        }
    }
}
