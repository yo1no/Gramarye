package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.U0OrdinaryTrace;
import net.minecraft.server.level.ServerPlayer;

/** Qualification only: never installs a default, returns state, or mutates an account. */
public final class U0OrdinaryManaObservation {
    private U0OrdinaryManaObservation() { }
    public static void recordExisting(ServerPlayer actor, int checkpoint, int role) {
        if (!actor.getServer().isSameThread()) throw new IllegalStateException("U0_MANA_OBSERVATION_THREAD");
        var state = ManaAttachments.existing(actor);
        boolean available = state != null && state.availability() == ManaAvailability.AVAILABLE;
        U0OrdinaryTrace.note("MANA_EXISTING", available ? state.balance() : -1, checkpoint, 0, 0, 0, 0,
                role, state == null ? 0 : 1, available ? 1 : 0,
                state == null ? "ABSENT" : available ? "AVAILABLE" : state.unavailableReason().name());
    }
}
