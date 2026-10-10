package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11D3ClientHarness;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
abstract class P11D3ClientPacketMixin {
    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$d3Respawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        P11D3ClientHarness.respawnReturned((ClientPacketListener) (Object) this);
    }
}
