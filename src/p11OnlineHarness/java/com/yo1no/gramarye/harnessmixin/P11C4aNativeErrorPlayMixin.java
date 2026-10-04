package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11C4aNativeErrorPlayMixin {
    @WrapOperation(method="handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;isHardcore()Z"),require=1,expect=1,allow=1)
    private boolean error$requiredTail(MinecraftServer server,Operation<Boolean> original) {
        boolean result=original.call(server);
        P11C4aNativeErrorProbe.afterHardcoreQuery((ServerGamePacketListenerImpl)(Object)this);
        return result;
    }
}
