package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigCatchProbe;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11C4aConfigCatchGameMixin {
    @Inject(method="handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V",at=@At("RETURN"),require=1,expect=1,allow=1)
    private void configCatch$animate(ServerboundSwingPacket packet,CallbackInfo ci){P11C4aConfigCatchProbe.animate((ServerGamePacketListenerImpl)(Object)this);}
}
