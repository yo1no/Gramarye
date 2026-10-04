package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11C4aNativeObservations;
import com.yo1no.gramarye.P11C4aNativeObservations.Event;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import com.yo1no.gramarye.P11ParkingPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact production bridges are observed without changing the original operation or its error policy. */
@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11C4aBoundaryObservationMixin {
    @WrapMethod(method = "configurationFinished(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lnet/minecraft/network/protocol/configuration/ServerboundFinishConfigurationPacket;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V")
    private static void c4a$config(ServerConfigurationPacketListenerImpl listener,
            ServerboundFinishConfigurationPacket packet, Operation<Void> body, Operation<Void> original) {
        var call = P11C4aNativeObservations.begin(listener, Event.CONFIG_CALL_ENTER);
        boolean normal = false;
        try { original.call(listener, packet, body); normal = true; }
        finally { P11C4aNativeObservations.end(call, normal); }
    }

    @WrapMethod(method = "performRespawn(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V")
    private static void c4a$respawn(ServerGamePacketListenerImpl listener,
            ServerboundClientCommandPacket packet, Operation<Void> body, Operation<Void> original) {
        var call = P11C4aNativeObservations.begin(listener, Event.RESPAWN_CALL_ENTER);
        boolean normal = false;
        try { original.call(listener, packet, body); normal = true; }
        finally { P11C4aNativeObservations.end(call, normal); }
    }

    @WrapMethod(method = "switchToConfig(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V")
    private static void c4a$switch(ServerGamePacketListenerImpl listener,
            Operation<Void> body, Operation<Void> original) {
        var call = P11C4aNativeObservations.begin(listener, Event.SWITCH_CALL_ENTER);
        boolean normal = false;
        try { original.call(listener, body); normal = true; }
        finally { P11C4aNativeObservations.end(call, normal); }
    }

    @Inject(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$ingress(@Coerce Object request, Connection connection,
            ICommonPacketListener listener, CallbackInfo callback) {
        P11C4aNativeObservations.ingress(request, connection, listener);
    }

    @Inject(method = "requireFactoryTicket(Lnet/minecraft/server/players/PlayerList;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$factory(PlayerList list, GameProfile profile, ClientInformation information, CallbackInfo callback) {
        P11C4aNativeObservations.current(Event.FACTORY_TICKET_CHECKED);
    }

    @Inject(method = "requireRespawnTicket(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$respawnTicket(PlayerList list, ServerPlayer player, boolean keep, CallbackInfo callback) {
        P11C4aNativeObservations.current(Event.RESPAWN_TICKET_CHECKED);
    }

    @Inject(method = "expectedActor(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$actor(ServerPlayer player, CallbackInfo callback) {
        P11C4aNativeObservations.current(Event.EXPECTED_ACTOR);
    }

    @Inject(method = "expectedNativeFrame(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/network/protocol/Packet;)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$frame(PlayerList list, Packet<?> packet, CallbackInfo callback) {
        P11C4aNativeObservations.expectedFrame(packet);
    }

    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$send(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) {
        P11C4aNativeObservations.sent(listener, packet);
    }

    @Inject(method = "parkingInstalled(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;Lcom/yo1no/gramarye/P11ParkingPacketListener;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$parking(ServerConfigurationPacketListenerImpl previous,
            P11ParkingPacketListener parking, CallbackInfo callback) {
        P11C4aNativeObservations.event(parking.getConnection(), Event.PARKING_INSTALLED);
    }
}
