package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aC6NativeProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$TokenBucket", remap = false)
abstract class P11C4aC6BucketMixin {
    @Shadow private long units;
    @WrapMethod(method = "take(J)Lcom/yo1no/gramarye/P11ControlBudgets$RateResult;", require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$take(long now, Operation<Object> original) {
        long before;
        synchronized (this) { before = units; }
        var result = original.call(now);
        long after;
        synchronized (this) { after = units; }
        P11C4aC6NativeProbe.took(this, now, before, after, result);
        return result;
    }
}
