package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1LifecycleClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class P11L1LifecycleClientMixin {
    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        P11L1LifecycleClientProbe.respawnReturned((ClientPacketListener) (Object) this);
    }
    @Inject(method = "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Configuration(ClientboundStartConfigurationPacket packet, CallbackInfo callback) {
        P11L1LifecycleClientProbe.configurationReturned((ClientPacketListener) (Object) this);
    }
}
