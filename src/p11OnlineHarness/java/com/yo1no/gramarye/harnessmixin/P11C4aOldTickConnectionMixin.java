package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aOldTickProbe;
import net.minecraft.network.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
abstract class P11C4aOldTickConnectionMixin {
    @Inject(method="setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void oldTick$protocol(ProtocolInfo<?> protocol,PacketListener listener,CallbackInfo ci) {
        P11C4aOldTickProbe.protocolInstall((Connection)(Object)this);
    }
}
