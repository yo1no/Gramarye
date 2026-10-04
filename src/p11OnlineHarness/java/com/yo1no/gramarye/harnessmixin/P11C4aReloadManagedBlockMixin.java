package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aReloadBlockerProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Locked Z reloadResources has exactly one original MinecraftServer.managedBlock invocation. */
@Mixin(MinecraftServer.class)
abstract class P11C4aReloadManagedBlockMixin {
    @WrapOperation(method = "reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$reloadManagedBlock(MinecraftServer server, BooleanSupplier done, Operation<Void> original) {
        P11C4aReloadBlockerProbe.managed(server, true, false);
        boolean normal = false;
        try { original.call(server, done); normal = true; }
        finally { P11C4aReloadBlockerProbe.managed(server, false, normal); }
    }
}
