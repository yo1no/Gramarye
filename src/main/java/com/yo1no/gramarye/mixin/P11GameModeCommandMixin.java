package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import com.yo1no.gramarye.P11NativePresence;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameModeCommand.class)
abstract class P11GameModeCommandMixin {
    @WrapOperation(method = "setMode(Lcom/mojang/brigadier/context/CommandContext;Ljava/util/Collection;Lnet/minecraft/world/level/GameType;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setGameMode(Lnet/minecraft/world/level/GameType;)Z"),
            require = 1, expect = 1, allow = 1)
    private static boolean p11$truthfulMode(ServerPlayer actor, GameType mode, Operation<Boolean> original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        return P11NativePresence.gameMode(context.getSource(), actor, mode, original);
    }
}
