package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aNativeObservations;
import com.yo1no.gramarye.P11C4aNativeObservations.Event;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exact locked NeoForge fallback call; no negotiation, payload or disconnect behavior is replaced. */
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class P11C4aClientConfigurationObservationMixin {
    @WrapOperation(method = "handleConfigurationFinished(Lnet/minecraft/network/protocol/configuration/ClientboundFinishConfigurationPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/network/registration/NetworkRegistry;initializeOtherConnection(Lnet/minecraft/network/protocol/configuration/ClientConfigurationPacketListener;)V"),
            require = 1, expect = 1, allow = 1)
    private void c4a$otherFallback(ClientConfigurationPacketListener listener, Operation<Void> original) {
        P11C4aNativeObservations.event(listener.getConnection(), Event.CLIENT_RETURN_OTHER_FALLBACK);
        boolean connected = listener.getConnection().isConnected();
        original.call(listener);
        if (connected && !listener.getConnection().isConnected()) {
            P11C4aNativeObservations.event(listener.getConnection(), Event.CLIENT_RETURN_OTHER_FALLBACK_DISCONNECTED);
        }
    }
}
