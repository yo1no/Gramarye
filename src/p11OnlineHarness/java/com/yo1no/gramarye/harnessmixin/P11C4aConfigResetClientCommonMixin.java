package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aConfigResetClientProbe;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
abstract class P11C4aConfigResetClientCommonMixin {
    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$originalDeferredAck(ClientCommonPacketListenerImpl listener, Packet<?> packet,
            BooleanSupplier condition, Duration duration, Operation<Void> original) {
        original.call(listener, packet, P11C4aConfigResetClientProbe.defer(listener, packet, condition), duration);
    }

    @WrapOperation(method = "sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean c4a$actualNativeQueueAdd(List<Object> queue, Object value, Operation<Boolean> original,
            Packet<?> packet, BooleanSupplier condition, Duration duration) {
        boolean added = original.call(queue, value);
        P11C4aConfigResetClientProbe.queued((ClientCommonPacketListenerImpl) (Object) this, packet, added);
        return added;
    }

    @Inject(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ClientboundKeepAlivePacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeChallenge(ClientboundKeepAlivePacket packet, CallbackInfo callback) {
        P11C4aConfigResetClientProbe.received((ClientCommonPacketListenerImpl) (Object) this, packet);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeSend(Packet<?> packet, CallbackInfo callback) {
        P11C4aConfigResetClientProbe.sending((ClientCommonPacketListenerImpl) (Object) this, packet, false);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeSendReturned(Packet<?> packet, CallbackInfo callback) {
        P11C4aConfigResetClientProbe.sending((ClientCommonPacketListenerImpl) (Object) this, packet, true);
    }
}
