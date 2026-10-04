package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aFirstTryProbe;
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

/** One excluded exact request loss before the normal offer; all other calls remain original. */
@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11C4aFirstTryBoundaryMixin {
    @WrapMethod(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",
            require = 1, expect = 1, allow = 1)
    private static void c4a$firstTryIngress(@Coerce Object request, Connection connection,
            ICommonPacketListener listener, Operation<Void> original) {
        var scope = P11C4aFirstTryProbe.beginIngress(request, connection, listener);
        try { if (!P11C4aFirstTryProbe.suppress(scope)) { original.call(request, connection, listener); } }
        finally { P11C4aFirstTryProbe.endIngress(scope); }
    }

    @Inject(method = "requireRespawnTicket(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$firstTryBody(PlayerList list, ServerPlayer actor, boolean keep, CallbackInfo callback) {
        P11C4aFirstTryProbe.body(actor);
    }

    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$firstTrySubmitted(ServerCommonPacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, CallbackInfo callback) {
        P11C4aFirstTryProbe.submitted(listener, packet);
    }
}
