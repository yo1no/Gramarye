package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11CanonicalAdvancements;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.P11RecipeDelivery;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.ServerRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Only presentation is sunk; logical recipe mutation and its criterion already ran natively. */
@Mixin(ServerRecipeBook.class)
abstract class P11RecipeBookMixin implements P11RecipeDelivery.Access {
    @Unique private final P11CanonicalAdvancements.Delivery p11$delivery =
            new P11CanonicalAdvancements.Delivery(false);

    @Override
    public boolean p11$needsRecipeSync() { return p11$delivery.pending(); }

    @WrapOperation(method = "sendRecipes(Lnet/minecraft/network/protocol/game/ClientboundRecipePacket$State;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$recipePresentation(ServerGamePacketListenerImpl listener, Packet<?> packet,
            Operation<Void> original, ClientboundRecipePacket.State state, ServerPlayer player,
            List<ResourceLocation> recipes) {
        if (player.getRecipeBook() == (Object) this && P11NativeStorageBoundary.managedRecipeReceiver(player)
                && !P11NativeStorageBoundary.nativeDeliveryEligible(player)) {
            p11$delivery.requireInitial();
            return;
        }
        // A genuine transport fault retains its native propagation, including Error policy.
        original.call(listener, packet);
    }

    @WrapMethod(method = "sendInitialRecipeBook(Lnet/minecraft/server/level/ServerPlayer;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$initial(ServerPlayer player, Operation<Void> original) {
        if (player.getRecipeBook() != (Object) this || !P11NativeStorageBoundary.managedRecipeReceiver(player)) {
            original.call(player);
            return;
        }
        if (!P11NativeStorageBoundary.nativeDeliveryEligible(player)) {
            p11$delivery.requireInitial();
            return;
        }
        long generation = p11$delivery.generation();
        var connection = player.connection;
        boolean normal = false;
        try {
            original.call(player);
            normal = true;
        } finally {
            if (normal && player.connection == connection
                    && P11NativeStorageBoundary.nativeDeliveryEligible(player)) {
                p11$delivery.submitted(generation);
            } else {
                p11$delivery.requireInitial();
            }
        }
    }
}
