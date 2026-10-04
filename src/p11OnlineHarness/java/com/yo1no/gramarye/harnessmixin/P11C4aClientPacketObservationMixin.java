package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aNativeObservations;
import com.yo1no.gramarye.P11C4aNativeObservations.Event;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real client listener consumers, separate from marker emission or caller return. */
@Mixin(ClientPacketListener.class)
abstract class P11C4aClientPacketObservationMixin {
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$login(ClientboundLoginPacket packet, CallbackInfo callback) {
        P11C4aNativeObservations.event(((ClientPacketListener) (Object) this).getConnection(), Event.CLIENT_LOGIN_RETURN);
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        P11C4aNativeObservations.event(((ClientPacketListener) (Object) this).getConnection(), Event.CLIENT_RESPAWN_RETURN);
    }

    @Inject(method = "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c4a$configuration(ClientboundStartConfigurationPacket packet, CallbackInfo callback) {
        P11C4aNativeObservations.event(((ClientPacketListener) (Object) this).getConnection(), Event.CLIENT_START_CONFIGURATION_RETURN);
    }
}
