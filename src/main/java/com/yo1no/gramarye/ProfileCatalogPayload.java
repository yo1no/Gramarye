package com.yo1no.gramarye;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Exact clientbound P8 Profile-catalog payload value. */
final class ProfileCatalogPayload implements CustomPacketPayload {
    static final Type<ProfileCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "profile_catalog"));
    static final StreamCodec<RegistryFriendlyByteBuf, ProfileCatalogPayload> STREAM_CODEC =
            StreamCodec.of(
                    P8PayloadCodecSupport::encodeProfileCatalog,
                    P8PayloadCodecSupport::decodeProfileCatalog);

    private final P8ProfileCatalogSnapshot snapshot;

    ProfileCatalogPayload(
            long catalogGeneration, List<P8ProfileCatalogEntry> entries) {
        this(new P8ProfileCatalogSnapshot(catalogGeneration, entries));
    }

    ProfileCatalogPayload(P8ProfileCatalogSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    static ProfileCatalogPayload incoming(
            long catalogGeneration, List<P8ProfileCatalogEntry> entries) {
        return new ProfileCatalogPayload(
                P8ProfileCatalogSnapshot.incoming(catalogGeneration, entries));
    }

    P8ProfileCatalogSnapshot snapshot() {
        return snapshot;
    }

    long catalogGeneration() {
        return snapshot.catalogGeneration();
    }

    List<P8ProfileCatalogEntry> entries() {
        return snapshot.entries();
    }

    int bodySize() {
        return snapshot.wireBodyBytes();
    }

    @Override
    public Type<ProfileCatalogPayload> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ProfileCatalogPayload that
                        && catalogGeneration() == that.catalogGeneration()
                        && entries().equals(that.entries());
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalogGeneration(), entries());
    }
}
