package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRequiredClientProbe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aRequiredClientDisconnectMixin {
    @Inject(method = "handleDisconnect(Lnet/minecraft/network/protocol/common/ClientboundDisconnectPacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void required$closed(ClientboundDisconnectPacket packet, CallbackInfo callback) {
        P11C4aRequiredClientProbe.disconnected(((ClientCommonPacketListenerImpl) (Object) this).getConnection(), packet.reason());
    }
}
