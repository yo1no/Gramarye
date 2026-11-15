package com.yo1no.gramarye.v0engineering.mixin;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.V0AuthenticatedLoginObservation;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the exact scoped authenticator call can publish an engineering witness. */
@Mixin(ServerLoginPacketListenerImpl.class)
abstract class V0OnlineSessionVerificationMixin {
    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private Connection connection;

    @Inject(method = "startClientVerification(Lcom/mojang/authlib/GameProfile;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void v0ObserveVerification(GameProfile profile, CallbackInfo callback) {
        V0AuthenticatedLoginObservation.observe((ServerLoginPacketListenerImpl) (Object) this,
                profile, server, connection);
    }
}
