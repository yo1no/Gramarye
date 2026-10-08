package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CooldownFaultProbe;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(P11NativeOperationBoundary.class)
abstract class P11CooldownFaultOperationMixin {
    @WrapOperation(method = "end(Lcom/yo1no/gramarye/P11NativeOperationBoundary$OperationScope;Z)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11QualifiedSourceOwner;nativeOperationFailed(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;)V"),
            require = 1, expect = 1, allow = 1)
    private static void p11$cooldownOperationFailure(@Coerce Object owner, @Coerce Object body,
            Operation<Void> original, P11NativeOperationBoundary.OperationScope scope, boolean normal) {
        P11CooldownFaultProbe.operationFailure(owner, body, scope, normal, false, false);
        boolean returned = false;
        try { original.call(owner, body); returned = true; }
        finally { P11CooldownFaultProbe.operationFailure(owner, body, scope, normal, true, returned); }
    }
}
