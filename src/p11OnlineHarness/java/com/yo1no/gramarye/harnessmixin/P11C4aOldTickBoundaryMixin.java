package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=P11LiveTransitionBoundary.class,remap=false)
abstract class P11C4aOldTickBoundaryMixin {
    @Inject(method="requireFactoryTicket(Lnet/minecraft/server/players/PlayerList;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",at=@At("TAIL"),require=1,expect=1,allow=1)
    private static void oldTick$factory(PlayerList list,GameProfile profile,ClientInformation info,CallbackInfo ci) { P11C4aOldTickProbe.factory(); }
    @Inject(method="nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private static void oldTick$sending(ServerCommonPacketListenerImpl listener,Packet<?> packet,Operation<Void> original,CallbackInfo ci) { P11C4aOldTickProbe.sent(listener,packet,false); }
    @Inject(method="nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",at=@At("RETURN"),require=1,expect=1,allow=1)
    private static void oldTick$sent(ServerCommonPacketListenerImpl listener,Packet<?> packet,Operation<Void> original,CallbackInfo ci) { P11C4aOldTickProbe.sent(listener,packet,true); }
}
