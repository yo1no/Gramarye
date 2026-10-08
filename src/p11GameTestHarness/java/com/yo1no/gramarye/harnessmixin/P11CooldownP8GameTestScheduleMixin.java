package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownGameTestHarness;
import com.yo1no.gramarye.P8S3PresentationGameTests;
import net.minecraft.gametest.framework.GameTestHelper;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(P8S3PresentationGameTests.class)
abstract class P11CooldownP8GameTestScheduleMixin {
    @WrapMethod(method = {
            "appliedFactHandoffRollsBackFailuresAndDrainsCurrentTick(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "hitRecipientsUseActualWatchersAndRevalidateCurrentIdentity(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "productionTransportEncodesCatalogBeforePresentationEvent(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "catalogFailurePoliciesUseActualDrainAndBoundedAccounting(Lnet/minecraft/gametest/framework/GameTestHelper;)V"
    }, require = 4, expect = 4, allow = 4)
    private static void p11$schedule(GameTestHelper helper, Operation<Void> original) {
        P11CooldownGameTestHarness.schedule(helper, () -> original.call(helper));
    }
}
