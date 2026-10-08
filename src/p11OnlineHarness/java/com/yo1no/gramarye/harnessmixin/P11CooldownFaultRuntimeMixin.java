package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11CooldownFaultProbe;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11CooldownFaultRuntimeMixin {
    @Coerce
    @WrapMethod(method = "transferSpawnedProjectile(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Ljava/util/UUID;Lcom/yo1no/gramarye/P9StarterProjectile;)Lcom/yo1no/gramarye/RuntimePermitTransferDisposition;",
            require = 1, expect = 1, allow = 1)
    private Object p11$cooldownTransfer(MinecraftServer server, @Coerce Object permit, UUID planned,
            @Coerce Object projectile, Operation<Object> original) {
        Object result = null; Throwable primary = null;
        try { result = original.call(server, permit, planned, projectile); return result; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11CooldownFaultProbe.transferReturned(projectile, result, primary); }
    }
    @WrapMethod(method = "preserveRuntimeFault(Lcom/yo1no/gramarye/ServerSlot;Ljava/lang/RuntimeException;)Ljava/lang/RuntimeException;",
            require = 1, expect = 1, allow = 1)
    private RuntimeException p11$cooldownFault(@Coerce Object slot, RuntimeException primary, Operation<RuntimeException> original) {
        P11CooldownFaultProbe.runtimeFault(slot, primary, false, null);
        var result = original.call(slot, primary);
        P11CooldownFaultProbe.runtimeFault(slot, primary, true, result);
        return result;
    }
    @WrapMethod(method = "handleRuntimePost(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$cooldownOriginalPost(ServerTickEvent.Post event, Operation<Void> original) {
        try { original.call(event); }
        catch (RuntimeException | Error primary) {
            P11CooldownFaultProbe.postThrew(event.getServer(), primary); throw primary;
        }
    }
}
