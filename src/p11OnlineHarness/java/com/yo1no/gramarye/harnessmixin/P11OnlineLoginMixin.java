package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11OnlineLoginAccess;
import com.yo1no.gramarye.P11OnlineServerHarness;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Excluded identity bridge; it does not inspect login names, challenges, keys or session tokens. */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class P11OnlineLoginMixin implements P11OnlineLoginAccess {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;

    @Override
    public void p11$onlineAuthenticated(UUID authenticatedUuid) {
        P11OnlineServerHarness.authenticated(server, connection, authenticatedUuid);
    }
}
