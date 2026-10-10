package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7PendingPermitOwner")
abstract class P11D3PermitCostMixin {
    @Coerce
    @WrapMethod(method = "acquire(Ljava/util/UUID;JJ)Lcom/yo1no/gramarye/magic/network/P7PendingPermitOwner$AcquireResult;", require = 1, expect = 1, allow = 1)
    private Object p11$d3Acquire(UUID id, long epoch, long generation, Operation<Object> original) {
        long start = P11D3Observation.costStart(this); boolean normal = false; Object result = null;
        try { result = original.call(id, epoch, generation); normal = true; return result; }
        finally { P11D3Observation.costEnd(3, start, normal, result); }
    }
}
