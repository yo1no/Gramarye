package com.yo1no.gramarye;

import net.minecraft.server.level.ServerPlayer;

/** Finite recipe INIT consumer, invoked only after the original successful respawn body. */
public final class P11RecipeDelivery {
    private P11RecipeDelivery() {}

    public static void afterRespawn(ServerPlayer player) {
        if (P11NativeStorageBoundary.managedRecipeReceiver(player)) {
            // Native INIT serializes current known/highlight/settings; it never adds recipes.
            // The original copy may have overwritten recipes announced by prepared-B
            // callbacks. Every successful replacement therefore gets this one current INIT.
            // The native send seam retains a pending bit when its connection is unavailable.
            player.getRecipeBook().sendInitialRecipeBook(player);
        }
    }

    public interface Access {
        boolean p11$needsRecipeSync();
    }
}
