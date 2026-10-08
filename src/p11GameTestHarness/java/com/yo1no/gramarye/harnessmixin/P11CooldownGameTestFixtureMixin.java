package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownGameTestHarness;
import com.yo1no.gramarye.P7S4LoginManaGameTests;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(P7S4LoginManaGameTests.class)
abstract class P11CooldownGameTestFixtureMixin {
    @WrapMethod(method = "directRuntime(Lcom/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService;Lcom/yo1no/gramarye/RuntimeExecutionPort;)Lcom/yo1no/gramarye/SkillRuntimeService;",
            require = 1, expect = 1, allow = 1)
    @Coerce
    private static Object p11$runtime(SkillDefinitionStoreService store,
            @Coerce Object port, Operation<Object> original) {
        return P11CooldownGameTestHarness.defaultRuntime(store, port);
    }

    @WrapOperation(method = "actualP9ReservedContinuationSurvivesRootAndClosesLateWithoutWorldEffects(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            at = @At(value = "NEW", target = "(Lcom/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService;Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionPolicyProvider;Lcom/yo1no/gramarye/P5RuntimeProjector;Lcom/yo1no/gramarye/RuntimeReferenceResolver;Lcom/yo1no/gramarye/RuntimeExecutionPort;)Lcom/yo1no/gramarye/SkillRuntimeService;"),
            require = 1, expect = 1, allow = 1)
    @Coerce
    private static Object p11$reservedRuntime(SkillDefinitionStoreService store,
            SkillSubmissionPolicyProvider policy, @Coerce Object projector,
            @Coerce Object resolver, @Coerce Object port,
            Operation<Object> original) {
        return P11CooldownGameTestHarness.runtime(store, policy, projector, resolver, port);
    }

    @WrapMethod(method = "cooldownAttachments(Lnet/minecraft/server/MinecraftServer;)Lcom/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService;",
            require = 1, expect = 1, allow = 1)
    private static PlayerSkillAttachmentService p11$attachments(MinecraftServer server,
            Operation<PlayerSkillAttachmentService> original) {
        return P11CooldownGameTestHarness.attachments(server);
    }

    @Coerce
    @WrapMethod(method = "nativeGameTestFacade(Lnet/minecraft/server/MinecraftServer;)Lcom/yo1no/gramarye/P4E2QualificationFacade;",
            require = 1, expect = 1, allow = 1)
    private static Object p11$facade(MinecraftServer server, Operation<Object> original) {
        return P11CooldownGameTestHarness.facade(server);
    }

    @WrapMethod(method = {
            "actualP9ReservedContinuationSurvivesRootAndClosesLateWithoutWorldEffects(Lnet/minecraft/gametest/framework/GameTestHelper;)V",
            "actualP9ActorWitnessRejectsRespawnDimensionAndLogoutBeforeTransfer(Lnet/minecraft/gametest/framework/GameTestHelper;)V"
    }, require = 2, expect = 2, allow = 2)
    private static void p11$schedule(GameTestHelper helper, Operation<Void> original) {
        P11CooldownGameTestHarness.schedule(helper, () -> original.call(helper));
    }
}
