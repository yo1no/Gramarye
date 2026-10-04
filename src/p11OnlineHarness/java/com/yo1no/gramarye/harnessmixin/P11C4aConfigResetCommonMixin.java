package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aConfigResetProbe;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aConfigResetCommonMixin implements P11C4aConfigResetProbe.Fields {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;
    @Shadow private int latency;
    @Override public long c4a$resetTime() { return keepAliveTime; }
    @Override public boolean c4a$resetPending() { return keepAlivePending; }
    @Override public long c4a$resetChallenge() { return keepAliveChallenge; }
    @Override public int c4a$resetLatency() { return latency; }

    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$originalConstructor(MinecraftServer server, Connection connection, CommonListenerCookie cookie, CallbackInfo callback) {
        P11C4aConfigResetProbe.constructed((ServerCommonPacketListenerImpl) (Object) this, cookie);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeSend(Packet<?> packet, CallbackInfo callback) {
        P11C4aConfigResetProbe.sending((ServerCommonPacketListenerImpl) (Object) this, packet);
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$nativeSubmitted(Packet<?> packet, CallbackInfo callback) {
        P11C4aConfigResetProbe.submitted((ServerCommonPacketListenerImpl) (Object) this, packet);
    }

    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            require = 1, expect = 1, allow = 1)
    private void c4a$originalAck(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        boolean pending = keepAlivePending;
        long challenge = keepAliveChallenge;
        boolean normal = false;
        try { original.call(packet); normal = true; }
        finally { P11C4aConfigResetProbe.ackReturned((ServerCommonPacketListenerImpl) (Object) this,
                packet.getId(), pending, challenge, normal); }
    }
}
