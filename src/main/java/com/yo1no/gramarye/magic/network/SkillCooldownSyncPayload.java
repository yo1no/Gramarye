package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class SkillCooldownSyncPayload implements CustomPacketPayload {
    static final Type<SkillCooldownSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("gramarye", "skill_cooldown_sync"));
    static final StreamCodec<RegistryFriendlyByteBuf, SkillCooldownSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    P7PayloadCodecSupport::encodeSkillCooldownSnapshot,
                    P7PayloadCodecSupport::decodeSkillCooldownSnapshot);

    private final SkillCooldownSnapshot snapshot;

    SkillCooldownSyncPayload(SkillCooldownSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    SkillCooldownSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public Type<SkillCooldownSyncPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof SkillCooldownSyncPayload that
                        && snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        return snapshot.hashCode();
    }
}
