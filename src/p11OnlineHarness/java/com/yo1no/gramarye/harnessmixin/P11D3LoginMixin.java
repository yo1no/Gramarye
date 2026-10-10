package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11D3ServerHarness;
import com.yo1no.gramarye.P11OnlineLoginAccess;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class P11D3LoginMixin implements P11OnlineLoginAccess {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;
    @Override public void p11$onlineAuthenticated(UUID id) { P11D3ServerHarness.authenticated(server, connection, id); }
}
