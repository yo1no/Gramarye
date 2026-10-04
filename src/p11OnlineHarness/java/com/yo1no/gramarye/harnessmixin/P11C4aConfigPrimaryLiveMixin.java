package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="com.yo1no.gramarye.P11LiveTransitionService",remap=false)
abstract class P11C4aConfigPrimaryLiveMixin {
    @WrapOperation(method="runPacketBody(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Ljava/lang/Runnable;)Z",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/PacketListener;onPacketError(Lnet/minecraft/network/protocol/Packet;Ljava/lang/Exception;)V"),require=1,expect=1,allow=1)
    private static void primary$policy(PacketListener listener,Packet<?> packet,Exception failure,Operation<Void> original) {
        P11C4aConfigPrimaryProbe.listener(P11C4aConfigPrimaryProbe.Stage.LIVE_PACKET_ERROR,listener,failure);
        original.call(listener,packet,failure);
    }
}
