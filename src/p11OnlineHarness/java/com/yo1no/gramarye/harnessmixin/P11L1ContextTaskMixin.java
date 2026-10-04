package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1ContextRefusalProbe;
import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Excluded one-call fixture hold, after real authentication and native CONFIG installation. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class P11L1ContextTaskMixin {
    @WrapOperation(method = "startNextTask()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ConfigurationTask;start(Ljava/util/function/Consumer;)V"),
            require = 1, expect = 1, allow = 1)
    private void l1$holdExactRequiredTask(ConfigurationTask task, Consumer<Packet<?>> sender, Operation<Void> original) {
        if (!P11L1ContextRefusalProbe.captureTask((ServerConfigurationPacketListenerImpl) (Object) this, task, sender, original)) {
            original.call(task, sender);
        }
    }
}
