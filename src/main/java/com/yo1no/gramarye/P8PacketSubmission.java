package com.yo1no.gramarye;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Objects;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Locked PLAY encoder measurement and single-recipient local submission boundary. */
final class P8PacketSubmission {
    private P8PacketSubmission() {
        throw new AssertionError("no instances");
    }

    static int measureClientboundPlayPacket(
            ServerPlayer player, CustomPacketPayload payload, int maximumBytes) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("packet measurement bound must be positive");
        }
        if (!canSubmit(player, payload)) {
            throw new IllegalStateException("P8 payload connection is not accepting");
        }
        var encoderHandler = player.connection
                .getConnection()
                .channel()
                .pipeline()
                .get("encoder");
        if (!(encoderHandler instanceof PacketEncoder<?> encoder)
                || encoder.getProtocolInfo().id() != ConnectionProtocol.PLAY
                || encoder.getProtocolInfo().flow() != PacketFlow.CLIENTBOUND) {
            throw new IllegalStateException("P8 payload requires the live clientbound PLAY encoder");
        }
        ByteBuf encoded = Unpooled.buffer(Math.min(256, maximumBytes), maximumBytes);
        try {
            var connectionType = NetworkRegistry.getConnectionType(
                    player.connection.getConnection());
            var protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                    RegistryFriendlyByteBuf.decorator(
                            player.registryAccess(), connectionType));
            protocol.codec().encode(
                    encoded, new ClientboundCustomPayloadPacket(payload));
            return encoded.readableBytes();
        } finally {
            encoded.release();
        }
    }

    static boolean canSubmit(ServerPlayer player, CustomPacketPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        var listener = player.connection;
        if (listener == null) {
            return false;
        }
        var connection = listener.getConnection();
        return connection != null
                && connection.isConnected()
                && listener.isAcceptingMessages()
                && listener.hasChannel(payload)
                && hasClientboundPlayEncoder(connection)
                && !player.hasDisconnected();
    }

    private static boolean hasClientboundPlayEncoder(Connection connection) {
        var encoderHandler = connection.channel().pipeline().get("encoder");
        return encoderHandler instanceof PacketEncoder<?> encoder
                && encoder.getProtocolInfo().id() == ConnectionProtocol.PLAY
                && encoder.getProtocolInfo().flow() == PacketFlow.CLIENTBOUND;
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        if (!canSubmit(player, payload)) {
            throw new IllegalStateException("P8 payload connection stopped accepting");
        }
        player.connection.send(payload);
    }
}

interface P8CatalogTransport {
    default boolean canSubmit(ServerPlayer player, ProfileCatalogPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        return true;
    }

    int packetCharge(ServerPlayer player, ProfileCatalogPayload payload);

    void submit(ServerPlayer player, ProfileCatalogPayload payload);
}

enum P8ProductionCatalogTransport implements P8CatalogTransport {
    INSTANCE;

    @Override
    public boolean canSubmit(ServerPlayer player, ProfileCatalogPayload payload) {
        return P8PacketSubmission.canSubmit(player, payload);
    }

    @Override
    public int packetCharge(ServerPlayer player, ProfileCatalogPayload payload) {
        int measured = P8PacketSubmission.measureClientboundPlayPacket(
                player,
                payload,
                PresentationLimits.MAX_PROFILE_CATALOG_PACKET_CHARGE_BYTES);
        int expected = Math.addExact(
                payload.bodySize(),
                PresentationLimits.PROFILE_CATALOG_PACKET_OVERHEAD_BYTES);
        if (measured != expected
                || measured > PresentationLimits.MAX_PROFILE_CATALOG_PACKET_CHARGE_BYTES) {
            throw new IllegalStateException(
                    "P8 catalog PacketEncoder charge is not the authorized value");
        }
        return measured;
    }

    @Override
    public void submit(ServerPlayer player, ProfileCatalogPayload payload) {
        P8PacketSubmission.send(player, payload);
    }
}
