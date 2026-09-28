package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeDeliveryProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registered only in the excluded harness configuration's client array. */
@Mixin(ClientPacketListener.class)
abstract class P11ClientDeliveryObservationMixin {
    @Inject(method = "handleUpdateAdvancementsPacket(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$advancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo callback) {
        P11NativeDeliveryProbe.afterAdvancements((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleAddOrRemoveRecipes(Lnet/minecraft/network/protocol/game/ClientboundRecipePacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$recipes(ClientboundRecipePacket packet, CallbackInfo callback) {
        P11NativeDeliveryProbe.afterRecipes((ClientPacketListener) (Object) this, packet);
    }
}
