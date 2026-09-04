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
        context.enqueueWork(new P7IntentAckDispatchTask(
                payload.acknowledgement(), composition.clientMirrorDispatchPort()));
    }

    static void handlePlayerManaSnapshot(
            PlayerManaSyncPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        context.enqueueWork(new P7ManaDispatchTask(
                payload.snapshot(), composition.clientMirrorDispatchPort()));
    }

    static void handleSkillCooldownSnapshot(
            SkillCooldownSyncPayload payload,
            IPayloadContext context,
            P7NetworkComposition composition) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(composition, "composition");
        context.enqueueWork(new P7CooldownDispatchTask(
                payload.snapshot(), composition.clientMirrorDispatchPort()));
    }
}
