package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11C4aConfigCatchProbe;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerConfigurationPacketListenerImpl.class)
abstract class P11C4aConfigCatchNativeMixin {
    @Shadow @Final private static Component DISCONNECT_REASON_INVALID_DATA;
    @WrapOperation(method="handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;getPlayerForLogin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)Lnet/minecraft/server/level/ServerPlayer;"),require=1,expect=1,allow=1)
    private ServerPlayer configCatch$factory(PlayerList list,GameProfile profile,ClientInformation information,Operation<ServerPlayer> original){
        ServerPlayer result=original.call(list,profile,information);
        P11C4aConfigCatchProbe.constructed((ServerConfigurationPacketListenerImpl)(Object)this,result);
        return result;
    }
    @WrapOperation(method="handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V"),require=1,expect=1,allow=1)
    private void configCatch$place(PlayerList list,Connection connection,ServerPlayer player,CommonListenerCookie cookie,Operation<Void> original){
        P11C4aConfigCatchProbe.place((ServerConfigurationPacketListenerImpl)(Object)this);original.call(list,connection,player,cookie);
    }
    @WrapOperation(method="handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at=@At(value="INVOKE",target="Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"),require=1,expect=1,allow=1)
    private void configCatch$log(Logger logger,String unused,Throwable failure,Operation<Void> original){
        var self=(ServerConfigurationPacketListenerImpl)(Object)this;
        P11C4aConfigCatchProbe.logged(self,failure,false);original.call(logger,unused,failure);P11C4aConfigCatchProbe.logged(self,failure,true);
    }
    @WrapOperation(method="handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1,expect=1,allow=1)
    private void configCatch$send(Connection connection,Packet<?> packet,Operation<Void> original){
        var self=(ServerConfigurationPacketListenerImpl)(Object)this;
        boolean exact=packet instanceof ClientboundDisconnectPacket disconnect && disconnect.reason()==DISCONNECT_REASON_INVALID_DATA;
        P11C4aConfigCatchProbe.invalid(self,connection,exact,false,false);original.call(connection,packet);
        P11C4aConfigCatchProbe.invalid(self,connection,exact,false,true);
    }
    @WrapOperation(method="handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/chat/Component;)V"),require=1,expect=1,allow=1)
    private void configCatch$disconnect(Connection connection,Component reason,Operation<Void> original){
        var self=(ServerConfigurationPacketListenerImpl)(Object)this;boolean exact=reason==DISCONNECT_REASON_INVALID_DATA;
        P11C4aConfigCatchProbe.invalid(self,connection,exact,true,false);original.call(connection,reason);
        P11C4aConfigCatchProbe.invalid(self,connection,exact,true,true);
    }
}
