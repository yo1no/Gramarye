package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ClientHarness;
import com.yo1no.gramarye.magic.network.P11L1InputObservation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P9ClientCastInput")
abstract class P11L1ClientInputMixin {
    @Shadow private boolean keyMappingRegistered;
    @Shadow private boolean senderSessionAvailable;
    @Shadow private boolean pendingFlushRequired;
    @WrapMethod(method = "handleClientPostTick()V", require = 1, expect = 1, allow = 1)
    private void p11$l1InputReady(Operation<Void> original) {
        original.call();
com.yo1no.gramarye.P11L1HostStopClientProbe.inputReady(keyMappingRegistered && senderSessionAvailable && !pendingFlushRequired);
P11L1ClientHarness.inputReady(keyMappingRegistered && senderSessionAvailable && !pendingFlushRequired);
    }
    @Inject(method = "sendPayload(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void p11$l1InputReturned(@Coerce Object payload, CallbackInfo callback) { P11L1InputObservation.submitted(payload); }
}
