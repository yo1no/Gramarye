package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownGameTestHarness;
import com.yo1no.gramarye.P9S3ProjectileGameTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(P9S3ProjectileGameTests.class)
abstract class P11CooldownP9GameTestScheduleMixin {
    @WrapMethod(method = "assertZeroCooldownBeforeSpawn(Lcom/yo1no/gramarye/SkillRuntimeService;Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/RuntimeAdmissionResult$AcceptedMemoryOnly;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$zero(@Coerce Object runtime, MinecraftServer server,
            ServerPlayer actor, @Coerce Object accepted, Operation<Void> original) {
        P11CooldownGameTestHarness.zeroCooldown(runtime, server, actor, accepted);
    }

    @WrapMethod(method = {
            "realSpawnTransferHitAndNextDrainUseTheHeldChild(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "falseAndThrowingInsertionNeverOpenOrPublish(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "cancelledSweepContinuesAndInvalidImpactsTerminal(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "reservedClaimAndWrongTransferWitnessesAreOneShot(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "replacementRemovalAndDeadlineCloseWithoutDamage(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "sixteenthOpenPermitIsThePerPlayerMaximum(Lnet/minecraft/gametest/framework/GameTestHelper;)V"
    }, require = 6, expect = 6, allow = 6)
    private static void p11$schedule(GameTestHelper helper, Operation<Void> original) {
        P11CooldownGameTestHarness.schedule(helper, () -> original.call(helper));
    }
}
