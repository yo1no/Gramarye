package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1ImpactCustodyProbe;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.SkillRuntimeService")
abstract class P11L1ImpactClaimMixin {
    @WrapMethod(method = "claimObservedProjectileHit(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/RuntimeProjectileContinuationPermit;Lcom/yo1no/gramarye/P9StarterProjectile;Lcom/yo1no/gramarye/ProjectileHitCandidateV0;)Ljava/util/Optional;",
            require = 1, expect = 1, allow = 1)
    private Optional<?> l1$actualObservedClaim(MinecraftServer server, @Coerce Object permit,
            @Coerce Object projectile, @Coerce Object candidate, Operation<Optional<?>> original) {
        var result = original.call(server, permit, projectile, candidate);
        P11L1ImpactCustodyProbe.claimReturned(this, server, permit, projectile, candidate, result);
        return result;
    }
}
