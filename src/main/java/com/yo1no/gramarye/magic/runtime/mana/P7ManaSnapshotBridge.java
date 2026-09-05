package com.yo1no.gramarye.magic.runtime.mana;

import com.yo1no.gramarye.P6RuntimeExecutionCapability;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Capability-gated scalar observation of the existing player mana Attachment. */
public final class P7ManaSnapshotBridge {
    private P7ManaSnapshotBridge() {}

    public static long observeBalance(
            P6RuntimeExecutionCapability capability, ServerPlayer actor) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(actor, "actor");
        var account = new PlayerManaAccountAccess(actor);
        if (!account.isLogicThread()) {
            throw new IllegalStateException("P7_MANA_OBSERVATION_REQUIRES_SERVER_THREAD");
        }
        var state = ManaAttachments.state(actor);
        return switch (state.availability()) {
            case AVAILABLE -> state.balance();
            case UNAVAILABLE -> -1L;
        };
    }
}
