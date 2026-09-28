package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeRewardProbe;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.ExecuteCommand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual parsed function command has returned; no replacement queue/action/executor. */
@Mixin(ExecuteCommand.class)
abstract class P11RewardFunctionFaultMixin {
    @Shadow @Final private String commandInput;

    @Inject(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/ContextChain;runExecutable(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/Object;Lcom/mojang/brigadier/ResultConsumer;Z)I", shift = At.Shift.AFTER),
            require = 1, expect = 1, allow = 1)
    private void p11$functionPrefix(ExecutionCommandSource<?> source, ExecutionContext<?> context,
            Frame frame, CallbackInfo callback) {
        P11NativeRewardProbe.afterFunctionCommand(source, context, commandInput);
    }
}
