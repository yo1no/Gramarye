package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class P11L1RewardMixin {
    @WrapMethod(method = "awardKillScore(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/world/damagesource/DamageSource;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1KillScore(net.minecraft.world.entity.Entity victim, int amount,
            net.minecraft.world.damagesource.DamageSource source, Operation<Void> original) {
        boolean observed = P11L1ServerHarness.beginKillScore((ServerPlayer) (Object) this, victim, source);
        boolean normal = false;
        try { original.call(victim, amount, source); normal = true; }
        finally { P11L1ServerHarness.endKillScore(observed, normal); }
    }
    @Inject(method = "giveExperiencePoints(I)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1OriginalExperience(int amount, CallbackInfo callback) {
        P11L1ServerHarness.experience((ServerPlayer) (Object) this, amount);
    }
}
