package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
abstract class P11EntityCreditRemovalMixin {
    @WrapMethod(method = "setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$actualRemoval(Entity.RemovalReason reason, Operation<Void> original) {
        original.call(reason);
        // A throwing native teardown did not prove the terminal. Do not release at HEAD.
        if (this instanceof P11NativeOperationBoundary.CreditAccess credit) { credit.p11$releaseCreditFields(); }
        else if (this instanceof P11NativeOperationBoundary.DragonCreditAccess dragon) { dragon.p11$releaseDragonCredit(); }
    }
}
