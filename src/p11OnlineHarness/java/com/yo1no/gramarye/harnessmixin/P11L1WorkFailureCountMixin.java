package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1WorkRewardProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11QualifiedSourceOwner")
abstract class P11L1WorkFailureCountMixin {
    @WrapMethod(method = "nativeOperationFailed(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1ActualFailureCounter(@Coerce Object body, Operation<Void> original) {
        original.call(body);
        P11L1WorkRewardProbe.operationFailureReturned(this, body);
    }
}
