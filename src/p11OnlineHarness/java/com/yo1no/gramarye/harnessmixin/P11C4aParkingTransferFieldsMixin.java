package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aParkingTransferProbe;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aParkingTransferFieldsMixin implements P11C4aParkingTransferProbe.Fields {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;
    @Shadow private int latency;
    @Override public long c4a$time() { return keepAliveTime; }
    @Override public boolean c4a$pending() { return keepAlivePending; }
    @Override public long c4a$challenge() { return keepAliveChallenge; }
    @Override public int c4a$latency() { return latency; }

    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            require = 1, expect = 1, allow = 1)
    private void c4a$captureExactAck(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        var self = (ServerCommonPacketListenerImpl) (Object) this;
        boolean observed = P11C4aParkingTransferProbe.beforeAck(self, packet);
        boolean normal = false;
        try { original.call(packet); normal = true; }
        finally { if (observed) { P11C4aParkingTransferProbe.afterAck(self, normal); } }
    }
}
