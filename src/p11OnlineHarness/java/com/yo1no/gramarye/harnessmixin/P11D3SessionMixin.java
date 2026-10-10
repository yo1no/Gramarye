package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ServerSessionService")
abstract class P11D3SessionMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/magic/network/P7ServerAccess;Lcom/yo1no/gramarye/magic/network/P7ReloadAdmissionGate;Lcom/yo1no/gramarye/magic/network/P7PendingPermitOwner;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$d3Owner(@Coerce Object access, @Coerce Object gate, @Coerce Object permits, CallbackInfo callback) {
        P11D3Observation.owner(this, permits);
    }
}
