package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aCompleteBFaultProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Exact original selected stale-A cleanup, no retargeting, replacement policy or direct body invocation. */
@Mixin(value = P11NativeStorageBoundary.class, remap = false)
abstract class P11C4aCompleteBRemoveMixin {
    @WrapMethod(method = "remove(Lnet/minecraft/server/level/ServerPlayer;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            require = 1, expect = 1, allow = 1)
    private static void completeB$remove(ServerPlayer actor, Operation<Void> body, Operation<Void> original) {
        Object token = P11C4aCompleteBFaultProbe.staleRemoveEntered(actor);
        boolean normal = false; Throwable primary = null;
        try { original.call(actor, body); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11C4aCompleteBFaultProbe.staleRemoveEnded(token, normal, primary); }
    }
}
