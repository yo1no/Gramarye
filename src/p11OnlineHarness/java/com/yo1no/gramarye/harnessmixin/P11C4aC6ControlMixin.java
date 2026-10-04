package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.yo1no.gramarye.P11C4aC6NativeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11TransitionControl", remap = false)
abstract class P11C4aC6ControlMixin implements P11C4aC6NativeProbe.ControlView {
    @Unique private Object c6$tryRate;
    @Unique private Object c6$statusRate;
    @Unique private Object c6$waiting;
    public Object c6$tryRate() { return c6$tryRate; }
    public Object c6$statusRate() { return c6$statusRate; }
    public Object c6$wait() { return c6$waiting; }

    // Observe the actual constructed objects; original field assignments stay in <init>/open.
    @ModifyExpressionValue(method = "<init>(Lcom/yo1no/gramarye/P11IdentityOwner$CapturedIdentity;Lcom/yo1no/gramarye/P11StartupLimits;JJJJ)V", at = @At(value = "NEW", target = "(IIJ)Lcom/yo1no/gramarye/P11ControlBudgets$TokenBucket;", ordinal = 0), require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$captureTry(@Coerce Object value) {
        c6$tryRate = value;
        return value;
    }
    @ModifyExpressionValue(method = "<init>(Lcom/yo1no/gramarye/P11IdentityOwner$CapturedIdentity;Lcom/yo1no/gramarye/P11StartupLimits;JJJJ)V", at = @At(value = "NEW", target = "(IIJ)Lcom/yo1no/gramarye/P11ControlBudgets$TokenBucket;", ordinal = 1), require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$captureStatus(@Coerce Object value) {
        c6$statusRate = value;
        return value;
    }
    @ModifyExpressionValue(method = {"<init>(Lcom/yo1no/gramarye/P11IdentityOwner$CapturedIdentity;Lcom/yo1no/gramarye/P11StartupLimits;JJJJ)V", "open(Lcom/yo1no/gramarye/P11TransitionProtocol$Scope;Lcom/yo1no/gramarye/P11TransitionProtocol$Kind;JZ)Z"}, at = @At(value = "NEW", target = "(J)Lcom/yo1no/gramarye/P11ControlBudgets$AdmissionWait;"), require = 2, expect = 2, allow = 2)
    @Coerce private Object c6$captureWait(@Coerce Object value) {
        c6$waiting = value;
        return value;
    }

    @WrapMethod(method = "offer(Lcom/yo1no/gramarye/P11TransitionControl$Listener;Lcom/yo1no/gramarye/P11TransitionProtocol$Request;J)Lcom/yo1no/gramarye/P11TransitionControl$Offer;", require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$offer(@Coerce Object listener,
            @Coerce Object request, long now, Operation<Object> original) {
        Object call = P11C4aC6NativeProbe.offering(this, request);
        Object result = null;
        boolean normal = false;
        try { result = original.call(listener, request, now); normal = true; return result; }
        finally { P11C4aC6NativeProbe.offered(call, result, normal); }
    }

    @WrapMethod(method = "nextService(Z)Lcom/yo1no/gramarye/P11TransitionControl$Service;", require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$service(boolean notificationsAllowed, Operation<Object> original) {
        var result = original.call(notificationsAllowed);
        P11C4aC6NativeProbe.service(this, result);
        return result;
    }
}
