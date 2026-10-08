package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownServerHarness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11CastCooldownService")
abstract class P11CooldownOwnerMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/P11FoundationService;Lcom/yo1no/gramarye/P11CastCooldownService$PolicyResolver;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownOwner(@Coerce Object foundation, @Coerce Object resolver, CallbackInfo callback) {
        P11CooldownServerHarness.cooldownOwner(this, foundation);
    }
}
