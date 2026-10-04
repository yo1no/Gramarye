package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aNativeErrorProbe;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aNativeErrorSendMixin {
    @WrapOperation(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V"),require=1,expect=1,allow=1)
    private void error$nativeSend(Connection connection,Packet<?> packet,PacketSendListener callback,boolean flush,Operation<Void> original) {
        boolean selected=P11C4aNativeErrorProbe.beforeConnectionSend((ServerCommonPacketListenerImpl)(Object)this,packet);
        P11C4aConfigPrimaryProbe.scheduling((ServerCommonPacketListenerImpl)(Object)this,packet);
        original.call(connection,packet,callback,flush);
        P11C4aNativeErrorProbe.afterConnectionSend(selected);
    }
}
