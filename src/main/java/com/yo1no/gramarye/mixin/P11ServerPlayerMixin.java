package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class P11ServerPlayerMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At("RETURN"), require = 1, expect = 1)
    private void p11$constructed(MinecraftServer server, ServerLevel level, GameProfile profile,
            ClientInformation information, CallbackInfo callback) {
        P11NativeStorageBoundary.constructed((ServerPlayer) (Object) this);
    }

    @WrapMethod(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V")
    private void p11$copy(ServerPlayer old, boolean keepEverything, Operation<Void> original) {
        P11NativeStorageBoundary.copy((ServerPlayer) (Object) this, old, keepEverything, original);
    }
}
