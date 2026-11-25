package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads original scalar arguments even for NOOP; does not bind another A05 observer. */
@Mixin(targets = "com.yo1no.gramarye.A05Observer")
abstract class U0A05ScalarTraceMixin {
    @Inject(method = "receipt(IJJJJJILjava/lang/String;Ljava/lang/String;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void u0$receipt(int nativeTick, long runtimeTick, long instance, long lineage, long request,
            long event, int ordinal, String terminal, String reason, CallbackInfo callback) {
        try { U0OrdinaryTrace.note("P6_RECEIPT", instance, lineage, request, event, runtimeTick, 0, ordinal, nativeTick, 0, terminal); U0OrdinaryTrace.note("P6_REASON", instance, lineage, request, event, runtimeTick, 0, ordinal, nativeTick, 0, reason); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "offer(Ljava/lang/String;IJJJJILjava/lang/String;JJIIILjava/lang/String;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void u0$offer(String phase, int nativeTick, long runtimeTick, long instance, long request,
            long event, int ordinal, String kind, long generation, long sequence, int selected,
            int candidates, int admitted, String outcome, CallbackInfo callback) {
        try { U0OrdinaryTrace.note("P8_" + phase, instance, request, event, generation, sequence, runtimeTick, ordinal, selected, candidates, kind + ":" + outcome); U0OrdinaryTrace.note("P8_COUNTS", generation, sequence, admitted, nativeTick, runtimeTick, instance, 0, 0, 0, phase); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "submission(IJJLjava/lang/String;JLjava/lang/String;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void u0$submission(int nativeTick, long generation, long sequence, String kind,
            long connectionEpoch, String outcome, CallbackInfo callback) {
        try { U0OrdinaryTrace.note("P8_SUBMISSION", generation, sequence, connectionEpoch, nativeTick, 0, 0, 0, 0, 0, kind + ":" + outcome); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
}
