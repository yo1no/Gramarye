package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1ImpactCustodyProbe;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.yo1no.gramarye.P9StarterProjectile")
abstract class P11L1ImpactCustodyMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onProjectileImpact(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/HitResult;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean l1$afterOriginalImpact(Projectile projectile, HitResult hit, Operation<Boolean> original) {
        boolean cancelled = original.call(projectile, hit);
        P11L1ImpactCustodyProbe.impactReturned(projectile, hit, cancelled);
        return cancelled;
    }
}
