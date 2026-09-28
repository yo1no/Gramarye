package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Commands.class)
abstract class P11CommandsMixin {
    @Shadow @Final private static ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT;

    @WrapMethod(method = "executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Consumer;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$owningContext(CommandSourceStack source,
            Consumer<ExecutionContext<CommandSourceStack>> consumer, Operation<Void> original) {
        P11NativeOperationBoundary.commands(source, consumer, CURRENT_EXECUTION_CONTEXT.get(), original);
    }
}
