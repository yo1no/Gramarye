package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.U0OrdinaryTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Counts only actual calls to the one locked leaf while an ordinary dispatch/entity span is active. */
@Mixin(targets = "net.minecraft.server.level.DistanceManager")
abstract class U0DistanceTicketTraceMixin {
    @WrapMethod(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", require = 1, expect = 1, allow = 1)
    private void u0$ticket(long chunk, @Coerce Object ticket, Operation<Void> original) {
        boolean returned = false;
        try { original.call(chunk, ticket); returned = true; }
        finally { try { U0OrdinaryTrace.note("NATIVE_TICKET_ADD", chunk, 0, 0, 0, 0, 0, 0, 0, 0, returned ? "RETURN" : "THROW"); } catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { /* Original gameplay retains its primary. */ }
        } }
    }
}
