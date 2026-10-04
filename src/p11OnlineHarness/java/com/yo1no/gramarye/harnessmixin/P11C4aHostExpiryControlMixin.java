package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aHostExpiryProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11TransitionControl", remap = false)
abstract class P11C4aHostExpiryControlMixin {
    @WrapOperation(method = "recordNotStarted(JLcom/yo1no/gramarye/P11TransitionProtocol$Reason;J)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11ControlBudgets$AdmissionWait;refused(J)Lcom/yo1no/gramarye/P11ControlBudgets$WaitResult;"),
            require = 1, expect = 1, allow = 1)
    @Coerce private Object expiry$refused(@Coerce Object wait, long now, Operation<Object> original) {
        var result = original.call(wait, now); P11C4aHostExpiryProbe.waited(this, wait, now, result, true); return result;
    }
    @WrapOperation(method = "observeWait(J)Lcom/yo1no/gramarye/P11ControlBudgets$WaitResult;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11ControlBudgets$AdmissionWait;observe(J)Lcom/yo1no/gramarye/P11ControlBudgets$WaitResult;"),
            require = 1, expect = 1, allow = 1)
    @Coerce private Object expiry$observe(@Coerce Object wait, long now, Operation<Object> original) {
        var result = original.call(wait, now); P11C4aHostExpiryProbe.waited(this, wait, now, result, false); return result;
    }
}
