package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/** Owns only call-local source selection cleanup; original configuration policy/catch stays intact. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class P11ConfigurationSourceMixin {
    @WrapMethod(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$sourceScope(ServerboundFinishConfigurationPacket packet, Operation<Void> original) {
        P11NativeStorageBoundary.configurationFinished(
                (ServerConfigurationPacketListenerImpl) (Object) this, packet, original);
    }
}
