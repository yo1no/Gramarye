package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aRequiredClientProbe;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
/** Client array only. Faults only this exact P11 registration; B and all other registrars are original. */
@Mixin(targets = "com.yo1no.gramarye.P11TransitionPayloadRegistrar", remap = false)
abstract class P11C4aRequiredRegistrarMixin {
    @WrapMethod(method = "register(Lnet/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent;)V")
    private static void required$register(RegisterPayloadHandlersEvent event, Operation<Void> original) {
        if (!P11C4aRequiredClientProbe.originalRegistration()) { return; }
        original.call(event);
        P11C4aRequiredClientProbe.registrationReturned();
    }
    @ModifyArg(method = "register(Lnet/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent;registrar(Ljava/lang/String;)Lnet/neoforged/neoforge/network/registration/PayloadRegistrar;"),
            index = 0, require = 1, expect = 1, allow = 1)
    private static String required$version(String original) { return P11C4aRequiredClientProbe.registrarVersion(original); }
}
