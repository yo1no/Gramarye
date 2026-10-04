package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1WorkRewardProbe;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AdvancementRewards.class)
abstract class P11L1WorkRewardMixin {
    @WrapMethod(method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V", require = 1, expect = 1, allow = 1)
    private void p11$l1WholeGrant(ServerPlayer actor, Operation<Void> original) {
        int role = P11L1WorkRewardProbe.grantEntered((AdvancementRewards) (Object) this);
        boolean normal = false; Throwable primary = null;
        try { original.call(actor); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1WorkRewardProbe.grantFinished(role, normal, primary); }
    }
    @WrapOperation(method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;giveExperiencePoints(I)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$l1ActualWholeRecipient(ServerPlayer actor, int amount, Operation<Void> original) {
        P11L1WorkRewardProbe.rewardExperience((AdvancementRewards) (Object) this, actor, amount);
        original.call(actor, amount);
    }
}
