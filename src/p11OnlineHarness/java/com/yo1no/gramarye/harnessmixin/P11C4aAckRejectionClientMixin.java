package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aAckRejectionClientProbe;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aAckRejectionClientMixin {
    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$selectedNativeAck(ClientCommonPacketListenerImpl listener, Packet<?> packet,
            BooleanSupplier condition, Duration duration, Operation<Void> original) {
        var selected = P11C4aAckRejectionClientProbe.packet(listener, packet);
        original.call(listener, selected, P11C4aAckRejectionClientProbe.condition(listener, condition), duration);
    }
    @WrapOperation(method = "sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean c4a$originalQueueAdd(List<Object> queue, Object value, Operation<Boolean> original,
            Packet<?> packet, BooleanSupplier condition, Duration duration) {
        boolean added = original.call(queue, value);
        P11C4aAckRejectionClientProbe.queued((ClientCommonPacketListenerImpl) (Object) this, packet, added);
        return added;
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$actualSend(Packet<?> packet, CallbackInfo callback) {
        P11C4aAckRejectionClientProbe.sent((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
    @Inject(method = "handleDisconnect(Lnet/minecraft/network/protocol/common/ClientboundDisconnectPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$actualDisconnectPacket(ClientboundDisconnectPacket packet, CallbackInfo callback) {
        P11C4aAckRejectionClientProbe.disconnected((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
}
