package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownL1Probe;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Excluded normal-return identity observation; no cell, material or publication mutation. */
@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService")
abstract class P11CooldownL1MaterialMixin {
    @Shadow @Final private Map<UUID, ?> cells;

    @Inject(method = "publish(Lcom/yo1no/gramarye/P11QualifiedSourceOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11CastCooldownService$Cell;Lcom/yo1no/gramarye/P11CastCooldownData;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownL1Published(@Coerce Object source, @Coerce Object body, @Coerce Object cell,
            @Coerce Object replacement, CallbackInfo callback) {
        P11CooldownL1Probe.published(source, body, cell, replacement);
    }

    @WrapMethod(method = "materialAdopted(Lcom/yo1no/gramarye/P11QualifiedSourceOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;)Z",
            require = 1, expect = 1, allow = 1)
    private boolean p11$cooldownL1Adopted(@Coerce Object source, @Coerce Object body, Operation<Boolean> original) {
        boolean result = original.call(source, body);
        P11CooldownL1Probe.materialAdopted(source, body, result, cells);
        return result;
    }
}
