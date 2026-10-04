package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aNativeObservations;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes only the existing final CONFIG catch; original logging/exit policy is untouched. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class P11C4aConfigurationFailureMixin {
    @WrapOperation(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$originalCatch(Logger logger, String message, Throwable failure, Operation<Void> original) {
        P11C4aNativeObservations.configurationCatch(
                (ServerConfigurationPacketListenerImpl) (Object) this, failure);
        original.call(logger, message, failure);
    }
}
