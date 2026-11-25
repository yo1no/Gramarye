package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.U0BoundedEntityQuery$Scan")
abstract class U0BoundedScanTraceMixin {
    @Shadow @Final boolean complete;
    @Shadow @Final int rawCount;
    @Shadow @Final int sectionLookups;
    @Inject(method = "<init>(ZIILjava/util/List;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$scan(CallbackInfo callback) {
        try { U0OrdinaryTrace.note("BOUNDED_SCAN_RETURN", rawCount, sectionLookups, 0, 0, 0, 0, complete ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
}
