package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class PlayerManaSyncPayload implements CustomPacketPayload {
    static final Type<PlayerManaSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gramarye", "player_mana_sync"));
    static final StreamCodec<RegistryFriendlyByteBuf, PlayerManaSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    P7PayloadCodecSupport::encodePlayerManaSnapshot,
                    P7PayloadCodecSupport::decodePlayerManaSnapshot);

    private final PlayerManaSnapshot snapshot;

    PlayerManaSyncPayload(PlayerManaSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    PlayerManaSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Type<PlayerManaSyncPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PlayerManaSyncPayload that
                        && snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }
}
