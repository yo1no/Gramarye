package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.magic.network.P11D3ClientObservation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ClientMirror")
abstract class P11D3ClientMirrorMixin {
    @Inject(method = "onIntentAcknowledgement(JLcom/yo1no/gramarye/magic/network/IntentAcknowledgement;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$d3Ack(long generation, @Coerce Object ack, CallbackInfo callback) {
        P11D3ClientObservation.acknowledgement(this, generation, ack);
    }
}
