package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0NormalCarrierObservation;
import com.yo1no.gramarye.U0OrdinaryTrace;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Qualification only: the original guard remains the sole decision. */
@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class U0CurrentActorTraceMixin {
    @WrapMethod(method = "currentV0Actor(Lcom/yo1no/gramarye/V0SlotRuntime;Lcom/yo1no/gramarye/V0CatalogAuthority;Lcom/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary$V0PlayWitness;Lcom/yo1no/gramarye/P11QualifiedSourceOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11ReceiptLedger$Source;Lnet/minecraft/server/level/ServerPlayer;)Z", require = 1, expect = 1, allow = 1)
    private boolean u0$current(@Coerce Object v0, @Coerce Object authority, @Coerce Object witness,
            @Coerce Object source, @Coerce Object body, @Coerce Object proof, @Coerce Object actor,
            Operation<Boolean> original) {
        boolean actual = original.call(v0, authority, witness, source, body, proof, actor);
        try { U0NormalCarrierObservation.actorCurrent(source, body, proof, actor, actual); }
        catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original result remains primary. */ }
        }
        return actual;
    }
}
