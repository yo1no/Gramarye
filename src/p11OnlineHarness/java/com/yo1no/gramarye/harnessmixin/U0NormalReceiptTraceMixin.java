package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import java.util.UUID;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yo1no.gramarye.V0SlotRuntime$V0ReleaseReceipt")
abstract class U0NormalReceiptTraceMixin {
    @Shadow @Final private UUID attempt;
    @Shadow private long releasedAt;
    @Shadow private boolean revoked;
    @Inject(method = "beginPublication()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$begin(CallbackInfo callback) {
        try { U0OrdinaryTrace.note("PUBLICATION_UNKNOWN_RETURN", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, 0, 0, 0, revoked ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "published()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$pending(CallbackInfo callback) {
        try { U0OrdinaryTrace.note("PUBLICATION_PENDING_RETURN", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, 0, 0, 0, revoked ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "armed(J)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$arm(long tick, CallbackInfo callback) {
        try { U0OrdinaryTrace.note("ARM_RETURN", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, tick, 0, 0, revoked ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "revoke()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$revoke(CallbackInfo callback) {
        try { U0OrdinaryTrace.note("REVOKE_RETURN", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, 0, 0, 0, revoked ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "unknown()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$unknown(CallbackInfo callback) {
        try { U0OrdinaryTrace.note("UNKNOWN_RETURN", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, 0, 0, 0, revoked ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @Inject(method = "fact()Lcom/yo1no/gramarye/P11CastCooldownService$ReleaseFact;", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$fact(CallbackInfoReturnable<Object> callback) {
        try { U0OrdinaryTrace.note("RECEIPT_FACT", attempt.getMostSignificantBits(), attempt.getLeastSignificantBits(), releasedAt, 0, 0, 0, revoked ? 1 : 0, 0, 0, callback.getReturnValue() instanceof Enum<?> value ? value.name() : "INVALID_RESULT"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
}
