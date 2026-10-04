package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aUiInputProbe;
import com.yo1no.gramarye.P11C4aUiHeldInputProbe;
import net.minecraft.network.Connection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11ClientTransitions", remap = false)
abstract class P11C4aUiInputControllerMixin {
    @Inject(method = "tick(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Post;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void c4a$realTick(ClientTickEvent.Post event, CallbackInfo callback) {
        P11C4aUiInputProbe.controllerTick();
        P11C4aUiHeldInputProbe.controllerTick();
    }
    @Inject(method = "send(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$submit(@Coerce Object request, CallbackInfo callback) {
        P11C4aUiInputProbe.submitted(request);
        P11C4aUiHeldInputProbe.submitted(request);
    }
    @Inject(method = "handle(Lcom/yo1no/gramarye/P11TransitionProtocol$State;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$accepted(@Coerce Object state, Connection connection,
            ICommonPacketListener listener, CallbackInfo callback) {
        P11C4aUiInputProbe.accepted(state, connection);
        P11C4aUiHeldInputProbe.accepted(state, connection);
    }
    @Inject(method = "nativeFrameApplied(Lnet/minecraft/client/multiplayer/ClientPacketListener;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private static void c4a$frameSettled(ClientPacketListener listener, Packet<?> packet, CallbackInfo callback) {
        P11C4aUiInputProbe.settledIfReady();
    }
}
