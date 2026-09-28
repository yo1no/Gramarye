package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeRewardProbe;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Excluded fault after the actual loot insertion and its original menu publication. */
@Mixin(AdvancementRewards.class)
abstract class P11RewardLootFaultMixin {
    @WrapOperation(method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;broadcastChanges()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$lootPrefix(AbstractContainerMenu menu, Operation<Void> original, ServerPlayer actor) {
        original.call(menu);
        P11NativeRewardProbe.afterLootMenu(actor);
    }
}
