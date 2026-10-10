package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

final class P7ServerAccess {
    MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    boolean sameThread(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").isSameThread();
    }

    boolean running(MinecraftServer server) {
        var exactServer = Objects.requireNonNull(server, "server");
        return exactServer.isRunning() && !exactServer.isStopped();
    }

    ServerPlayer currentPlayer(MinecraftServer server, UUID authenticatedPlayerId) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        return Objects.requireNonNull(server, "server")
                .getPlayerList()
                .getPlayer(authenticatedPlayerId);
    }

    boolean currentConnectedPlayer(
            MinecraftServer server,
            ServerPlayer actor,
            UUID authenticatedPlayerId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        var connection = actorConnection(server, actor);
        return connection != null
                && authenticatedPlayerId.equals(actor.getUUID())
                && server.getPlayerList().getPlayer(authenticatedPlayerId) == actor
                && actor.connection.isAcceptingMessages()
                && !actor.hasDisconnected();
    }

    /** Exact native source, including closed-C logout; never substitutes the UUID roster. */
    Connection actorConnection(MinecraftServer server, ServerPlayer actor) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        var listener = actor.connection;
        if (actor.getServer() != server || listener == null || listener.player != actor) {
            return null;
        }
        var connection = listener.getConnection();
        return connection.getPacketListener() == listener ? connection : null;
    }

    long authoritativeTick(MinecraftServer server) {
        return Objects.requireNonNull(server, "server").overworld().getGameTime();
    }

    void disconnectCurrent(MinecraftServer server, ServerPlayer actor) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        if (!server.isSameThread()) {
            throw new P7SemanticInvariantException("disconnect outside the server logic thread");
        }
        if (!currentConnectedPlayer(server, actor, actor.getUUID())) {
            return;
        }
        actor.connection.disconnect(Component.literal("Network session closed"));
    }
}
