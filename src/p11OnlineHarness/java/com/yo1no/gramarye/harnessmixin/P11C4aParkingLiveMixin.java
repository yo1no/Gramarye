package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aParkingProbe;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the real Live service's K, not the independent data-resource diagnostic. */
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aParkingLiveMixin {
    @Shadow private int waiting;
    @Shadow @Final private Map<?, ?> entries;

    @Inject(method = "service(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$beforeQuantum(@Coerce Object value, CallbackInfo callback) { c4a$observe(value); }

    @Inject(method = "service(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c4a$afterQuantum(@Coerce Object value, CallbackInfo callback) { c4a$observe(value); }

    @Unique private void c4a$observe(Object value) {
        var entry = (P11C4aParkingProbe.EntryAccess) value;
        int actualK;
        boolean actuallyWaiting;
        synchronized (entries) {
            actualK = waiting;
            actuallyWaiting = entry.c4a$parkingWaiting();
        }
        P11C4aParkingProbe.liveScalars(entry.c4a$parkingConnection(), actualK, actuallyWaiting);
    }
}
