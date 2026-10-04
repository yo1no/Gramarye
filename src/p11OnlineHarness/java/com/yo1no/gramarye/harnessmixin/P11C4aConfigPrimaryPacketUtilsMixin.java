package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(PacketUtils.class)
abstract class P11C4aConfigPrimaryPacketUtilsMixin {
    @WrapOperation(method="lambda$ensureRunningOnSameThread$0(Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/protocol/Packet;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/PacketListener;onPacketError(Lnet/minecraft/network/protocol/Packet;Ljava/lang/Exception;)V"),require=1,expect=1,allow=1)
    private static void primary$policy(PacketListener listener,Packet<?> packet,Exception failure,Operation<Void> original) {
        P11C4aConfigPrimaryProbe.listener(P11C4aConfigPrimaryProbe.Stage.PACKET_UTILS_ERROR,listener,failure);
        original.call(listener,packet,failure);
    }
}
