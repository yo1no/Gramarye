package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(targets = "com.yo1no.gramarye.magic.network.P7AuthoritativeSyncService")
abstract class P11D3ResultMixin {
    @WrapMethod(method = "accept(Lcom/yo1no/gramarye/magic/network/P7ServerIntentResult;)V", require = 1, expect = 1, allow = 1)
    private void p11$d3Result(@Coerce Object result, Operation<Void> original) {
        P11D3Observation.result(result, () -> original.call(result));
    }
    @WrapOperation(method = "submit(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/magic/network/P7SessionIdentity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Lcom/yo1no/gramarye/magic/network/P7AuthoritativeSyncService$Submission;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/magic/network/P7AuthoritativeSyncService$Transport;submit(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$d3Submitted(@Coerce Object transport, ServerPlayer actor, CustomPacketPayload payload, Operation<Void> original) {
        original.call(transport, actor, payload); P11D3Observation.submitted(actor, payload);
    }
}
