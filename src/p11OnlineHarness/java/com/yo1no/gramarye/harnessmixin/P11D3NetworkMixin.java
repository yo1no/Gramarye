package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.network.Connection;
import java.util.UUID;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7CastIntentNetworkHandler")
abstract class P11D3NetworkMixin {
    @WrapMethod(method = "handle(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/yo1no/gramarye/magic/network/P7NetworkComposition;)V",
            require = 1, expect = 1, allow = 1)
    private static void p11$d3Source(@Coerce Object payload, IPayloadContext context, @Coerce Object composition, Operation<Void> original) {
        P11D3Observation.network(payload, context, () -> original.call(payload, context, composition));
    }
    @Coerce
    @WrapOperation(method = "handleAuthenticated(Lcom/yo1no/gramarye/magic/network/CastIntentPayload;Ljava/util/UUID;Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lcom/yo1no/gramarye/magic/network/P7NetworkComposition;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/magic/network/P7ConnectionEpochSnapshotSource;captureAuthenticatedSession(Ljava/util/UUID;Lnet/minecraft/network/Connection;)Lcom/yo1no/gramarye/magic/network/P7ConnectionEpochSnapshotSource$CaptureResult;"),
            require = 1, expect = 1, allow = 1)
    private static Object p11$d3ActualCapture(@Coerce Object source, UUID id, Connection connection, Operation<Object> original) {
        Object result = original.call(source, id, connection);
        P11D3Observation.handlerCaptured(id, connection, result); return result;
    }
}
