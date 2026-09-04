package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class IntentAckPayload implements CustomPacketPayload {
    static final Type<IntentAckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gramarye", "intent_ack"));
    static final StreamCodec<RegistryFriendlyByteBuf, IntentAckPayload> STREAM_CODEC =
            StreamCodec.of(
                    P7PayloadCodecSupport::encodeIntentAcknowledgement,
                    P7PayloadCodecSupport::decodeIntentAcknowledgement);

    private final IntentAcknowledgement acknowledgement;

    IntentAckPayload(IntentAcknowledgement acknowledgement) {
        this.acknowledgement = Objects.requireNonNull(
                acknowledgement, "acknowledgement");
    }

    IntentAcknowledgement acknowledgement() {
        return acknowledgement;
    }

    @Override
    public Type<IntentAckPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IntentAckPayload that)) {
            return false;
        }
        return acknowledgement.sequence() == that.acknowledgement.sequence()
                && acknowledgement.disposition()
                        == that.acknowledgement.disposition()
                && acknowledgement.flags() == that.acknowledgement.flags()
                && acknowledgement.expectedNext().equals(
                        that.acknowledgement.expectedNext());
    }

    @Override
    public int hashCode() {
        var result = Long.hashCode(acknowledgement.sequence());
        result = 31 * result + acknowledgement.disposition().hashCode();
        result = 31 * result + Integer.hashCode(acknowledgement.flags());
        return 31 * result + acknowledgement.expectedNext().hashCode();
    }
}
