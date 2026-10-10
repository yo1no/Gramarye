package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11D3ClientHarness;
import com.yo1no.gramarye.magic.network.P11D3ClientObservation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P9ClientCastInput")
abstract class P11D3ClientInputMixin {
    @Shadow private boolean keyMappingRegistered;
    @Shadow private boolean senderSessionAvailable;
    @Shadow private boolean pendingFlushRequired;
    @WrapMethod(method = "handleClientPostTick()V", require = 1, expect = 1, allow = 1)
    private void p11$d3Ready(Operation<Void> original) {
        original.call(); P11D3ClientHarness.inputReady(keyMappingRegistered && senderSessionAvailable && !pendingFlushRequired);
    }
    @Inject(method = "sendPayload(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void p11$d3Sent(@Coerce Object payload, CallbackInfo callback) { P11D3ClientObservation.submitted(payload); }
}
