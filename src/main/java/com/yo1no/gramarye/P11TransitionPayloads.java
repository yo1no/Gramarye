package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

record P11TransitionRequestPayload(P11TransitionProtocol.Request request)
        implements CustomPacketPayload {
    static final Type<P11TransitionRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "transition_request"));
    static final StreamCodec<FriendlyByteBuf, P11TransitionRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> P11TransitionWire.encodeRequest(buffer, payload.request()),
                    buffer -> new P11TransitionRequestPayload(P11TransitionWire.decodeRequest(buffer)));

    P11TransitionRequestPayload { Objects.requireNonNull(request, "request"); }

    @Override
    public Type<P11TransitionRequestPayload> type() { return TYPE; }
}

record P11TransitionStatePayload(P11TransitionProtocol.State state)
        implements CustomPacketPayload {
    static final Type<P11TransitionStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "transition_state"));
    static final StreamCodec<FriendlyByteBuf, P11TransitionStatePayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> P11TransitionWire.encodeState(buffer, payload.state()),
                    buffer -> new P11TransitionStatePayload(P11TransitionWire.decodeState(buffer)));

    P11TransitionStatePayload { Objects.requireNonNull(state, "state"); }

    @Override
    public Type<P11TransitionStatePayload> type() { return TYPE; }
}
