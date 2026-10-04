package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1WorkBoundaryProbe;
import org.spongepowered.asm.mixin.Mixin;

/** Scalar observation around the unchanged original per-instance W release. */
@Mixin(targets = "com.yo1no.gramarye.ServerSlot$InstanceState")
abstract class P11L1WorkReleaseMixin {
    @WrapMethod(method = "releaseWork()V", require = 1, expect = 1, allow = 1)
    private void p11$l1WorkReleased(Operation<Void> original) {
        int slot = P11L1WorkBoundaryProbe.beforeRelease(this);
        boolean normal = false;
        try {
            original.call();
            normal = true;
        } finally {
            P11L1WorkBoundaryProbe.afterRelease(slot, normal);
        }
    }
}
