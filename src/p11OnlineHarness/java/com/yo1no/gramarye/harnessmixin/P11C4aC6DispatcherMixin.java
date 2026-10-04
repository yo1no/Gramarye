package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aC6NativeProbe;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$FairDispatcher", remap = false)
abstract class P11C4aC6DispatcherMixin {
    @Shadow @Final private ArrayDeque<Long> ready;
    @Shadow private int usedQuanta;
    @WrapMethod(method = "poll(J)Ljava/util/Optional;", require = 1, expect = 1, allow = 1)
    private Optional<?> c6$poll(long tick, Operation<Optional<?>> original) {
        List<Long> before;
        synchronized (this) { before = List.copyOf(ready); }
        var result = original.call(tick);
        int used;
        synchronized (this) { used = usedQuanta; }
        P11C4aC6NativeProbe.polled(this, tick, before, used, result);
        return result;
    }
    @WrapMethod(method = "complete(Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher$Dispatch;Z)Z", require = 1, expect = 1, allow = 1)
    private boolean c6$complete(@Coerce Object dispatch, boolean more,
            Operation<Boolean> original) {
        boolean result = original.call(dispatch, more);
        List<Long> after;
        synchronized (this) { after = List.copyOf(ready); }
        P11C4aC6NativeProbe.completed(this, dispatch, more, result, after);
        return result;
    }
}
