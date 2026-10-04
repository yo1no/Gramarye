package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aBossProducerProbe;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aBossProducerSendMixin {
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V", at=@At("HEAD"), require=1, expect=1, allow=1)
    private void boss$sendHead(Packet<?> packet, CallbackInfo callback) {
        P11C4aBossProducerProbe.sent((ServerCommonPacketListenerImpl)(Object)this,packet,false);
    }
    @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V", at=@At("RETURN"), require=1, expect=1, allow=1)
    private void boss$sendReturn(Packet<?> packet, CallbackInfo callback) {
        P11C4aBossProducerProbe.sent((ServerCommonPacketListenerImpl)(Object)this,packet,true);
    }
}
