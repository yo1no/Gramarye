package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yo1no.gramarye.P11NativePresence;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RideCommand.class)
abstract class P11RideCommandMixin {
    @WrapOperation(method = "mount(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"),
            require = 1, expect = 1, allow = 1)
    private static boolean p11$truthfulRide(Entity actor, Entity vehicle, boolean force, Operation<Boolean> original)
            throws CommandSyntaxException {
        return P11NativePresence.ride(actor, vehicle, force, original);
    }
}
