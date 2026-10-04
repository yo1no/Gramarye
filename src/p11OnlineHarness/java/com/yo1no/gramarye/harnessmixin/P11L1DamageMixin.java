package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1ServerHarness;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yo1no.gramarye.P9WorldEffectHandoff")
abstract class P11L1DamageMixin {
    @WrapOperation(method = "commitDamage(Lcom/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge$DamageCommit;)Lcom/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge$CommitDisposition;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$l1OriginalHurt(LivingEntity target, DamageSource source, float amount, Operation<Boolean> original) {
        P11L1ServerHarness.damageEntering(target, source);
        boolean result = original.call(target, source, amount);
com.yo1no.gramarye.P11L1HostStopProbe.damage(target, source, amount, result);
P11L1ServerHarness.nativeDamage(target, source, amount, result);
        return result;
    }
    @Inject(method = "commitDamage(Lcom/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge$DamageCommit;)Lcom/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge$CommitDisposition;",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1DamageReturned(CallbackInfoReturnable<Object> callback) { P11L1ServerHarness.damageReturned(callback.getReturnValue()); }
}
