package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aHeldProbe;
import com.yo1no.gramarye.P11C4aErrorStatusProbe;
import com.yo1no.gramarye.P11C4aTerminalStatusProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11TransitionControl", remap = false)
abstract class P11C4aHeldControlMixin {
    @WrapMethod(method = "offer(Lcom/yo1no/gramarye/P11TransitionControl$Listener;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;J)Lcom/yo1no/gramarye/P11TransitionControl$Offer;", require = 1, expect = 1, allow = 1)
    @Coerce private Object held$offer(@Coerce Object listener, @Coerce Object request, long now, Operation<Object> original) {
        var observation = P11C4aHeldProbe.beforeOffer(this, request);
        var errorObservation = P11C4aErrorStatusProbe.beforeOffer(this, request);
        var terminalObservation = P11C4aTerminalStatusProbe.beforeOffer(this, request);
        Object result = original.call(listener, request, now);
        P11C4aHeldProbe.afterOffer(observation, result);
        P11C4aErrorStatusProbe.afterOffer(errorObservation, result);
        P11C4aTerminalStatusProbe.afterOffer(terminalObservation, result);
        return result;
    }
}
