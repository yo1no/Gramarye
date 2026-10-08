package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11CooldownCloneClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class P11CooldownCloneClientMixin {
    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$cooldownCloneRespawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        P11CooldownCloneClientProbe.respawnReturned((ClientPacketListener) (Object) this);
    }
}
