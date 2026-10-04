package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRewardContinuityClientProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
abstract class P11C4aRewardContinuityClientMixin {
    @Inject(method = "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void reward$config(ClientboundStartConfigurationPacket packet, CallbackInfo callback) {
        P11C4aRewardContinuityClientProbe.configurationStarted((ClientPacketListener) (Object) this);
    }
    @Inject(method = "handleUpdateAdvancementsPacket(Lnet/minecraft/network/protocol/game/ClientboundUpdateAdvancementsPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void reward$advancements(ClientboundUpdateAdvancementsPacket packet, CallbackInfo callback) {
        P11C4aRewardContinuityClientProbe.advancements((ClientPacketListener) (Object) this, packet);
    }
    @Inject(method = "handleAddOrRemoveRecipes(Lnet/minecraft/network/protocol/game/ClientboundRecipePacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void reward$recipes(ClientboundRecipePacket packet, CallbackInfo callback) {
        P11C4aRewardContinuityClientProbe.recipes((ClientPacketListener) (Object) this, packet);
    }
}
