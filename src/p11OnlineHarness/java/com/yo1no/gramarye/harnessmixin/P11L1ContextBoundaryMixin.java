package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11L1ContextRefusalProbe;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11L1ContextBoundaryMixin {
    @Inject(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void l1$contextIngress(@Coerce Object request, Connection connection,
            ICommonPacketListener listener, CallbackInfo callback) {
        if (connection.isConnected() && connection.getPacketListener() == listener && listener.getConnection() == connection) {
            P11L1ContextRefusalProbe.ingress(request, connection);
        }
    }
    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void l1$contextSubmitted(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) { P11L1ContextRefusalProbe.submitted(listener, packet); }
    @Inject(method = "requireFactoryTicket(Lnet/minecraft/server/players/PlayerList;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void l1$contextFactory(PlayerList list, GameProfile profile, ClientInformation information,
            CallbackInfo callback) { P11L1ContextRefusalProbe.factory(profile.getId()); }
}
