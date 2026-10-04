package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aFirstTryProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Primitive observation of the actual fence publication, never a setter or model replacement. */
@Mixin(targets = "com.yo1no.gramarye.P11TransitionControl", remap = false)
abstract class P11C4aFirstTryControlMixin {
    @Shadow private long fence;
    @Shadow private long sceneSerial;
    @Shadow private long originalActor;
    @Shadow private long statusVersion;
    @Shadow private long lastClientSequence;

    @WrapMethod(method = "offer(Lcom/yo1no/gramarye/P11TransitionControl$Listener;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;J)Lcom/yo1no/gramarye/P11TransitionControl$Offer;",
            require = 1, expect = 1, allow = 1)
    @Coerce
    private Object c4a$firstTryOffered(@Coerce Object listener, @Coerce Object request, long now,
            Operation<Object> original) {
        P11C4aFirstTryProbe.offering(this, request);
        Object result = original.call(listener, request, now);
        P11C4aFirstTryProbe.offered(this, request, result);
        return result;
    }

    @Inject(method = "recordNotStarted(JLcom/yo1no/gramarye/P11TransitionProtocol$Reason;J)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c4a$firstTryFence(long sequence, @Coerce Object reason, long now, CallbackInfo callback) {
        P11C4aFirstTryProbe.fenced(this, sequence, reason, fence, sceneSerial, originalActor,
                statusVersion, lastClientSequence);
    }
}
