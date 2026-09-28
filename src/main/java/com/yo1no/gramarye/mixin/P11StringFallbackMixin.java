package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The original fallback still runs; its lossy encoding cannot qualify a managed snapshot. */
@Mixin(targets = "net.minecraft.nbt.NbtIo$StringFallbackDataOutput")
abstract class P11StringFallbackMixin {
    @WrapOperation(
            method = "writeUTF(Ljava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/Util;logAndPauseIfInIde(Ljava/lang/String;Ljava/lang/Throwable;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$encodingFallback(String message, Throwable failure, Operation<Void> original) {
        P11NativeStorageBoundary.stringEncodingFallback();
        original.call(message, failure);
    }
}
