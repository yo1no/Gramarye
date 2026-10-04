package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aSubmissionRejectProbe;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Connection.class)
abstract class P11C4aSubmissionConnectionMixin {
    @WrapMethod(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",require=1,expect=1,allow=1)
    private void submit$read(ChannelHandlerContext context,Packet<?> packet,Operation<Void> original) {
        Object token=P11C4aSubmissionRejectProbe.channelEntered((Connection)(Object)this,packet);
        boolean normal=false;Throwable primary=null;
        try{original.call(context,packet);normal=true;}
        catch(RuntimeException|Error failure){primary=failure;throw failure;}
        finally{P11C4aSubmissionRejectProbe.channelEnded(token,normal,primary);}
    }
    @WrapOperation(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"),require=1,expect=1,allow=1)
    private void submit$dispatch(Packet<?> packet,PacketListener listener,Operation<Void> original) {
        try{original.call(packet,listener);}
        catch(RuntimeException|Error failure){
            P11C4aSubmissionRejectProbe.dispatchThrown((Connection)(Object)this,failure);throw failure;
        }
    }
    @WrapOperation(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V"),require=2,expect=2,allow=2)
    private void submit$disconnect(Connection connection,Component reason,Operation<Void> original) {
        boolean selected=P11C4aSubmissionRejectProbe.disconnectEntered(connection,reason);
        original.call(connection,reason);
        P11C4aSubmissionRejectProbe.disconnectReturned(selected);
    }
}
