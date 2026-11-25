package com.yo1no.gramarye.magic.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Generic normal operation. Identity, content and policy never come from the client. */
final class CastCurrentSpellPayload implements CustomPacketPayload {
    static final String NORMAL_PROTOCOL_VERSION = "gramarye-normal-spell-v1";
    static final Type<CastCurrentSpellPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gramarye", "cast_current_spell"));
    static final StreamCodec<RegistryFriendlyByteBuf, CastCurrentSpellPayload> STREAM_CODEC =
            StreamCodec.of(CastCurrentSpellPayload::encode, CastCurrentSpellPayload::decode);

    private final long sequence;

    CastCurrentSpellPayload(long sequence) {
        // All i64 structures decode; P7 alone classifies a non-positive sequence.
        this.sequence = sequence;
    }

    long sequence() { return sequence; }

    private static void encode(RegistryFriendlyByteBuf output, CastCurrentSpellPayload payload) {
        if (payload.sequence <= 0) {
            throw new EncoderException("normal outbound sequence is not positive");
        }
        try {
            output.writeLong(payload.sequence);
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode normal intent", failure);
        }
    }

    private static CastCurrentSpellPayload decode(RegistryFriendlyByteBuf input) {
        if (input.readableBytes() != Long.BYTES) {
            throw new DecoderException("normal intent must contain exactly eight bytes");
        }
        try {
            return new CastCurrentSpellPayload(input.readLong());
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed normal intent", failure);
        }
    }

    @Override public Type<CastCurrentSpellPayload> type() { return TYPE; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof CastCurrentSpellPayload that && sequence == that.sequence;
    }

    @Override public int hashCode() { return Long.hashCode(sequence); }
}
