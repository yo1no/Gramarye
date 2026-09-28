package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class P11LivingEntityCreditMixin implements P11NativeOperationBoundary.CreditAccess {
    @Shadow protected Player lastHurtByPlayer;
    @Shadow private LivingEntity lastHurtByMob;
    @Unique private P11NativeOperationBoundary.Credit p11$playerCredit;
    @Unique private P11NativeOperationBoundary.Credit p11$mobCredit;
    @Unique private P11NativeOperationBoundary.Credit p11$damageCredit;
    @Unique private final P11NativeOperationBoundary.CreditLifetime p11$creditLifetime =
            P11NativeOperationBoundary.createCreditLifetime();

    @WrapOperation(method = {"baseTick()V", "setLastHurtByPlayer(Lnet/minecraft/world/entity/player/Player;)V",
            "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByPlayer:Lnet/minecraft/world/entity/player/Player;"),
            require = 5, expect = 5, allow = 5)
    private void p11$playerField(LivingEntity holder, Player value, Operation<Void> original) {
        var replacement = P11NativeOperationBoundary.acquireCredit(holder, value instanceof ServerPlayer actor ? actor : null);
        boolean written = false;
        try { original.call(holder, value); written = true; }
        finally { p11$playerCredit = P11NativeOperationBoundary.fieldWritten(p11$playerCredit, replacement, written); }
    }

    @WrapOperation(method = "setLastHurtByMob(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/entity/LivingEntity;lastHurtByMob:Lnet/minecraft/world/entity/LivingEntity;"),
            require = 1, expect = 1, allow = 1)
    private void p11$mobField(LivingEntity holder, LivingEntity value, Operation<Void> original) {
        var replacement = P11NativeOperationBoundary.acquireCredit(holder, value instanceof ServerPlayer actor ? actor : null);
        boolean written = false;
        try { original.call(holder, value); written = true; }
        finally { p11$mobCredit = P11NativeOperationBoundary.fieldWritten(p11$mobCredit, replacement, written); }
    }

    @WrapOperation(method = {"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            "handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V",
            "getLastDamageSource()Lnet/minecraft/world/damagesource/DamageSource;"},
            at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/entity/LivingEntity;lastDamageSource:Lnet/minecraft/world/damagesource/DamageSource;"),
            require = 3, expect = 3, allow = 3)
    private void p11$damageField(LivingEntity holder, DamageSource value, Operation<Void> original) {
        var replacement = P11NativeOperationBoundary.acquireCredit(holder,
                value != null && value.getEntity() instanceof ServerPlayer actor ? actor : null);
        boolean written = false;
        try { original.call(holder, value); written = true; }
        finally { p11$damageCredit = P11NativeOperationBoundary.fieldWritten(p11$damageCredit, replacement, written); }
    }

    @Override
    public P11NativeOperationBoundary.CreditScope p11$beginKillCredit() {
        var actor = lastHurtByPlayer != null ? lastHurtByPlayer : lastHurtByMob;
        return actor instanceof ServerPlayer player
                ? P11NativeOperationBoundary.beginCredit((LivingEntity) (Object) this, player,
                        lastHurtByPlayer != null ? p11$playerCredit : p11$mobCredit) : null;
    }

    @Override
    public P11NativeOperationBoundary.CreditScope p11$beginMobCredit(ServerPlayer exactFieldActor) {
        return lastHurtByMob == exactFieldActor
                ? P11NativeOperationBoundary.beginCredit((LivingEntity) (Object) this, exactFieldActor,
                        p11$mobCredit) : null;
    }

    @Override
    public void p11$beginNativeConsumers() {
        P11NativeOperationBoundary.beginCreditConsumers(p11$creditLifetime);
    }

    @Override
    public void p11$endNativeConsumers() {
        if (P11NativeOperationBoundary.endCreditConsumers(p11$creditLifetime)) { p11$releaseCreditFields(); }
    }

    @Override
    public void p11$nativeCreditRevived() {
        P11NativeOperationBoundary.creditRevived(p11$creditLifetime);
    }

    @Override
    public void p11$releaseCreditFields() {
        if (!P11NativeOperationBoundary.creditRemoval(p11$creditLifetime)) { return; }
        P11NativeOperationBoundary.releaseCredit(p11$playerCredit);
        P11NativeOperationBoundary.releaseCredit(p11$mobCredit);
        P11NativeOperationBoundary.releaseCredit(p11$damageCredit);
        p11$playerCredit = null; p11$mobCredit = null; p11$damageCredit = null;
        if (this instanceof P11NativeOperationBoundary.DragonCreditAccess dragon) {
            dragon.p11$releaseDragonCredit();
        }
    }

    @WrapMethod(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$deathConsumers(DamageSource source, Operation<Void> original) {
        p11$beginNativeConsumers();
        P11NativeOperationBoundary.CreditScope scope = null;
        boolean normal = false;
        try { scope = p11$beginKillCredit(); original.call(source); normal = true; }
        finally {
            try { P11NativeOperationBoundary.endCredit(scope, normal); }
            finally { p11$endNativeConsumers(); }
        }
    }
}
