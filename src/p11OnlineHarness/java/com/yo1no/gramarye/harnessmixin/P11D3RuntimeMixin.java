package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11D3ServerHarness;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.IdentityHashMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11D3RuntimeMixin {
    @Shadow @Final private IdentityHashMap<MinecraftServer, ?> slots;
    @Inject(method = "handleRuntimeStarted(Lnet/neoforged/neoforge/event/server/ServerStartedEvent;Lcom/yo1no/gramarye/P5RuntimeLimits;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$d3Started(ServerStartedEvent event, @Coerce Object limits, CallbackInfo callback) {
        P11D3ServerHarness.runtimeStarted(event.getServer(), slots.get(event.getServer()));
    }
    @Inject(method = "admitAuthenticatedPlayerCast(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/magic/definition/document/SkillReference;Lcom/yo1no/gramarye/CastGeometryExecutionDataV0;)Lcom/yo1no/gramarye/RuntimeAdmissionResult;",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$d3Admitted(MinecraftServer server, ServerPlayer actor, SkillReference reference, @Coerce Object geometry, CallbackInfoReturnable<Object> callback) {
        P11D3ServerHarness.admitted(server, actor, reference, callback.getReturnValue());
    }
    @Inject(method = "transferSpawnedProjectile(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Ljava/util/UUID;Lcom/yo1no/gramarye/P9StarterProjectile;)Lcom/yo1no/gramarye/RuntimePermitTransferDisposition;",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void p11$d3Transferred(MinecraftServer server, @Coerce Object permit, UUID planned, @Coerce Object projectile, CallbackInfoReturnable<Object> callback) {
        P11D3ServerHarness.transferred(callback.getReturnValue());
    }
    @Inject(method = "recordP9HitClaimResult(Lcom/yo1no/gramarye/ServerSlot;Lcom/yo1no/gramarye/ServerSlot$InstanceState;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Lcom/yo1no/gramarye/ProjectileHitCandidateV0;Lcom/yo1no/gramarye/RuntimePermitClaimDisposition;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void p11$d3Claimed(@Coerce Object slot, @Coerce Object instance, @Coerce Object permit, @Coerce Object hit, @Coerce Object disposition, CallbackInfo callback) {
        P11D3ServerHarness.claimed(disposition);
    }
}
