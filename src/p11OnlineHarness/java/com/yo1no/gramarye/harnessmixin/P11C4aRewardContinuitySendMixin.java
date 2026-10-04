package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aRewardContinuityProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aRewardContinuitySendMixin {
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;)V")
    private void reward$send(Packet<?> packet, Operation<Void> original) {
        P11C4aRewardContinuityProbe.send((ServerCommonPacketListenerImpl) (Object) this, packet, false);
        original.call(packet);
        P11C4aRewardContinuityProbe.send((ServerCommonPacketListenerImpl) (Object) this, packet, true);
    }
}
