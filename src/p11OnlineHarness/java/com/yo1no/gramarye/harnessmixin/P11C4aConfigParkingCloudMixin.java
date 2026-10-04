package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aConfigParkingResetProbe;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AreaEffectCloud.class)
abstract class P11C4aConfigParkingCloudMixin {
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/effect/MobEffect;applyInstantenousEffect(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;ID)V"),
            require = 1, expect = 1, allow = 1)
    private void reset$actualEffect(MobEffect effect, Entity direct, Entity owner,
            LivingEntity victim, int amplifier, double multiplier, Operation<Void> original) {
        P11C4aConfigParkingResetProbe.effect((AreaEffectCloud) (Object) this, effect, direct, owner,
                victim, amplifier, multiplier, original);
    }
}
