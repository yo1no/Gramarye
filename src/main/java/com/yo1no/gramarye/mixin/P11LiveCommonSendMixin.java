package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/** Locked Login/Respawn/StartConfiguration producers use this one-argument native send. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11LiveCommonSendMixin {
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;)V", require = 1, expect = 1, allow = 1)
    private void p11$transitionFrame(Packet<?> packet, Operation<Void> original) {
        P11LiveTransitionBoundary.nativeSend((ServerCommonPacketListenerImpl) (Object) this, packet, original);
    }
}
