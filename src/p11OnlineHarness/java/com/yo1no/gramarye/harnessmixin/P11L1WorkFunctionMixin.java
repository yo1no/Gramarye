package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1WorkRewardProbe;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.server.ServerFunctionManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerFunctionManager.class)
abstract class P11L1WorkFunctionMixin {
    @WrapMethod(method = "execute(Lnet/minecraft/commands/functions/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1Function(CommandFunction<CommandSourceStack> function, CommandSourceStack source, Operation<Void> original) {
        int role = P11L1WorkRewardProbe.functionEntered(function, source);
        boolean normal = false; Throwable primary = null;
        try { original.call(function, source); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1WorkRewardProbe.functionFinished(role, normal, primary); }
    }
    @WrapOperation(method = "execute(Lnet/minecraft/commands/functions/CommandFunction;Lnet/minecraft/commands/CommandSourceStack;)V",
            at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$l1SameNativeCatch(Logger logger, String text, Object function, Object failure, Operation<Void> original) {
        boolean selected = P11L1WorkRewardProbe.selectedCatch(function, failure);
        original.call(logger, text, function, failure);
        P11L1WorkRewardProbe.catchReturned(selected);
    }
}
