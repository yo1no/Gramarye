package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.neoforged.neoforge.network.handling.IPayloadContext;

final class P7ClientPayloadHandlers {
    private P7ClientPayloadHandlers() {
        throw new AssertionError("no instances");
    }

    static void handleIntentAcknowledgement(
            IntentAckPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var port = composition.clientMirrorDispatchPort();
        long generation = port.captureDispatchGeneration(context.connection(), context.listener());
        if (generation > 0) {
            context.enqueueWork(new P7IntentAckDispatchTask(payload.acknowledgement(), port, generation));
        }
    }

    static void handlePlayerManaSnapshot(
            PlayerManaSyncPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var port = composition.clientMirrorDispatchPort();
        long generation = port.captureDispatchGeneration(context.connection(), context.listener());
        if (generation > 0) {
            context.enqueueWork(new P7ManaDispatchTask(payload.snapshot(), port, generation));
        }
    }

    static void handleSkillCooldownSnapshot(
            SkillCooldownSyncPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        var port = composition.clientMirrorDispatchPort();
        long generation = port.captureDispatchGeneration(context.connection(), context.listener());
        if (generation > 0) {
            context.enqueueWork(new P7CooldownDispatchTask(payload.snapshot(), port, generation));
        }
    }
}
