package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aParkingProbe;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aParkingKeepAliveMixin {
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;

    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V", require = 1, expect = 1, allow = 1)
    private void c4a$actualParkingAck(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        boolean pending = keepAlivePending;
        long challenge = keepAliveChallenge;
        original.call(packet);
        P11C4aParkingProbe.keepAliveAck((ServerCommonPacketListenerImpl) (Object) this,
                packet.getId(), pending, challenge, keepAlivePending, keepAliveChallenge);
    }
}
