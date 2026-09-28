package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeRewardProbe;
import net.minecraft.server.ServerFunctionManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observes, but does not bypass, the native function manager's Exception catch/log policy. */
@Mixin(ServerFunctionManager.class)
abstract class P11RewardFunctionCatchMixin {
    @WrapOperation(method = "execute(Lnet/minecraft/commands/functions/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)V",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$nativeCatch(Logger logger, String message, Object function, Object failure,
            Operation<Void> original) {
        P11NativeRewardProbe.caughtFunction(function, failure);
        original.call(logger, message, function, failure);
    }
}
