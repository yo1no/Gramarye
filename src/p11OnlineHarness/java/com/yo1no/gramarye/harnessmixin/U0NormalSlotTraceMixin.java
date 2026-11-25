package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import com.yo1no.gramarye.U0NormalCarrierObservation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.V0SlotRuntime")
abstract class U0NormalSlotTraceMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/V0SlotStartup;Lcom/yo1no/gramarye/V0SlotBinding;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void u0$constructed(@Coerce Object startup, @Coerce Object binding, CallbackInfo callback) {
        try { com.yo1no.gramarye.U0OrdinaryScenario.captureRuntime(this, startup, binding); }
        catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original constructor result remains primary. */ }
        }
    }

    @Coerce
    @WrapMethod(method = "acceptCompletion(Lcom/yo1no/gramarye/V0CaptureCompletion;J)Lcom/yo1no/gramarye/V0Admission;", require = 1, expect = 1, allow = 1)
    private Object u0$accept(@Coerce Object completion, long runtimeTick, Operation<Object> original) {
        try { U0OrdinaryTrace.enter("ACCEPT", runtimeTick); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        try {
            Object actual = original.call(completion, runtimeTick);
            try { com.yo1no.gramarye.U021FutureCompletionBridge.observeOriginalReturn(completion, actual); }
            catch (RuntimeException | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE",0,0,0,0,0,0,0,0,0,""); }
                catch (RuntimeException | Error recorderFailure) { /* Original return is preserved. */ }
            }
            try { U0OrdinaryTrace.note("ACCEPT_RETURN", 0, 0, 0, 0, 0, 0, 0, 0, 0, actual instanceof Enum<?> value ? value.name() : "INVALID_RESULT"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
            return actual;
        } finally { try { U0OrdinaryTrace.leave("ACCEPT"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        } }
    }
    @WrapMethod(method = "dispatch(IJLcom/yo1no/gramarye/V0P6StatementAdapter;)V", require = 1, expect = 1, allow = 1)
    private void u0$dispatch(int cell, long runtimeTick, @Coerce Object adapter, Operation<Void> original) {
        try { U0OrdinaryTrace.enter("DISPATCH", runtimeTick); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        try { original.call(cell, runtimeTick, adapter); }
        finally { try { U0OrdinaryTrace.leave("DISPATCH"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        } }
    }
    @WrapOperation(method = "acceptCompletion(Lcom/yo1no/gramarye/V0CaptureCompletion;J)Lcom/yo1no/gramarye/V0Admission;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11CastCooldownService;installPending(Lcom/yo1no/gramarye/P11CastCooldownService$Prepared;)Z"), require = 1, expect = 1, allow = 1)
    private boolean u0$install(@Coerce Object service, @Coerce Object prepared, Operation<Boolean> original) {
        boolean actual = original.call(service, prepared);
        try { U0OrdinaryTrace.note(actual ? "INSTALL_TRUE" : "INSTALL_FALSE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        return actual;
    }
    @Coerce
    @WrapOperation(method = "acceptCompletion(Lcom/yo1no/gramarye/V0CaptureCompletion;J)Lcom/yo1no/gramarye/V0Admission;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11CastCooldownService;claimPendingSource(Lcom/yo1no/gramarye/P11CastCooldownService$Prepared;Lcom/yo1no/gramarye/P11QualifiedSourceOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11ReceiptLedger$Source;)Lcom/yo1no/gramarye/P11ReceiptLedger$Source;"), require = 1, expect = 1, allow = 1)
    private Object u0$handoff(@Coerce Object service, @Coerce Object prepared, @Coerce Object source,
            @Coerce Object body, @Coerce Object proof, Operation<Object> original) {
        Object actual = original.call(service, prepared, source, body, proof);
        try { U0OrdinaryTrace.note(actual == null ? "SOURCE_HANDOFF_NULL" : "SOURCE_HANDOFF_NONNULL", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        return actual;
    }
    @Coerce
    @WrapOperation(method = "acceptCompletion(Lcom/yo1no/gramarye/V0CaptureCompletion;J)Lcom/yo1no/gramarye/V0Admission;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog;compareAndAccept(Lcom/yo1no/gramarye/V0CatalogAuthority$Reservation;Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog$Candidate;)Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog$Acceptance;"), require = 1, expect = 1, allow = 1)
    private Object u0$cas(@Coerce Object catalog, @Coerce Object reservation, @Coerce Object candidate, Operation<Object> original) {
        Object actual = original.call(catalog, reservation, candidate);
        try { U0OrdinaryTrace.note(actual instanceof Enum<?> value ? "CAS_" + value.name() : "INVALID_CAS_RESULT", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        return actual;
    }
    @Inject(method = "nativeCarrierTerminal(Lcom/yo1no/gramarye/SkillRuntimeService$V0CarrierBinding;Lcom/yo1no/gramarye/V0TerminalReason;)V",
            at = @At("RETURN"), require = 4, expect = 4, allow = 4)
    private void u0$terminal(@Coerce Object carrier, @Coerce Object reason, CallbackInfo callback) {
        try { U0NormalCarrierObservation.closed(carrier, reason); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
    @WrapMethod(method = "endNormalLogout(Lcom/yo1no/gramarye/SkillRuntimeService$NormalLogoutScope;Z)V", require = 1, expect = 1, allow = 1)
    private void u0$normalLogoutProof(@Coerce Object scope, boolean proved, Operation<Void> original) {
        original.call(scope, proved);
        try { com.yo1no.gramarye.U0OrdinaryLogoutScenario.proofReturned(proved); }
        catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original terminal remains primary. */ }
        }
    }


}
