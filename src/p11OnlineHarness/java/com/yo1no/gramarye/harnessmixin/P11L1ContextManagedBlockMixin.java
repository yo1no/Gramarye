package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1ContextRefusalProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftServer.class)
abstract class P11L1ContextManagedBlockMixin {
    @WrapOperation(method = "reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"),
            require = 1, expect = 1, allow = 1)
    private void l1$originalManagedBlock(MinecraftServer server, BooleanSupplier done, Operation<Void> original) {
        P11L1ContextRefusalProbe.managed(server, true, false);
        boolean normal = false;
        try { original.call(server, done); normal = true; }
        finally { P11L1ContextRefusalProbe.managed(server, false, normal); }
    }
}
