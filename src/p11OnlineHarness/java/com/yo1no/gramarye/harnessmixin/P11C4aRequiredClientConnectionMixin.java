package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRequiredClientProbe;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
abstract class P11C4aRequiredClientConnectionMixin {
    @Inject(method = "setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void required$installed(ProtocolInfo<?> protocol, PacketListener listener, CallbackInfo callback) {
        P11C4aRequiredClientProbe.installed((Connection) (Object) this, listener);
    }
}
