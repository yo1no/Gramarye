package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aHostExpiryClientProbe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aHostExpiryNetworkMixin {
    @Inject(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void host$peerChallenge(ClientboundKeepAlivePacket packet, CallbackInfo callback) {
        P11C4aHostExpiryClientProbe.received((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void host$peerAck(Packet<?> packet, CallbackInfo callback) {
        P11C4aHostExpiryClientProbe.sent((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
    @Inject(method = "handleDisconnect(Lnet/minecraft/network/protocol/common/ClientboundDisconnectPacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void host$peerDisconnect(ClientboundDisconnectPacket packet, CallbackInfo callback) {
        P11C4aHostExpiryClientProbe.disconnectPacket((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
}
