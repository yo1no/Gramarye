package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import com.yo1no.gramarye.P11ClientTransitions;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.magic.network.U0NormalInputObservation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P9ClientCastInput")
abstract class U0NormalInputTraceMixin {
    @Shadow private boolean keyMappingRegistered;
    @Shadow private boolean senderSessionAvailable;
    @Shadow private boolean pendingFlushRequired;
    @WrapMethod(method = "handleClientPostTick()V", require = 1, expect = 1, allow = 1)
    private void u0$post(Operation<Void> original) {
        try { U0OrdinaryTrace.enter("INPUT_POST", -1); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        try {
            original.call();
            try { var client = Minecraft.getInstance(); long gates = (keyMappingRegistered ? 1 : 0) | (senderSessionAvailable ? 2 : 0) | (pendingFlushRequired ? 4 : 0) | (client.level != null ? 8 : 0) | (client.player != null ? 16 : 0) | (client.getConnection() != null ? 32 : 0) | (client.screen == null ? 64 : 0) | (client.isWindowActive() ? 128 : 0) | (!client.isPaused() ? 256 : 0) | (P11ClientTransitions.blocksCast() ? 512 : 0); U0OrdinaryTrace.note("CLIENT_GATE", gates, P11ClientTransitions.inputSuppressionGeneration(), 0, 0, 0, 0, 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        } finally { try { U0OrdinaryTrace.leave("INPUT_POST"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        } }
    }
    @Inject(method = "sendPayload(Lcom/yo1no/gramarye/magic/network/CastCurrentSpellPayload;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void u0$sent(@Coerce Object payload, CallbackInfo callback) {
        try { U0NormalInputObservation.submitted(payload); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
    }
}
