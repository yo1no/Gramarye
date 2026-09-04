package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class CastIntentPayload implements CustomPacketPayload {
    static final Type<CastIntentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gramarye", "cast_intent"));
    static final StreamCodec<RegistryFriendlyByteBuf, CastIntentPayload> STREAM_CODEC =
            StreamCodec.of(
                    P7PayloadCodecSupport::encodeCastIntent,
                    P7PayloadCodecSupport::decodeCastIntent);

    private final CastIntent intent;

    CastIntentPayload(CastIntent intent) {
        this.intent = Objects.requireNonNull(intent, "intent");
    }

    CastIntent intent() {
        return intent;
    }

    @Override
    public Type<CastIntentPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof CastIntentPayload that && intent.equals(that.intent);
    }

    @Override
    public int hashCode() {
        return intent.hashCode();
    }
}
