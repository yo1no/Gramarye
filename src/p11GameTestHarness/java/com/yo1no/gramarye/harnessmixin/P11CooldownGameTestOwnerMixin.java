package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownGameTestHarness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService")
abstract class P11CooldownGameTestOwnerMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/P11FoundationService;Lcom/yo1no/gramarye/P11CastCooldownService$PolicyResolver;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$capture(@Coerce Object foundation,
            @Coerce Object resolver, CallbackInfo callback) {
        P11CooldownGameTestHarness.constructed(foundation, this);
    }
}
