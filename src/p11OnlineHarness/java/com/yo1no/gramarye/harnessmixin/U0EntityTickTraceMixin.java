package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.U0OrdinaryTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Ordinary-artifact profile is pinned separately; no normal/legacy policy is changed here. */
@Mixin(targets = "com.yo1no.gramarye.P9StarterProjectile")
abstract class U0EntityTickTraceMixin {
    @WrapMethod(method = "tickV0()V", require = 1, expect = 1, allow = 1)
    private void u0$entity(Operation<Void> original) {
        try { if (((Entity) (Object) this).level() instanceof ServerLevel level) { U0OrdinaryTrace.nativeTick(level.getServer().getTickCount()); } U0OrdinaryTrace.enter("ENTITY", -1); U0OrdinaryTrace.note("ENTITY_TICK", ((Entity) (Object) this).getId(), 0, 0, 0, 0, 0, 0, 0, 0, ""); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        }
        try { original.call(); }
        finally { try { U0OrdinaryTrace.leave("ENTITY"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        } }
    }
}
