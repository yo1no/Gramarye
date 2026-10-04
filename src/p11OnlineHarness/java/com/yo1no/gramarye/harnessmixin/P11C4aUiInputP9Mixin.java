package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aUiInputProbe;
import com.yo1no.gramarye.P11C4aUiContextProbe;
import com.yo1no.gramarye.magic.network.P11C4aUiInputP9Observation;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P9ClientCastInput", remap = false)
abstract class P11C4aUiInputP9Mixin {
    @Shadow private boolean keyMappingRegistered;
    @Shadow private boolean senderSessionAvailable;
    @Shadow private boolean pendingFlushRequired;
    @WrapMethod(method = "handleClientPostTick()V", require = 1, expect = 1, allow = 1)
    private void c4a$nativeConsumer(Operation<Void> original) {
        P11C4aUiInputProbe.beforeCastTick(keyMappingRegistered, senderSessionAvailable, pendingFlushRequired);
        var context = P11C4aUiContextProbe.before(keyMappingRegistered, senderSessionAvailable, pendingFlushRequired);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally { P11C4aUiContextProbe.after(context, normal); }
        P11C4aUiInputProbe.afterCastTick();
    }
    @WrapMethod(method = "evaluateGates(Lcom/yo1no/gramarye/magic/network/P9ClientCastInput$GateProbe;)Lcom/yo1no/gramarye/magic/network/P9ClientCastInput$GateDecision;",
            require = 1, expect = 1, allow = 1)
    @Coerce
    private static Object c4a$nativeGate(@Coerce Object probe, Operation<Object> original) {
        var result = original.call(probe);
        P11C4aUiContextProbe.gate(result);
        return result;
    }
    @Inject(method = "handleRenderFrame()V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeRender(CallbackInfo callback) { P11C4aUiContextProbe.rendered(); }
    @WrapOperation(method = "dropAllPendingClicks()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean c4a$drop(KeyMapping key, Operation<Boolean> original) {
        boolean consumed = original.call(key);
        if (consumed) { P11C4aUiInputProbe.droppedClick(); }
        if (consumed) { P11C4aUiContextProbe.dropped(); }
        return consumed;
    }
    @Inject(method = "sendPayload(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$actualCastSubmitted(@Coerce Object payload, CallbackInfo callback) {
        P11C4aUiInputP9Observation.submitted(payload);
    }
}
