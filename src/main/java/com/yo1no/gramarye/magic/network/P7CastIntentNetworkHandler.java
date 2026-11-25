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
        enqueueAuthenticated(new P7CastOperation.Legacy(payload.intent()),
                authenticatedPlayerId, context, composition);
    }

    static void handleNormal(CastCurrentSpellPayload payload, IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        if (!(context.player() instanceof ServerPlayer actor)) {
            context.disconnect(Component.literal("Invalid packet sender"));
            return;
        }
        handleNormalAuthenticated(payload, actor.getUUID(), context, composition);
    }

    static void handleNormalAuthenticated(CastCurrentSpellPayload payload, UUID authenticatedPlayerId,
            IPayloadContext context, P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        enqueueAuthenticated(new P7CastOperation.NormalSpell(payload.sequence()),
                authenticatedPlayerId, context, composition);
    }

    private static void enqueueAuthenticated(P7CastOperation operation, UUID authenticatedPlayerId,
            IPayloadContext context, P7NetworkComposition composition) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var permitOwner = composition.pendingPermitOwner();
        var connection = Objects.requireNonNull(context.connection(), "authenticated connection");
        var source = composition.connectionEpochSource();
        var captured = source.captureAuthenticatedSession(authenticatedPlayerId, connection);
        if (captured.outcome() != P7ConnectionEpochSnapshotSource.CaptureOutcome.CAPTURED) {
            return;
        }
        var identity = captured.identity().orElseThrow();
        if (!authenticatedPlayerId.equals(identity.authenticatedPlayerId())) {
            throw new P7SemanticInvariantException("captured session belongs to another sender");
        }
        var acquisition = permitOwner.acquire(
                authenticatedPlayerId, identity.connectionEpoch(), identity.serverGeneration());
        if (acquisition.outcome() == P7PendingPermitOwner.AcquireOutcome.SERVER_UNAVAILABLE
                || acquisition.outcome() == P7PendingPermitOwner.AcquireOutcome.STALE_GENERATION) {
            return;
        }
        if (acquisition.outcome() == P7PendingPermitOwner.AcquireOutcome.SERVER_BUSY) {
            if (source.isCurrentCapture(identity, connection)) {
                context.reply(new IntentAckPayload(new IntentAcknowledgement(
                        operation.sequence(),
                        IntentAcknowledgement.Disposition.SERVER_BUSY,
                        0,
                        null)));
            }
            return;
        }
        var permit = acquisition.permit().orElseThrow();
        boolean submissionReturned = false;
        Throwable primary = null;
        try {
            // This verifies only the original capture, never refreshes its authority.
            if (!source.isCurrentCapture(identity, connection)) {
                return;
            }
            var queuedIntent = new P7QueuedCastIntent(identity, operation);
            var task = new P7ServerDispatchTask(
                    queuedIntent, composition.serverIntentDispatchPort(), permit);
            context.enqueueWork(task);
            submissionReturned = true;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (!submissionReturned) {
                if (primary == null) {
                    permit.releaseAfterEnqueueFailure();
                } else {
                    try {
                        permit.releaseAfterEnqueueFailure();
                    } catch (RuntimeException | Error secondary) {
                        suppress(primary, secondary);
                    }
                }
            }
        }
    }

    private static void suppress(Throwable primary, Throwable secondary) {
        if (primary != secondary) {
            try {
                primary.addSuppressed(secondary);
            } catch (RuntimeException | Error suppressionFailure) {
                // Diagnostic failure cannot replace the original submission/dispatch primary.
            }
        }
    }
}
