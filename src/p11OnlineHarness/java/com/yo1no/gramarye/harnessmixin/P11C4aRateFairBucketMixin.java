package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRateFairProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$TokenBucket", remap = false)
abstract class P11C4aRateFairBucketMixin implements P11C4aRateFairProbe.BucketView {
    @Shadow private long units;
    @Override public long rf$units() { synchronized (this) { return units; } }
    @WrapMethod(method = "take(J)Lcom/yo1no/gramarye/P11ControlBudgets$RateResult;", require = 1, expect = 1, allow = 1)
    @Coerce private Object rf$take(long now, Operation<Object> original) {
        long before; synchronized (this) { before = units; }
        long other = -1;
        try { other = P11C4aRateFairProbe.counterpartBefore(this); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        var result = original.call(now);
        long after; synchronized (this) { after = units; }
        try { P11C4aRateFairProbe.token(this, now, before, after, other, result); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        return result;
    }
}
