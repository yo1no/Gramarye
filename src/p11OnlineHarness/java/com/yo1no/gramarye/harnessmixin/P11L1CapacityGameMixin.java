package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1CapacityWorkProbe;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11L1CapacityGameMixin {
    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ServerboundSwingPacket;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1PeerSwing(ServerboundSwingPacket packet, CallbackInfo callback) {
        P11L1CapacityWorkProbe.animate((ServerGamePacketListenerImpl) (Object) this);
    }
}
