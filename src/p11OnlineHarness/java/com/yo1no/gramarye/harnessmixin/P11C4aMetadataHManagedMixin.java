package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aMetadataHProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** One original locked-Z reloadResources managedBlock; never replaces its condition or body. */
@Mixin(MinecraftServer.class)
abstract class P11C4aMetadataHManagedMixin {
    @WrapOperation(method = "reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$metadataHManaged(MinecraftServer server, BooleanSupplier done, Operation<Void> original) {
        P11C4aMetadataHProbe.managed(server, true, false);
        boolean normal = false;
        try { original.call(server, done); normal = true; }
        finally { P11C4aMetadataHProbe.managed(server, false, normal); }
    }
}
