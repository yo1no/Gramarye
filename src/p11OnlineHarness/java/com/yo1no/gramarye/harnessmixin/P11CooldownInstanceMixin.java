package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownServerHarness;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.ServerSlot$InstanceState")
abstract class P11CooldownInstanceMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/magic/api/id/SkillInstanceId;Lcom/yo1no/gramarye/RuntimeSkillInstanceSequence;Lcom/yo1no/gramarye/RuntimeBudgetAttribution;Lcom/yo1no/gramarye/RuntimeRevisionLease;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownInstance(@Coerce Object id, @Coerce Object sequence, @Coerce Object attribution,
            @Coerce Object lease, ServerPlayer actor, CallbackInfo callback) {
        P11CooldownServerHarness.instance(actor, this);
    }
}
