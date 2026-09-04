package com.yo1no.gramarye.magic.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
interface P7ServerDisconnectPort {
    void disconnect(MinecraftServer server, ServerPlayer actor);
}
