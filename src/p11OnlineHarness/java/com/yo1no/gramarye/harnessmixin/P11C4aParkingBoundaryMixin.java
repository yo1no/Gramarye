package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11C4aParkingProbe;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import com.yo1no.gramarye.P11ParkingPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11C4aParkingBoundaryMixin {
    @WrapMethod(method = "configurationFinished(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", require = 1, expect = 1, allow = 1)
    private static void c4a$parkingConfig(ServerConfigurationPacketListenerImpl listener,
            ServerboundFinishConfigurationPacket packet, Operation<Void> body, Operation<Void> original) {
        P11C4aParkingProbe.configCaller(listener.getConnection(), true, false);
        boolean normal = false;
        try { original.call(listener, packet, body); normal = true; }
        finally { P11C4aParkingProbe.configCaller(listener.getConnection(), false, normal); }
    }

    @Inject(method = "beforeConfigurationFactory(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)Z", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingFinal(ServerConfigurationPacketListenerImpl listener, CallbackInfoReturnable<Boolean> callback) {
        P11C4aParkingProbe.finalCheck(listener);
    }

    @Inject(method = "parkingInstalled(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lcom/yo1no/gramarye/P11ParkingPacketListener;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$parked(ServerConfigurationPacketListenerImpl previous, P11ParkingPacketListener parking, CallbackInfo callback) {
        P11C4aParkingProbe.parked(parking);
    }

    @Inject(method = "requireFactoryTicket(Lnet/minecraft/server/players/PlayerList;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingFactory(PlayerList list, GameProfile profile, ClientInformation information, CallbackInfo callback) {
        P11C4aParkingProbe.factory();
    }

    @Inject(method = "expectedActor(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingActor(ServerPlayer actor, CallbackInfo callback) { P11C4aParkingProbe.constructed(actor); }

    @Inject(method = "expectedNativeFrame(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/network/protocol/Packet;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingFrame(PlayerList list, Packet<?> packet, CallbackInfo callback) { P11C4aParkingProbe.frame(packet); }

    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingSend(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) { P11C4aParkingProbe.submitted(listener, packet); }

    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingSending(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) { P11C4aParkingProbe.sending(listener, packet); }

    @Inject(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$parkingIngress(@Coerce Object request, Connection connection, ICommonPacketListener listener, CallbackInfo callback) {
        if (connection.isConnected() && connection.getPacketListener() == listener && listener.getConnection() == connection) {
            P11C4aParkingProbe.ingress(request, connection);
        }
    }
}
