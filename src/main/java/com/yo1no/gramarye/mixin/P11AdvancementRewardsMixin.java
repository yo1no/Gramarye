package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AdvancementRewards.class)
abstract class P11AdvancementRewardsMixin {
    @WrapMethod(method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$wholeReward(ServerPlayer actor, Operation<Void> original) {
        P11NativeOperationBoundary.grant((AdvancementRewards) (Object) this, actor, original);
    }
}
