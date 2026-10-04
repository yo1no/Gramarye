package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1LifecycleBoundaryProbe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "com.yo1no.gramarye.ServerSlot$InstanceState")
abstract class P11L1LifecycleReleaseMixin {
    @WrapMethod(method = "releaseWork()V", require = 1, expect = 1, allow = 1)
    private void p11$l1LifecycleWorkReleased(Operation<Void> original) {
        boolean observed = P11L1LifecycleBoundaryProbe.beforeRelease(this);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally { P11L1LifecycleBoundaryProbe.afterRelease(observed, normal); }
    }
}
