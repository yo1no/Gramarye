package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.BuildContexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Custom commands and redirects execute before, or instead of, ExecuteCommand tasks. */
@Mixin(BuildContexts.class)
abstract class P11BuildContextsMixin {
    @WrapOperation(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;Lnet/minecraft/commands/execution/ChainModifiers;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/execution/CustomCommandExecutor;run(Ljava/lang/Object;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$customCommand(CustomCommandExecutor<?> executor, Object source,
            ContextChain<?> chain, ChainModifiers modifiers, ExecutionControl<?> control,
            Operation<Void> original, @Local(argsOnly = true) ExecutionContext<?> context) {
        var scope = P11NativeOperationBoundary.beginCommandSource(source, context);
        boolean normal = false;
        try { original.call(executor, source, chain, modifiers, control); normal = true; }
        finally { P11NativeOperationBoundary.end(scope, normal); }
    }

    @WrapOperation(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;Lnet/minecraft/commands/execution/ChainModifiers;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/execution/CustomModifierExecutor;apply(Ljava/lang/Object;Ljava/util/List;Lcom/mojang/brigadier/context/ContextChain;Lnet/minecraft/commands/execution/ChainModifiers;Lnet/minecraft/commands/execution/ExecutionControl;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$customModifier(CustomModifierExecutor<?> executor, Object source,
            List<?> sources, ContextChain<?> chain, ChainModifiers modifiers, ExecutionControl<?> control,
            Operation<Void> original, @Local(argsOnly = true) ExecutionContext<?> context) {
        var scopes = P11NativeOperationBoundary.beginCommandSources(source, sources, context);
        boolean normal = false;
        try { original.call(executor, source, sources, chain, modifiers, control); normal = true; }
        finally { P11NativeOperationBoundary.endCommandSources(scopes, normal); }
    }

    @WrapOperation(method = "execute(Lnet/minecraft/commands/ExecutionCommandSource;Ljava/util/List;Lnet/minecraft/commands/execution/ExecutionContext;Lnet/minecraft/commands/execution/Frame;Lnet/minecraft/commands/execution/ChainModifiers;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/ContextChain;runModifier(Lcom/mojang/brigadier/context/CommandContext;Ljava/lang/Object;Lcom/mojang/brigadier/ResultConsumer;Z)Ljava/util/Collection;"),
            require = 1, expect = 1, allow = 1)
    private Collection<?> p11$modifier(CommandContext<?> command, Object source,
            ResultConsumer<?> consumer, boolean forked, Operation<Collection<?>> original,
            @Local(argsOnly = true) ExecutionContext<?> context) throws CommandSyntaxException {
        var scope = P11NativeOperationBoundary.beginCommandSource(source, context);
        boolean normal = false;
        try {
            var result = original.call(command, source, consumer, forked);
            normal = true;
            return result;
        } finally { P11NativeOperationBoundary.end(scope, normal); }
    }
}
