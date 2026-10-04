package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aConfigResetProbe;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Connection.class)
abstract class P11C4aConfigResetConnectionMixin implements P11C4aConfigResetProbe.Transport {
    @Shadow private Channel channel;
    @Override public boolean c4a$resetAutoRead() { return channel != null && channel.config().isAutoRead(); }
    @Override public Object c4a$resetDecoder() { return channel == null ? null : channel.pipeline().get("decoder"); }
    @WrapMethod(method = "setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V",
            require = 1, expect = 1, allow = 1)
    private void c4a$nativeProtocol(ProtocolInfo<?> protocol, PacketListener listener, Operation<Void> original) {
        var connection = (Connection) (Object) this;
        boolean observed = P11C4aConfigResetProbe.beforeProtocol(connection, protocol, listener);
        boolean normal = false;
        try { original.call(protocol, listener); normal = true; }
        finally { P11C4aConfigResetProbe.afterProtocol(connection, observed, normal); }
    }
}
