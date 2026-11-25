package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.U0OrdinaryTrace;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the sole ordinary native callback; unrelated events never imply applied gameplay. */
@Mixin(targets = "com.yo1no.gramarye.P9V0WorldEffectHandoff")
abstract class U0TrueDamagePostTraceMixin {
    @WrapMethod(method = "observeV0DamagePost(Lnet/neoforged/neoforge/event/entity/living/LivingDamageEvent$Post;)V", require = 1, expect = 1, allow = 1)
    private static void u0$post(LivingDamageEvent.Post event, Operation<Void> original) {
        try { original.call(event); }
        finally {
            try { U0OrdinaryTrace.note("TRUE_POST_CALLBACK", event.getEntity().getId(), Float.floatToRawIntBits(event.getNewDamage()), Float.floatToRawIntBits(event.getReduction(DamageContainer.Reduction.ABSORPTION)), 0, 0, 0, event.getEntity().level().isClientSide() ? 1 : 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        }
    }
}
