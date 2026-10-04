package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import org.slf4j.Logger;

/** Native Configuration task and the one unstarted factory/place continuation. */
public final class P11ConfigurationBoundary {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component INVALID_DATA = Component.translatable("multiplayer.disconnect.invalid_player_data");
    private static final ThreadLocal<Placement> PLACEMENTS = new ThreadLocal<>();

    private P11ConfigurationBoundary() { }

    public interface Access {
        CommonListenerCookie p11$configurationCookie();
        ConfigurationTask.Type p11$currentConfigurationTask();
    }

    private static final class Placement {
        private final P11ParkingPacketListener parking;
        private final Placement previous;
        private ServerPlayer candidate;
        private boolean installed;

        private Placement(P11ParkingPacketListener parking, Placement previous) {
            this.parking = parking;
            this.previous = previous;
        }
    }

    public static void registerTasks(RegisterConfigurationTasksEvent event) {
        if (event.getListener() instanceof ServerConfigurationPacketListenerImpl listener) {
            event.register(new P11ConfigurationTask(listener));
        }
    }

    /** returnToWorld has no RegisterConfigurationTasksEvent; add only this task, not all mod tasks. */
    public static ConfigurationTask returnTask(ServerConfigurationPacketListenerImpl listener) {
        return new P11ConfigurationTask(listener);
    }

    public static void completeTask(ServerConfigurationPacketListenerImpl listener) {
        requireCurrent(listener);
        if (!P11ConfigurationTask.TYPE.equals(((Access) listener).p11$currentConfigurationTask())) {
            throw new IllegalStateException("P11_CONFIGURATION_TASK_NOT_CURRENT");
        }
        listener.finishCurrentTask(P11ConfigurationTask.TYPE);
    }

    /** Called inside the original Configuration try, immediately before its unique player factory. */
    public static boolean beforeFactory(ServerConfigurationPacketListenerImpl listener) {
        if (P11LiveTransitionBoundary.beforeConfigurationFactory(listener)) { return true; }
        requireCurrent(listener);
        var server = (MinecraftServer) listener.getMainThreadEventLoop();
        var parking = new P11ParkingPacketListener(server, listener.getConnection(),
                ((Access) listener).p11$configurationCookie());
        P11LiveTransitionBoundary.prepareParking(listener, parking);
        listener.getConnection().setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(server.registryAccess(), listener.getConnectionType())), parking);
        P11LiveTransitionBoundary.parkingInstalled(listener, parking);
        return false;
    }

    /** Only a current opaque root body ticket can enter the source scope below. */
    public static void resume(P11ParkingPacketListener parking) {
        requireCurrent(parking);
        P11NativeStorageBoundary.parkedConfiguration(parking, parking.profile(), parking.clientInformation(),
                () -> placeUnstarted(parking));
    }

    private static void placeUnstarted(P11ParkingPacketListener parking) {
        var scope = new Placement(parking, PLACEMENTS.get());
        PLACEMENTS.set(scope);
        try {
            // This is the original CONFIG tail's exception policy, not a second packet handler.
            // Duplicate/login eligibility is rechecked because an admission wait may be long.
            try {
                var server = (MinecraftServer) parking.getMainThreadEventLoop();
                var list = server.getPlayerList();
                if (list.getPlayer(parking.profile().getId()) != null) {
                    parking.disconnect(net.minecraft.server.players.PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
                    return;
                }
                var reason = list.canPlayerLogin(parking.getConnection().getRemoteAddress(), parking.profile());
                if (reason != null) { parking.disconnect(reason); return; }
                scope.candidate = list.getPlayerForLogin(parking.profile(), parking.clientInformation());
                list.placeNewPlayer(parking.getConnection(), scope.candidate, parking.currentCookie());
            } catch (Exception failure) {
                LOGGER.error("Couldn't place player in world", failure);
                parking.getConnection().send(new ClientboundDisconnectPacket(INVALID_DATA));
                parking.getConnection().disconnect(INVALID_DATA);
            }
        } finally {
            if (scope.previous == null) { PLACEMENTS.remove(); }
            else { PLACEMENTS.set(scope.previous); }
        }
    }

    /** Unique PlayerList native setup call; false means run its unchanged original operation. */
    public static boolean installParkedGame(Connection connection, ProtocolInfo<?> protocol, PacketListener listener) {
        var scope = PLACEMENTS.get();
        if (scope == null) { return false; }
        if (scope.installed || scope.parking.getConnection() != connection
                || !(listener instanceof ServerGamePacketListenerImpl game)
                || scope.candidate == null || game.player != scope.candidate
                || protocol.id() != net.minecraft.network.ConnectionProtocol.PLAY
                || protocol.flow() != connection.getReceiving()) {
            throw new IllegalStateException("P11_PARKED_PLACEMENT_MISMATCH");
        }
        P11KeepAliveBoundary.installGame(connection, scope.parking, game);
        scope.installed = true;
        return true;
    }

    private static void requireCurrent(net.minecraft.server.network.ServerCommonPacketListenerImpl listener) {
        if (!listener.getMainThreadEventLoop().isSameThread()
                || listener.getConnection().getPacketListener() != listener
                || !listener.getConnection().isConnected()) {
            throw new IllegalStateException("P11_CONFIGURATION_CALLER_NOT_CURRENT");
        }
    }
}
