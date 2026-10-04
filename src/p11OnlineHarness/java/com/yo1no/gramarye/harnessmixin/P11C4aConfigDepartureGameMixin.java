package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11C4aConfigDepartureGameMixin {
    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void departure$livePeerAction(ServerboundSwingPacket packet, CallbackInfo callback) {
        P11C4aConfigDepartureProbe.animate((ServerGamePacketListenerImpl) (Object) this);
    }
}
