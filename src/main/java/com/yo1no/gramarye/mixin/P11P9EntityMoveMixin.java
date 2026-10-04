package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11P9TrackingCleanup;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
abstract class P11P9EntityMoveMixin {
    @Shadow @Final private EntityAccess entity;

    @WrapMethod(method = "onMove()V", require = 1, expect = 1, allow = 1)
    private void p11$trackingTerminal(Operation<Void> original) {
        P11P9TrackingCleanup.moved(entity, original);
    }
}
