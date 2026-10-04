package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the actual original constructor's completed object is observed; never constructed here. */
@Mixin(targets = "com.yo1no.gramarye.ServerSlot$InstanceState")
abstract class P11L1InstanceMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/magic/api/id/SkillInstanceId;Lcom/yo1no/gramarye/RuntimeSkillInstanceSequence;Lcom/yo1no/gramarye/RuntimeBudgetAttribution;Lcom/yo1no/gramarye/RuntimeRevisionLease;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Instance(@Coerce Object id, @Coerce Object sequence, @Coerce Object attribution,
            @Coerce Object lease, ServerPlayer actor, CallbackInfo callback) {
com.yo1no.gramarye.P11L1HostStopProbe.instance(actor, this);
P11L1ServerHarness.instanceCreated(actor, this);
    }
}
