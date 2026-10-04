package com.yo1no.gramarye;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

/** Required, fixed-schema channels in both CONFIG and PLAY; neither handler asks for a player. */
final class P11TransitionPayloadRegistrar {
    private P11TransitionPayloadRegistrar() { }

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(P11TransitionWire.REGISTRAR_VERSION);
        registrar.executesOn(HandlerThread.NETWORK).commonToServer(
                P11TransitionRequestPayload.TYPE, P11TransitionRequestPayload.STREAM_CODEC,
                (payload, context) -> P11LiveTransitionBoundary.ingress(
                        payload.request(), context.connection(), context.listener()));
        registrar.executesOn(HandlerThread.MAIN).commonToClient(
                P11TransitionStatePayload.TYPE, P11TransitionStatePayload.STREAM_CODEC,
                (payload, context) -> P11ClientTransitionDispatch.handle(
                        payload.state(), context.connection(), context.listener()));
    }
}
