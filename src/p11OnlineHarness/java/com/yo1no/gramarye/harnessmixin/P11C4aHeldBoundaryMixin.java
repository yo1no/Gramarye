package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aHeldProbe;
import com.yo1no.gramarye.P11C4aErrorStatusProbe;
import com.yo1no.gramarye.P11C4aTerminalStatusProbe;
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
@Mixin(value = P11LiveTransitionBoundary.class, remap = false)
abstract class P11C4aHeldBoundaryMixin {
    @WrapMethod(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", require = 1, expect = 1, allow = 1)
    private static void held$ingress(@Coerce Object request, Connection connection, ICommonPacketListener listener, Operation<Void> original) throws Throwable {
        var scope = P11C4aHeldProbe.beginIngress(request, connection, listener);
        var errorScope = P11C4aErrorStatusProbe.beginIngress(request, connection, listener);
        var terminalScope = P11C4aTerminalStatusProbe.beginIngress(request, connection, listener);
        Throwable primary = null;
        try { original.call(request, connection, listener); }
        catch (Throwable failure) { primary = failure; throw failure; }
        finally {
            try { P11C4aHeldProbe.endIngress(scope, primary); }
            finally {
                try { P11C4aErrorStatusProbe.endIngress(errorScope, primary); }
                finally { P11C4aTerminalStatusProbe.endIngress(terminalScope, primary); }
            }
        }
    }
    @Inject(method = "requireRespawnTicket(Lnet/minecraft/server/players/PlayerList;Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void held$body(PlayerList list, ServerPlayer actor, boolean keep, CallbackInfo callback) { P11C4aHeldProbe.body(actor); }
    @Inject(method = "nativeSend(Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;Lnet/minecraft/network/protocol/Packet;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void held$submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet, Operation<Void> original, CallbackInfo callback) {
        P11C4aHeldProbe.submitted(listener, packet);
        P11C4aTerminalStatusProbe.submitted(listener, packet);
    }
}
