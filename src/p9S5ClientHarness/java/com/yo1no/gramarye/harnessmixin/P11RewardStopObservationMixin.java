package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeRewardStopProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

/** Observe the existing exact stop bridge; never replace its native operation or finalizer. */
@Mixin(value = P11NativeStorageBoundary.class, remap = false)
abstract class P11RewardStopObservationMixin {
    @WrapMethod(method = "stop(Lnet/minecraft/server/MinecraftServer;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$originalStop(MinecraftServer server, Operation<Void> nativeStop, Operation<Void> original) {
        boolean observed = P11NativeRewardStopProbe.begin(server);
        boolean normal = false;
        try { original.call(server, nativeStop); normal = true; }
        finally { P11NativeRewardStopProbe.end(server, observed, normal); }
    }
}
