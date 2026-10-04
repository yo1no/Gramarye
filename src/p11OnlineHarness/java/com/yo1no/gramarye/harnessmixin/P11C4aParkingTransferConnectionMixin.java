package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aParkingTransferProbe;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
abstract class P11C4aParkingTransferConnectionMixin implements P11C4aParkingTransferProbe.Transport {
    @Shadow private Channel channel;
    @Override public boolean c4a$autoRead() { return channel != null && channel.config().isAutoRead(); }
    @Override public Object c4a$decoder() { return channel == null ? null : channel.pipeline().get("decoder"); }

    @Inject(method = "setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c4a$actualInboundInstall(ProtocolInfo<?> protocol, PacketListener listener, CallbackInfo callback) {
        P11C4aParkingTransferProbe.protocolInstall((Connection) (Object) this);
    }
}
