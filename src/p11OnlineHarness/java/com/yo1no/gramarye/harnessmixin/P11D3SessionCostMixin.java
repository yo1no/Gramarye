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
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7ServerSessionService")
abstract class P11D3SessionCostMixin {
    @Coerce
    @WrapMethod(method = "open(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;)Lcom/yo1no/gramarye/magic/network/P7ServerSessionService$OpenResult;", require = 1, expect = 1, allow = 1)
    private Object p11$d3Open(MinecraftServer server, ServerPlayer actor, Operation<Object> original) {
        long start = P11D3Observation.costStart(this); boolean normal = false; Object result = null;
        try { result = original.call(server, actor); normal = true; return result; }
        finally { P11D3Observation.costEnd(0, start, normal, result); }
    }
    @Coerce
    @WrapMethod(method = "captureAuthenticatedSession(Ljava/util/UUID;Lnet/minecraft/network/Connection;)Lcom/yo1no/gramarye/magic/network/P7ConnectionEpochSnapshotSource$CaptureResult;", require = 1, expect = 1, allow = 1)
    private Object p11$d3Capture(UUID id, Connection connection, Operation<Object> original) {
        long start = P11D3Observation.costStart(this); boolean normal = false; Object result = null;
        try { result = original.call(id, connection); normal = true; return result; }
        finally { P11D3Observation.costEnd(1, start, normal, result); }
    }
    @WrapMethod(method = "isCurrentCapture(Lcom/yo1no/gramarye/magic/network/P7SessionIdentity;Lnet/minecraft/network/Connection;)Z", require = 1, expect = 1, allow = 1)
    private boolean p11$d3Recheck(@Coerce Object identity, Connection connection, Operation<Boolean> original) {
        long start = P11D3Observation.costStart(this); boolean normal = false;
        try { boolean result = original.call(identity, connection); normal = true; return result; }
        finally { P11D3Observation.costEnd(2, start, normal, null); }
    }
    @WrapMethod(method = "stop(Lnet/minecraft/server/MinecraftServer;)I", require = 1, expect = 1, allow = 1)
    private int p11$d3Stop(MinecraftServer server, Operation<Integer> original) {
        long start = P11D3Observation.costStart(this); boolean normal = false;
        try { int result = original.call(server); normal = true; return result; }
        finally { P11D3Observation.costEnd(4, start, normal, null); }
    }
}
