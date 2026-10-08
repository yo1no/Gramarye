package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.magic.network.P11CooldownInputObservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ClientMirror")
abstract class P11CooldownClientMirrorMixin {
    @Inject(method = "onSkillCooldownSnapshot(JLcom/yo1no/gramarye/magic/network/SkillCooldownSnapshot;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownMirror(long generation, @Coerce Object snapshot, CallbackInfo callback) {
        P11CooldownInputObservation.mirror(this, generation, snapshot);
    }
    @Inject(method = "onDisconnected()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownMirrorCleared(CallbackInfo callback) {
        P11CooldownInputObservation.disconnected(this);
    }
}
