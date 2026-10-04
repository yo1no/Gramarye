package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1PacketProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P8ServerPresentationService")
abstract class P11L1P8PolicyMixin {
    @WrapMethod(method = "submitEvent(Lcom/yo1no/gramarye/P8RecipientIdentity;Lcom/yo1no/gramarye/PresentationEvent;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1P8Policy(@Coerce Object identity, @Coerce Object event, Operation<Void> original) {
        boolean selected = P11L1PacketProbe.p8PolicyEntered(identity, event), normal = false;
        Throwable primary = null;
        try { original.call(identity, event); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1PacketProbe.p8PolicyFinished(selected, normal, primary); }
    }
}
