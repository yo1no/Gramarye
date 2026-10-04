package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aOldTickProbe;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aOldTickCommonMixin {
    @WrapOperation(method="keepConnectionAlive()V", at=@At(value="INVOKE", target="Lnet/minecraft/Util;getMillis()J"), require=1, expect=1, allow=1)
    private long oldTick$clock(Operation<Long> original) {
        long now = original.call();
        P11C4aOldTickProbe.clock((ServerCommonPacketListenerImpl)(Object)this, now);
        return now;
    }
    @Inject(method="keepConnectionAlive()V", at=@At("RETURN"), require=1, expect=1, allow=1)
    private void oldTick$return(CallbackInfo ci) { P11C4aOldTickProbe.tickReturned((ServerCommonPacketListenerImpl)(Object)this); }
    @WrapMethod(method="handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V", require=1, expect=1, allow=1)
    private void oldTick$ack(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        var observed=P11C4aOldTickProbe.beforeAck((ServerCommonPacketListenerImpl)(Object)this, packet.getId());
        boolean normal=false;
        try { original.call(packet); normal=true; }
        finally { P11C4aOldTickProbe.afterAck(observed, normal); }
    }
}
