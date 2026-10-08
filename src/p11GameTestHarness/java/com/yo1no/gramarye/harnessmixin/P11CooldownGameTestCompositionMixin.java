package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.P11CooldownGameTestHarness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Captures only the original composition's existing read-only qualification facade. */
@Mixin(Gramarye.class)
abstract class P11CooldownGameTestCompositionMixin {
    @WrapOperation(method = "<init>(Lnet/neoforged/bus/api/IEventBus;Lnet/neoforged/fml/ModContainer;)V",
            at = @At(value = "NEW", target = "()Lcom/yo1no/gramarye/P4E2QualificationFacade;"),
            require = 1, expect = 1, allow = 1)
    private P4E2QualificationFacade p11$observeOriginalFacade(Operation<P4E2QualificationFacade> original) {
        var facade = original.call();
        P11CooldownGameTestHarness.facade(facade);
        return facade;
    }
}
