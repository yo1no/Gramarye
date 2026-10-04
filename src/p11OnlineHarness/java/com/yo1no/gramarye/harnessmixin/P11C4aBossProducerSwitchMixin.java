package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aBossProducerProbe;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11C4aBossProducerSwitchMixin {
    @Inject(method="switchToConfig()V", at=@At("RETURN"), require=1, expect=1, allow=1)
    private void boss$originalSwitchReturned(CallbackInfo callback) {
        P11C4aBossProducerProbe.switched((ServerGamePacketListenerImpl)(Object)this);
    }
}
