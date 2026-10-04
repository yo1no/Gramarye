package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aParkingClientProbe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aParkingClientKeepAliveMixin {
    @Inject(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$parkingChallenge(ClientboundKeepAlivePacket packet, CallbackInfo callback) {
        P11C4aParkingClientProbe.keepAlive(((ClientCommonPacketListenerImpl) (Object) this).getConnection(), packet, false);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$parkingAck(Packet<?> packet, CallbackInfo callback) {
        P11C4aParkingClientProbe.keepAlive(((ClientCommonPacketListenerImpl) (Object) this).getConnection(), packet, true);
    }
}
