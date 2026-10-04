package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aRequiredClientProbe;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class P11C4aRequiredClientConfigurationMixin {
    @WrapMethod(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V")
    private void required$payload(ClientboundCustomPayloadPacket packet, Operation<Void> original) {
        original.call(packet);
        P11C4aRequiredClientProbe.failedPayload(((ClientConfigurationPacketListenerImpl) (Object) this).getConnection(), packet);
    }
}
