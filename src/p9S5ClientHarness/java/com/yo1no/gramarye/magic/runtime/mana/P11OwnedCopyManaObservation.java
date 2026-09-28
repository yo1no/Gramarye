package com.yo1no.gramarye.magic.runtime.mana;

import net.minecraft.server.level.ServerPlayer;

/** Excluded read-only observation; never creates a mana state or publication witness. */
public final class P11OwnedCopyManaObservation {
    private P11OwnedCopyManaObservation() {}

    public static boolean isExistingUnavailable(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null || !actor.getServer().isSameThread()) { return false; }
        var state = ManaAttachments.existing(actor);
        return state != null && state.availability() == ManaAvailability.UNAVAILABLE
                && state.unavailableReason() == ManaDecodeFailure.BALANCE_BELOW_MINIMUM;
    }
}
