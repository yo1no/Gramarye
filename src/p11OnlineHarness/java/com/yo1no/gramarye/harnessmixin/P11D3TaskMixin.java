package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ServerDispatchTask")
abstract class P11D3TaskMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/magic/network/P7QueuedCastIntent;Lcom/yo1no/gramarye/magic/network/P7ServerIntentDispatchPort;Lcom/yo1no/gramarye/magic/network/P7PendingPermit;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$d3Constructed(@Coerce Object queued, @Coerce Object port, @Coerce Object permit, CallbackInfo callback) {
        P11D3Observation.taskConstructed(this, queued);
    }
    @WrapMethod(method = "run()V", require = 1, expect = 1, allow = 1)
    private void p11$d3LateTask(Operation<Void> original) { P11D3Observation.task(this, () -> original.call()); }
}
