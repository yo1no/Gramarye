package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aConfigResetClientProbe;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class P11C4aConfigResetClientConfigurationMixin {
    @WrapMethod(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ClientboundFinishConfigurationPacket;)V",
            require = 1, expect = 1, allow = 1)
    private void c4a$originalFinish(ClientboundFinishConfigurationPacket packet, Operation<Void> original) {
        var listener = (ClientConfigurationPacketListenerImpl) (Object) this;
        boolean observed = P11C4aConfigResetClientProbe.beforeFinish(listener);
        boolean normal = false;
        try { original.call(packet); normal = true; }
        finally { P11C4aConfigResetClientProbe.afterFinish(listener, observed, normal); }
    }
}
