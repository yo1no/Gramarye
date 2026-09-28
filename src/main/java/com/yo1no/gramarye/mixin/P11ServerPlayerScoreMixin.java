package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
abstract class P11ServerPlayerScoreMixin {
    @WrapMethod(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            require = 1, expect = 1, allow = 1)
    private Entity p11$sameBodyDimensionCredit(DimensionTransition transition, Operation<Entity> original) {
        var holder = (P11NativeOperationBoundary.CreditAccess) this;
        holder.p11$beginNativeConsumers();
        try { return original.call(transition); }
        finally { holder.p11$endNativeConsumers(); }
    }

    @WrapOperation(method = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;revive()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$actualSameBodyRevive(ServerPlayer actor, Operation<Void> original) {
        original.call(actor);
        // This exact native call, not RemovalReason or a later arbitrary revive, preserves
        // the original field tokens across same-object dimension travel.
        ((P11NativeOperationBoundary.CreditAccess) actor).p11$nativeCreditRevived();
    }

    @WrapOperation(method = "awardKillScore(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;increaseScore(I)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$scoreOnly(ServerPlayer originalActor, int amount, Operation<Void> original) {
        P11NativeOperationBoundary.killScore(originalActor, amount, original);
    }

    @WrapMethod(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$deathConsumers(DamageSource source, Operation<Void> original) {
        var holder = (P11NativeOperationBoundary.CreditAccess) this;
        holder.p11$beginNativeConsumers();
        P11NativeOperationBoundary.CreditScope scope = null;
        boolean normal = false;
        try { scope = holder.p11$beginKillCredit(); original.call(source); normal = true; }
        finally {
            try { P11NativeOperationBoundary.endCredit(scope, normal); }
            finally { holder.p11$endNativeConsumers(); }
        }
    }
}
