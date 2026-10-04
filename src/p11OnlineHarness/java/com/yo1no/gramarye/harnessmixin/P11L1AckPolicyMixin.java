package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11L1PacketProbe;
import com.yo1no.gramarye.magic.network.P11L1AckObservation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.magic.network.P7AuthoritativeSyncService")
abstract class P11L1AckPolicyMixin {
    @WrapMethod(method = "accept(Lcom/yo1no/gramarye/magic/network/P7ServerIntentResult;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1AckPolicy(@Coerce Object result, Operation<Void> original) {
        boolean selected = P11L1AckObservation.policyEntered(result), normal = false;
        Throwable primary = null;
        try { original.call(result); normal = true; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1PacketProbe.ackPolicyFinished(selected, normal, primary); }
    }
    @WrapOperation(method = "submit(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerPlayer;Lcom/yo1no/gramarye/magic/network/P7SessionIdentity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)Lcom/yo1no/gramarye/magic/network/P7AuthoritativeSyncService$Submission;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/magic/network/P7AuthoritativeSyncService$Transport;submit(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$l1AckSend(@Coerce Object transport, ServerPlayer actor, CustomPacketPayload payload,
            Operation<Void> original) {
        P11L1AckObservation.beforeSend(actor, payload);
        original.call(transport, actor, payload);
    }
}
