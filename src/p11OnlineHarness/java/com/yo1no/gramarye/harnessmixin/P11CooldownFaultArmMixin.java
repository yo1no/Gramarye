package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownFaultProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** The owned exception is outside the product's callback-free OPEN/ARM scalar segment. */
@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService")
abstract class P11CooldownFaultArmMixin {
    @WrapMethod(method = "completeArm(Lcom/yo1no/gramarye/P11CastCooldownService$ArmPreparation;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$cooldownAfterOriginalArm(@Coerce Object arm, Operation<Void> original) {
        original.call(arm);
        P11CooldownFaultProbe.armReturned(arm);
    }
}
