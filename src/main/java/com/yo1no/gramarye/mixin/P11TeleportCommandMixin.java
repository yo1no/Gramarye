package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yo1no.gramarye.P11NativePresence;
import java.util.Set;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportCommand.class)
abstract class P11TeleportCommandMixin {
    @WrapOperation(method = "performTeleport(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFLnet/minecraft/server/commands/TeleportCommand$LookAt;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z"),
            require = 1, expect = 1, allow = 1)
    private static boolean p11$truthfulTeleport(Entity actor, ServerLevel level, double x, double y,
            double z, Set<RelativeMovement> movement, float yaw, float pitch, Operation<Boolean> original)
            throws CommandSyntaxException {
        P11NativePresence.requireCommand(actor, "teleport");
        return original.call(actor, level, x, y, z, movement, yaw, pitch);
    }
}
