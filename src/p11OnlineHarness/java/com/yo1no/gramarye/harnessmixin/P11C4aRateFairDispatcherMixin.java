package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRateFairProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$FairDispatcher", remap = false)
abstract class P11C4aRateFairDispatcherMixin {
    @Shadow @Final private ArrayDeque<Long> ready;
    @Shadow private int usedQuanta;
    @WrapMethod(method = "poll(J)Ljava/util/Optional;", require = 1, expect = 1, allow = 1)
    private Optional<?> rf$poll(long tick, Operation<Optional<?>> original) {
        List<Long> before; synchronized (this) { before = List.copyOf(ready); }
        var result = original.call(tick);
        int used; synchronized (this) { used = usedQuanta; }
        try { P11C4aRateFairProbe.polled(tick, before, used, result); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        return result;
    }
}
