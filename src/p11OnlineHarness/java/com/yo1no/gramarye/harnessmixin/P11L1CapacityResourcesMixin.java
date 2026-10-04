package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1CapacityWorkProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11ControlBudgets$Resources")
abstract class P11L1CapacityResourcesMixin {
    @WrapMethod(method = "mayAdmitWork(Lcom/yo1no/gramarye/P11ControlBudgets$Resources$AccountOwner;)Z", require = 1, expect = 1, allow = 1)
    private boolean p11$l1ManagedGate(@Coerce Object account, Operation<Boolean> original) {
        boolean result = original.call(account);
        P11L1CapacityWorkProbe.managedGateReturned(account, result);
        return result;
    }
}
