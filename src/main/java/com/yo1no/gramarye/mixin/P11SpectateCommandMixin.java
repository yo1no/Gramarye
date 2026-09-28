package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yo1no.gramarye.P11NativePresence;
import net.minecraft.server.commands.SpectateCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpectateCommand.class)
abstract class P11SpectateCommandMixin {
    @WrapOperation(method = "spectate(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerPlayer;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setCamera(Lnet/minecraft/world/entity/Entity;)V"),
            require = 1, expect = 1, allow = 1)
    private static void p11$truthfulSpectate(ServerPlayer actor, Entity target, Operation<Void> original)
            throws CommandSyntaxException {
        P11NativePresence.requireCommand(actor, "camera");
        original.call(actor, target);
    }
}
