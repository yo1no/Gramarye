package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderDragon.class)
abstract class P11EnderDragonCreditMixin implements P11NativeOperationBoundary.DragonCreditAccess {
    @Shadow private Player unlimitedLastHurtByPlayer;
    @Unique private P11NativeOperationBoundary.Credit p11$credit;

    @WrapOperation(method = "aiStep()V", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;unlimitedLastHurtByPlayer:Lnet/minecraft/world/entity/player/Player;"),
            require = 2, expect = 2, allow = 2)
    private void p11$nativeCredit(EnderDragon holder, Player value, Operation<Void> original) {
        var replacement = P11NativeOperationBoundary.acquireCredit(holder, value instanceof ServerPlayer actor ? actor : null);
        boolean written = false;
        try { original.call(holder, value); written = true; }
        finally { p11$credit = P11NativeOperationBoundary.fieldWritten(p11$credit, replacement, written); }
    }

    @Override public void p11$releaseDragonCredit() {
        P11NativeOperationBoundary.releaseCredit(p11$credit);
        p11$credit = null;
    }

    @WrapMethod(method = "tickDeath()V", require = 1, expect = 1, allow = 1)
    private void p11$deathConsumers(Operation<Void> original) {
        var holder = (P11NativeOperationBoundary.CreditAccess) this;
        holder.p11$beginNativeConsumers();
        P11NativeOperationBoundary.CreditScope scope = null;
        boolean normal = false;
        try {
            scope = unlimitedLastHurtByPlayer instanceof ServerPlayer player
                    ? P11NativeOperationBoundary.beginCredit((EnderDragon) (Object) this, player, p11$credit) : null;
            original.call(); normal = true;
        } finally {
            try { P11NativeOperationBoundary.endCredit(scope, normal); }
            finally { holder.p11$endNativeConsumers(); }
        }
    }
}
