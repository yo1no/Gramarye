package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aParkingClientProbe;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aParkingTransferClientMixin {
    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$deferOnlyOriginalAck(ClientCommonPacketListenerImpl listener, Packet<?> packet,
            BooleanSupplier originalCondition, Duration duration, Operation<Void> original) {
        original.call(listener, packet,
                P11C4aParkingClientProbe.deferAck(listener.getConnection(), packet, originalCondition), duration);
    }
}
