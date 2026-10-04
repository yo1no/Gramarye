package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ServerAuthorizationDispatcher")
abstract class P11L1DispatchMixin {
    // The final normal exit follows root dispatch + its original resultSink.accept.
    @Inject(method = "dispatch(Lcom/yo1no/gramarye/magic/network/P7QueuedCastIntent;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1OriginalDispatchReturned(CallbackInfo callback) { P11L1ServerHarness.dispatchReturned(); }
}
