package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aReloadBlockerProbe;
import com.yo1no.gramarye.P11C4aPortalProbe;
import com.yo1no.gramarye.P11C4aNativeSenderProbe;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Finite reload-phase observations only. No native body or admission result is replaced. */
@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11C4aReloadBoundaryObservationMixin {
    @Inject(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$reloadIngress(@Coerce Object request, Connection connection,
            ICommonPacketListener listener, CallbackInfo callback) {
        if (connection.isConnected() && connection.getPacketListener() == listener && listener.getConnection() == connection) {
            P11C4aNativeSenderProbe.ingress(request, connection);
            P11C4aReloadBlockerProbe.ingress(request, connection);
        }
    }

    @Inject(method = "requireRespawnTicket(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$reloadBody(PlayerList list, ServerPlayer actor, boolean keep, CallbackInfo callback) {
        P11C4aReloadBlockerProbe.body(actor);
    }

    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$reloadSubmitted(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) {
        P11C4aReloadBlockerProbe.submitted(listener, packet);
        P11C4aPortalProbe.submitted(listener, packet);
    }
}
