package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe;
import com.yo1no.gramarye.P11C4aConfigPrimaryProbe.Stage;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
abstract class P11C4aConfigPrimaryConnectionMixin {
    @Inject(method="doSendPacket(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",
            at=@At("HEAD"),require=1,expect=1,allow=1)
    private void primary$submitting(Packet<?> packet,PacketSendListener sendListener,boolean flush,CallbackInfo ci) {
        P11C4aConfigPrimaryProbe.submitting((Connection)(Object)this,packet);
    }
    @Inject(method="exceptionCaught(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Throwable;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void primary$exception(ChannelHandlerContext context,Throwable failure,CallbackInfo ci) {
        P11C4aConfigPrimaryProbe.connection(Stage.CONNECTION_EXCEPTION,(Connection)(Object)this,failure);
    }
    @Inject(method="disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void primary$disconnect(DisconnectionDetails ignored,CallbackInfo ci) {
        P11C4aConfigPrimaryProbe.connection(Stage.CONNECTION_DISCONNECT,(Connection)(Object)this,null);
    }
    @Inject(method="handleDisconnection()V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void primary$handled(CallbackInfo ci) {
        if(!((Connection)(Object)this).isConnected()) {
            P11C4aConfigPrimaryProbe.connection(Stage.HANDLE_DISCONNECTION,(Connection)(Object)this,null);
        }
    }
    @WrapOperation(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"),require=1,expect=1,allow=1)
    private void primary$directDispatch(Packet<?> packet,PacketListener listener,Operation<Void> original) {
        try {original.call(packet,listener);}
        catch(RuntimeException|Error failure) {
            P11C4aConfigPrimaryProbe.connection(Stage.DIRECT_DISPATCH_FAILURE,(Connection)(Object)this,failure);
            throw failure;
        }
    }
}
