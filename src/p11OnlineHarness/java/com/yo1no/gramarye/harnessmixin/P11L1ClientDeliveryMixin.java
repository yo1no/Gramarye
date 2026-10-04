package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11L1ClientHarness;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class P11L1ClientDeliveryMixin {
    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Login(ClientboundLoginPacket packet, CallbackInfo callback) {
        P11L1ClientHarness.loginReturned((ClientPacketListener) (Object) this);
    }
    @Inject(method = "handleUpdateAdvancementsPacket(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Progress(ClientboundUpdateAdvancementsPacket packet, CallbackInfo callback) {
        P11L1ClientHarness.advancements((ClientPacketListener) (Object) this, packet);
    }
    @Inject(method = "handleAddOrRemoveRecipes(Lnet/minecraft/network/protocol/game/ClientboundRecipePacket;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$l1Recipes(ClientboundRecipePacket packet, CallbackInfo callback) {
        P11L1ClientHarness.recipes((ClientPacketListener) (Object) this, packet);
    }
}
