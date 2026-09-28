package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativeWorldAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class P11MinecraftServerMixin implements P11NativeWorldAccess.ServerStorage {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    @Override public boolean p11$ownsWorldStorage(LevelStorageSource.LevelStorageAccess candidate) {
        return storageSource == candidate;
    }

    @Override public P11NativeWorldAccess.ReadWitness p11$worldReadWitness() {
        return ((P11NativeWorldAccess.ReadStorage) storageSource).p11$readWitness();
    }

    @WrapMethod(method = "stopServer()V")
    private void p11$stop(Operation<Void> original) {
        P11NativeStorageBoundary.stop((MinecraftServer) (Object) this, original);
    }

    @Inject(method = "stopServer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;close()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$detachedIndependentStop(CallbackInfo callback) {
        P11NativeStorageBoundary.flushDetachedIndependentAtStop((MinecraftServer) (Object) this, storageSource);
    }
}
