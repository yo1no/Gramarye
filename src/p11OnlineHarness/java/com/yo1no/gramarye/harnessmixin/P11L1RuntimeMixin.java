package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ServerHarness;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1RuntimeMixin {
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "handleRuntimeStopping(Lnet/neoforged/neoforge/event/server/ServerStoppingEvent;)V", require = 1, expect = 1, allow = 1)
    private void l1$hostStop(net.neoforged.neoforge.event.server.ServerStoppingEvent event,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        var server = event.getServer();
        com.yo1no.gramarye.P11L1HostStopProbe.boundary(server, null, true, false, false);
        boolean normal = false;
        try { original.call(event); normal = true; }
        finally { com.yo1no.gramarye.P11L1HostStopProbe.boundary(server, null, true, true, normal); }
    }
    @Inject(method = "admitAuthenticatedPlayerCast(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/magic/definition/document/SkillReference;Lcom/yo1no/gramarye/CastGeometryExecutionDataV0;)Lcom/yo1no/gramarye/RuntimeAdmissionResult;",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1Accepted(MinecraftServer server, ServerPlayer actor, SkillReference reference,
            @Coerce Object geometry, CallbackInfoReturnable<Object> callback) {
com.yo1no.gramarye.P11L1HostStopProbe.accepted(server, actor, geometry, callback.getReturnValue());
P11L1ServerHarness.accepted(this, server, actor, geometry, callback.getReturnValue());
    }

    @Inject(method = "transferSpawnedProjectile(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Ljava/util/UUID;Lcom/yo1no/gramarye/P9StarterProjectile;)Lcom/yo1no/gramarye/RuntimePermitTransferDisposition;",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$l1Transferred(MinecraftServer server, @Coerce Object permit, UUID planned,
            @Coerce Object projectile, CallbackInfoReturnable<Object> callback) {
com.yo1no.gramarye.P11L1HostStopProbe.transferred(projectile, callback.getReturnValue());
P11L1ServerHarness.transferred(projectile, callback.getReturnValue());
    }

    @Inject(method = "recordP9HitClaimResult(Lcom/yo1no/gramarye/ServerSlot;Lcom/yo1no/gramarye/ServerSlot$InstanceState;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Lcom/yo1no/gramarye/ProjectileHitCandidateV0;Lcom/yo1no/gramarye/RuntimePermitClaimDisposition;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void p11$l1Claim(@Coerce Object slot, @Coerce Object instance,
            @Coerce Object permit, @Coerce Object hit, @Coerce Object disposition, CallbackInfo callback) {
        P11L1ServerHarness.claimed(disposition);
    }
}
