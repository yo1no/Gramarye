package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aConfigParkingResetProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ThrownPotion.class)
abstract class P11C4aConfigParkingPotionMixin {
    @WrapOperation(method = "makeAreaOfEffectCloud(Lnet/minecraft/world/item/alchemy/PotionContents;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean reset$actualCloud(Level level, Entity entity, Operation<Boolean> original) {
        return P11C4aConfigParkingResetProbe.cloudAdded((ThrownPotion) (Object) this, level, entity, original);
    }
}
