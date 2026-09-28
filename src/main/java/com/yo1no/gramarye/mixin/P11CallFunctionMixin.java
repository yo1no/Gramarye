package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.CallFunction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CallFunction.class)
abstract class P11CallFunctionMixin {
    @WrapMethod(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$functionSource(ExecutionCommandSource<?> source, ExecutionContext<?> context,
            Frame frame, Operation<Void> original) {
        var scope = P11NativeOperationBoundary.beginCommandSource(source, context);
        boolean normal = false;
        try { original.call(source, context, frame); normal = true; }
        finally { P11NativeOperationBoundary.end(scope, normal); }
    }
}
