package com.yo1no.gramarye.magic.network;

import com.yo1no.gramarye.Gramarye;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

@EventBusSubscriber(modid = Gramarye.MOD_ID)
final class P7PayloadRegistrar {
    private static final P7NetworkComposition PRODUCTION =
            P7NetworkComposition.production();

    private P7PayloadRegistrar() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(P7NetworkBounds.PROTOCOL_VERSION)
                .executesOn(HandlerThread.NETWORK);
        registrar.playToServer(
                CastIntentPayload.TYPE,
                CastIntentPayload.STREAM_CODEC,
                (payload, context) -> P7CastIntentNetworkHandler.handle(
                        payload, context, PRODUCTION));
        registrar.playToClient(
                IntentAckPayload.TYPE,
                IntentAckPayload.STREAM_CODEC,
                (payload, context) -> P7ClientPayloadHandlers.handleIntentAcknowledgement(
                        payload, context, PRODUCTION));
        registrar.playToClient(
                PlayerManaSyncPayload.TYPE,
                PlayerManaSyncPayload.STREAM_CODEC,
                (payload, context) -> P7ClientPayloadHandlers.handlePlayerManaSnapshot(
                        payload, context, PRODUCTION));
        registrar.playToClient(
                SkillCooldownSyncPayload.TYPE,
                SkillCooldownSyncPayload.STREAM_CODEC,
                (payload, context) -> P7ClientPayloadHandlers.handleSkillCooldownSnapshot(
                        payload, context, PRODUCTION));
    }
}
