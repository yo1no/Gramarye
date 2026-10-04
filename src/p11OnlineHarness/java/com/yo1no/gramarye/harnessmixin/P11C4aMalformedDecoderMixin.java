package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(PacketDecoder.class)
abstract class P11C4aMalformedDecoderMixin {
    @Shadow @Final private ProtocolInfo<?> protocolInfo;
    @WrapMethod(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V", require = 1, expect = 1, allow = 1)
    private void malformed$decode(ChannelHandlerContext context, ByteBuf buffer, List<Object> packets, Operation<Void> original) throws Throwable {
        var frame = P11C4aMalformedWireProbe.begin(context, protocolInfo.id(), null, false);
        Throwable primary = null;
        try { original.call(context, buffer, packets); }
        catch (Throwable failure) { primary = failure; throw failure; }
        finally { P11C4aMalformedWireProbe.end(frame, primary); }
    }
}
