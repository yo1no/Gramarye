package com.yo1no.gramarye.v0engineering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.V0AuthenticatedLoginObservation;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Scopes the exact successful hasJoinedServer call before native verification. */
@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
abstract class V0OnlineSessionSuccessMixin {
    @WrapOperation(method = "run()V",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;startClientVerification(Lcom/mojang/authlib/GameProfile;)V"),
            require = 1, expect = 1, allow = 1)
    private void v0ObserveSuccess(ServerLoginPacketListenerImpl login, GameProfile profile, Operation<Void> original) {
        V0AuthenticatedLoginObservation.enter(login, profile);
        try {
            original.call(login, profile);
        } finally {
            V0AuthenticatedLoginObservation.leave(login, profile);
        }
    }
}
