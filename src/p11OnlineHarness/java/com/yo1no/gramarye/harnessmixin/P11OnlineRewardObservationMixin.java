package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11OnlineServerHarness;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancementRewards.class)
abstract class P11OnlineRewardObservationMixin {
    @Inject(method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void online$grant(ServerPlayer player, CallbackInfo callback) {
        P11OnlineServerHarness.grantObserved((AdvancementRewards) (Object) this, player);
    }
}
