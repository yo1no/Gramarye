package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aC6NativeProbe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$AdmissionWait", remap = false)
abstract class P11C4aC6WaitMixin {
    @Shadow @Final private long timeoutMillis;
    @Shadow private boolean started;
    @Shadow private boolean expired;
    @Shadow private boolean clockUnavailable;
    @Shadow private long startMillis;
    @Shadow private long lastMillis;
    @WrapMethod(method = "refused(J)Lcom/yo1no/gramarye/P11ControlBudgets$WaitResult;", require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$refused(long now, Operation<Object> original) {
        var result = original.call(now);
        P11C4aC6NativeProbe.waited(this, true, now, result, c6$fields());
        return result;
    }
    @WrapMethod(method = "observe(J)Lcom/yo1no/gramarye/P11ControlBudgets$WaitResult;", require = 1, expect = 1, allow = 1)
    @Coerce private Object c6$observed(long now, Operation<Object> original) {
        var result = original.call(now);
        P11C4aC6NativeProbe.waited(this, false, now, result, c6$fields());
        return result;
    }
    @Unique private P11C4aC6NativeProbe.WaitSample c6$fields() {
        synchronized (this) { return new P11C4aC6NativeProbe.WaitSample(started, expired, clockUnavailable,
                startMillis, lastMillis, timeoutMillis); }
    }
}
