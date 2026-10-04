package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aMalformedWireProbe;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(PacketEncoder.class)
abstract class P11C4aMalformedEncoderMixin {
    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V", require = 1, expect = 1, allow = 1)
    private void malformed$encode(ChannelHandlerContext context, Packet<?> packet, ByteBuf buffer, Operation<Void> original) throws Throwable {
        var frame = P11C4aMalformedWireProbe.begin(context, ((PacketEncoder<?>) (Object) this).getProtocolInfo().id(), packet, true);
        Throwable primary = null;
        try { original.call(context, packet, buffer); }
        catch (Throwable failure) { primary = failure; throw failure; }
        finally { P11C4aMalformedWireProbe.end(frame, primary); }
    }
}
