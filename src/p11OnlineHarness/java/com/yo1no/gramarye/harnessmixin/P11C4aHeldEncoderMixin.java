package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aHeldClientProbe;
import com.yo1no.gramarye.P11C4aErrorStatusClientProbe;
import com.yo1no.gramarye.P11C4aTerminalStatusClientProbe;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
/** Client array only: the normal packet encoder, not an engineering raw sender. */
@Mixin(PacketEncoder.class)
abstract class P11C4aHeldEncoderMixin {
    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V", require = 1, expect = 1, allow = 1)
    private void held$encode(ChannelHandlerContext context, Packet<?> packet, ByteBuf buffer, Operation<Void> original) {
        var scope = P11C4aHeldClientProbe.begin(context, ((PacketEncoder<?>) (Object) this).getProtocolInfo().id(), packet);
        var errorScope = P11C4aErrorStatusClientProbe.begin(context, ((PacketEncoder<?>) (Object) this).getProtocolInfo().id(), packet);
        var terminalScope = P11C4aTerminalStatusClientProbe.begin(context, ((PacketEncoder<?>) (Object) this).getProtocolInfo().id(), packet);
        boolean normal = false;
        try { original.call(context, packet, buffer); normal = true; }
        finally {
            P11C4aHeldClientProbe.end(scope, normal);
            P11C4aErrorStatusClientProbe.end(errorScope, normal);
            P11C4aTerminalStatusClientProbe.end(terminalScope, normal);
        }
    }
}
