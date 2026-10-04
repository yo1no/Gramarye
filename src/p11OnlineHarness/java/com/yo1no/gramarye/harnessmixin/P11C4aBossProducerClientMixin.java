package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aBossProducerClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
abstract class P11C4aBossProducerClientMixin {
    @Inject(method="handleBossUpdate(Lnet/minecraft/network/protocol/game/ClientboundBossEventPacket;)V",
            at=@At("RETURN"), require=1, expect=1, allow=1)
    private void boss$nativeHandlerReturn(ClientboundBossEventPacket packet, CallbackInfo callback) {
        P11C4aBossProducerClientProbe.received((ClientPacketListener)(Object)this,packet);
    }
}
