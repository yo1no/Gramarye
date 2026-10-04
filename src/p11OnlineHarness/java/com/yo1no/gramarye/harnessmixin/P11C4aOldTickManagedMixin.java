package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aOldTickProbe;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MinecraftServer.class)
abstract class P11C4aOldTickManagedMixin {
    @WrapOperation(method="reloadResources(Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;managedBlock(Ljava/util/function/BooleanSupplier;)V"),require=1,expect=1,allow=1)
    private void oldTick$managed(MinecraftServer server,BooleanSupplier ready,Operation<Void> original) {
        P11C4aOldTickProbe.managed(server,true,false);
        boolean normal=false;
        try { original.call(server,ready); normal=true; }
        finally { P11C4aOldTickProbe.managed(server,false,normal); }
    }
}
