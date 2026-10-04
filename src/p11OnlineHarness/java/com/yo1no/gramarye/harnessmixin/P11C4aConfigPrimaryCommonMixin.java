package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11C4aConfigPrimaryCommonMixin {
    @Inject(method="disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void primary$disconnect(DisconnectionDetails ignored,CallbackInfo ci) {
        P11C4aConfigPrimaryProbe.listener(P11C4aConfigPrimaryProbe.Stage.COMMON_DISCONNECT,(ServerCommonPacketListenerImpl)(Object)this,null);
    }
}
