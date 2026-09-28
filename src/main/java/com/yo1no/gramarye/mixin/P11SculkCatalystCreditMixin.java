package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.advancements.critereon.KilledTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Sculk uses lastHurtByMob, not getKillCredit's preferred lastHurtByPlayer. */
@Mixin(targets = "net.minecraft.world.level.block.entity.SculkCatalystBlockEntity$CatalystListener")
abstract class P11SculkCatalystCreditMixin {
    @WrapOperation(method = "tryAwardItSpreadsAdvancement(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/critereon/KilledTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$mobCause(KilledTrigger trigger, ServerPlayer actor, Entity victim, DamageSource damage,
            Operation<Void> original, @Local(argsOnly = true) LivingEntity exactVictim) {
        var scope = ((P11NativeOperationBoundary.CreditAccess) exactVictim).p11$beginMobCredit(actor);
        boolean normal = false;
        try { original.call(trigger, actor, victim, damage); normal = true; }
        finally { P11NativeOperationBoundary.endCredit(scope, normal); }
    }
}
