package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.magic.network.P11L1InputObservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ClientMirror")
abstract class P11L1ClientMirrorMixin {
    @Inject(method = "onPlayerManaSnapshot(JLcom/yo1no/gramarye/magic/network/PlayerManaSnapshot;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Mana(long generation, @Coerce Object snapshot, CallbackInfo callback) {
        P11L1InputObservation.mirror(this, generation, snapshot);
    }
    @Inject(method = "onSkillCooldownSnapshot(JLcom/yo1no/gramarye/magic/network/SkillCooldownSnapshot;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Cooldown(long generation, @Coerce Object snapshot, CallbackInfo callback) {
        P11L1InputObservation.mirror(this, generation, snapshot);
    }
}
