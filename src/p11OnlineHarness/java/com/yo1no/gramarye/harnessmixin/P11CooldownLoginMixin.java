package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownServerHarness;
import com.yo1no.gramarye.P11OnlineLoginAccess;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Paired only with the existing original hasJoined observer; no names/challenges/keys are read. */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class P11CooldownLoginMixin implements P11OnlineLoginAccess {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;
    @Override public void p11$onlineAuthenticated(UUID id) {
        P11CooldownServerHarness.authenticated(server, connection, id);
    }
}
