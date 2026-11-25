package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService$V0CarrierBinding")
abstract class U0NormalCarrierTraceMixin {
    @Shadow private boolean openPublished;
    @Shadow @Final boolean child;
    @Inject(method = "publishOpen()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$open(CallbackInfo callback) {
        try { com.yo1no.gramarye.U0OrdinaryLogoutScenario.opened(this); U0OrdinaryTrace.note("OPEN_RETURN", 0, 0, 0, 0, 0, 0, openPublished ? 1 : 0, child ? 1 : 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
}
