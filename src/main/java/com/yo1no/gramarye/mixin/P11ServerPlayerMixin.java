package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11NativePresence;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class P11ServerPlayerMixin {
    @WrapOperation(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$deathScene(net.minecraft.world.entity.LivingEntity actor,
            net.minecraft.world.damagesource.DamageSource damage, Operation<Boolean> original) {
        boolean cancelled = original.call(actor, damage);
        if (!cancelled) { P11LiveTransitionBoundary.publishDeath((ServerPlayer) (Object) this); }
        return cancelled;
    }

    @Inject(method = "showEndCredits()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$endScene(CallbackInfo callback) {
        P11LiveTransitionBoundary.publishEnd((ServerPlayer) (Object) this);
    }

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At("RETURN"), require = 1, expect = 1)
    private void p11$constructed(MinecraftServer server, ServerLevel level, GameProfile profile,
            ClientInformation information, CallbackInfo callback) {
        P11NativeStorageBoundary.constructed((ServerPlayer) (Object) this);
    }

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"),
            require = 1, expect = 1, allow = 1)
    private void p11$constructorCallback(MinecraftServer server, ServerLevel level, GameProfile profile,
            ClientInformation information, CallbackInfo callback) {
        P11NativeStorageBoundary.constructorCallback((ServerPlayer) (Object) this);
    }

    @WrapMethod(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V")
    private void p11$copy(ServerPlayer old, boolean keepEverything, Operation<Void> original) {
        P11NativeStorageBoundary.copy((ServerPlayer) (Object) this, old, keepEverything, original);
    }

    @Inject(method = "setGameMode(Lnet/minecraft/world/level/GameType;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;changeGameModeForPlayer(Lnet/minecraft/world/level/GameType;)Z"),
            cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$modePresence(net.minecraft.world.level.GameType mode,
            CallbackInfoReturnable<Boolean> callback) {
        if (P11NativePresence.denied((ServerPlayer) (Object) this, "gamemode")) { callback.setReturnValue(false); }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$teleportPresence(ServerLevel level, double x, double y, double z,
            java.util.Set<net.minecraft.world.entity.RelativeMovement> movement, float yaw, float pitch,
            CallbackInfoReturnable<Boolean> callback) {
        if (P11NativePresence.denied((ServerPlayer) (Object) this, "teleport")) { callback.setReturnValue(false); }
    }

    @Inject(method = {"teleportTo(DDD)V", "teleportRelative(DDD)V"},
            at = @At("HEAD"), require = 2, expect = 2, allow = 2)
    private void p11$directTeleport(double x, double y, double z, CallbackInfo callback) {
        P11NativePresence.require((ServerPlayer) (Object) this, "teleport");
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$directLevelTeleport(ServerLevel level, double x, double y, double z,
            float yaw, float pitch, CallbackInfo callback) {
        P11NativePresence.require((ServerPlayer) (Object) this, "teleport");
    }

    @Inject(method = "moveTo(DDD)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$movePresence(double x, double y, double z, CallbackInfo callback) {
        P11NativePresence.require((ServerPlayer) (Object) this, "move");
    }

    @Inject(method = "setCamera(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void p11$cameraPresence(net.minecraft.world.entity.Entity target, CallbackInfo callback) {
        P11NativePresence.require((ServerPlayer) (Object) this, "camera");
    }
}
