package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aAckRejectionProbe;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aAckRejectionCommonMixin implements P11C4aAckRejectionProbe.Fields {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;
    @Shadow private int latency;
    @Override public long c4a$ackTime() { return keepAliveTime; }
    @Override public boolean c4a$ackPending() { return keepAlivePending; }
    @Override public long c4a$ackChallenge() { return keepAliveChallenge; }
    @Override public int c4a$ackLatency() { return latency; }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$challenge(Packet<?> packet, CallbackInfo callback) {
        P11C4aAckRejectionProbe.challenge((ServerCommonPacketListenerImpl) (Object) this, packet, false);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$challengeReturned(Packet<?> packet, CallbackInfo callback) {
        P11C4aAckRejectionProbe.challenge((ServerCommonPacketListenerImpl) (Object) this, packet, true);
    }
    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            require = 1, expect = 1, allow = 1)
    private void c4a$actualAck(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        var scope = P11C4aAckRejectionProbe.begin((ServerCommonPacketListenerImpl) (Object) this, packet.getId(), false);
        boolean normal = false;
        try { original.call(packet); normal = true; }
        finally { P11C4aAckRejectionProbe.end(scope, normal); }
    }
    @WrapMethod(method = "keepConnectionAlive()V", require = 1, expect = 1, allow = 1)
    private void c4a$actualTick(Operation<Void> original) {
        var scope = P11C4aAckRejectionProbe.begin((ServerCommonPacketListenerImpl) (Object) this, 0, true);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally { P11C4aAckRejectionProbe.end(scope, normal); }
    }
    @WrapOperation(method = "keepConnectionAlive()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"),
            require = 1, expect = 1, allow = 1)
    private long c4a$nativeClock(Operation<Long> original) {
        long now = original.call();
        P11C4aAckRejectionProbe.nativeClock((ServerCommonPacketListenerImpl) (Object) this, now);
        return now;
    }
    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$actualDisconnectEntry(Component reason, CallbackInfo callback) {
        P11C4aAckRejectionProbe.rejecting((ServerCommonPacketListenerImpl) (Object) this, reason);
    }
    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$actualDisconnectReturn(Component reason, CallbackInfo callback) {
        P11C4aAckRejectionProbe.disconnectReturned((ServerCommonPacketListenerImpl) (Object) this);
    }
}
