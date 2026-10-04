package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yo1no.gramarye.P11L1PacketProbe;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.yo1no.gramarye.P8PacketSubmission")
abstract class P11L1P8SendMixin {
    @WrapOperation(method = "send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
            require = 1, expect = 1, allow = 1)
    private static void p11$l1P8Send(ServerGamePacketListenerImpl listener, CustomPacketPayload payload,
            Operation<Void> original, @Local(argsOnly = true) ServerPlayer recipient) {
        P11L1PacketProbe.beforeP8Send(recipient, payload);
        original.call(listener, payload);
    }
}
