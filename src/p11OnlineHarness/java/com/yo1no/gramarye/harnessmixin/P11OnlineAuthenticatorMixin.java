package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.yo1no.gramarye.P11OnlineLoginAccess;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Observes only the non-null native hasJoinedServer result, never an offline-profile fallback. */
@Mixin(targets = "net.minecraft.server.network.ServerLoginPacketListenerImpl$1")
abstract class P11OnlineAuthenticatorMixin {
    @Shadow @Final private ServerLoginPacketListenerImpl this$0;

    @WrapOperation(method = "run()V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/authlib/yggdrasil/ProfileResult;profile()Lcom/mojang/authlib/GameProfile;"),
            require = 1, expect = 1, allow = 1)
    private GameProfile p11$successfulSessionProfile(ProfileResult result, Operation<GameProfile> original) {
        GameProfile profile = original.call(result);
        ((P11OnlineLoginAccess) this$0).p11$onlineAuthenticated(profile.getId());
        return profile;
    }
}
