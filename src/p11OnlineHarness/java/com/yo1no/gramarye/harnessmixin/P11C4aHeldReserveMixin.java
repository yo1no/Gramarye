package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aHeldProbe;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aHeldReserveMixin {
    @WrapOperation(method = "service(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11TransitionControl;reserveDrain(Lcom/yo1no/gramarye/P11TransitionControl$Listener;)Ljava/util/Optional;"), require = 1, expect = 1, allow = 1)
    private Optional<?> held$reserve(@Coerce Object control, @Coerce Object listener, Operation<Optional<?>> original) {
        return P11C4aHeldProbe.reserve(control, listener, original);
    }
}
