package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownClientHarness;
import com.yo1no.gramarye.magic.network.P11CooldownInputObservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P9ClientCastInput")
abstract class P11CooldownClientInputMixin {
    @Shadow private boolean keyMappingRegistered;
    @Shadow private boolean senderSessionAvailable;
    @Shadow private boolean pendingFlushRequired;
    @WrapMethod(method = "handleClientPostTick()V", require = 1, expect = 1, allow = 1)
    private void p11$cooldownInputReady(Operation<Void> original) {
        original.call();
        P11CooldownClientHarness.inputReady(keyMappingRegistered && senderSessionAvailable && !pendingFlushRequired);
    }
    @Inject(method = "sendPayload(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void p11$cooldownSubmitted(@Coerce Object payload, CallbackInfo callback) {
        P11CooldownInputObservation.submitted(payload);
    }
}
