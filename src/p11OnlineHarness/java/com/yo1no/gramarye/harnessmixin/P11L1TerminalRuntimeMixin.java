package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1TerminalBoundaryProbe;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1TerminalRuntimeMixin {
    @Coerce
    @WrapMethod(method = "transferSpawnedProjectile(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Ljava/util/UUID;Lcom/yo1no/gramarye/P9StarterProjectile;)Lcom/yo1no/gramarye/RuntimePermitTransferDisposition;",
            require = 1, expect = 1, allow = 1)
    private Object l1$allOriginalTransferReturns(MinecraftServer server, @Coerce Object permit, UUID planned,
            @Coerce Object projectile, Operation<Object> original) {
        Object result = null; Throwable primary = null;
        try { result = original.call(server, permit, planned, projectile); return result; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1TerminalBoundaryProbe.transferReturned(projectile, result, primary); }
    }
    @WrapMethod(method = "preserveRuntimeFault(Lcom/yo1no/gramarye/ServerSlot;Ljava/lang/RuntimeException;)Ljava/lang/RuntimeException;",
            require = 1, expect = 1, allow = 1)
    private RuntimeException l1$originalRuntimeFault(@Coerce Object slot, RuntimeException primary, Operation<RuntimeException> original) {
        P11L1TerminalBoundaryProbe.runtimeFault(slot, primary, false, null);
        var returned = original.call(slot, primary);
        P11L1TerminalBoundaryProbe.runtimeFault(slot, primary, true, returned);
        return returned;
    }
    @WrapMethod(method = "handleRuntimePost(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V",
            require = 1, expect = 1, allow = 1)
    private void l1$originalPost(ServerTickEvent.Post event, Operation<Void> original) {
        try { original.call(event); }
        catch (RuntimeException | Error primary) {
            P11L1TerminalBoundaryProbe.postThrew(event.getServer(), primary); throw primary;
        }
    }
    @WrapMethod(method = "sweepActiveProjectileContinuations(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/ServerSlot;)V",
            require = 1, expect = 1, allow = 1)
    private static void l1$originalSweep(MinecraftServer server, @Coerce Object slot, Operation<Void> original) {
        boolean selected = P11L1TerminalBoundaryProbe.sweepEntering(server, slot); Throwable primary = null;
        try { original.call(server, slot); }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1TerminalBoundaryProbe.sweepReturned(selected, primary); }
    }
}
