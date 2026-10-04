package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11OnlineClientHarness;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the new excluded companion's client Mixin array may register these observers. */
@Mixin(ClientPacketListener.class)
abstract class P11OnlineClientObservationMixin {
    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$onlineCapacityRespawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
        P11OnlineClientHarness.afterRespawn((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleUpdateAdvancementsPacket(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$onlineAdvancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo callback) {
        P11OnlineClientHarness.afterAdvancements((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleAddOrRemoveRecipes(Lnet/minecraft/network/protocol/game/ClientboundRecipePacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$onlineRecipes(ClientboundRecipePacket packet, CallbackInfo callback) {
        P11OnlineClientHarness.afterRecipes((ClientPacketListener) (Object) this, packet);
    }
}
