package com.yo1no.gramarye;

import net.minecraft.server.level.ServerPlayer;

/** The only ingress used by the alternate distribution's server scenario. */
final class V0EngineeringEntry {
    private V0SlotStartup exact;

    private V0EngineeringEntry() {}

    static V0EngineeringEntry open(V0SlotStartup startup) {
        var entry = new V0EngineeringEntry();
        entry.exact = startup;
        return entry;
    }

    V0Submission submit(ServerPlayer observedActor, V0LogicalIntent intent) {
        return V0Distribution.submit(exact, observedActor, intent);
    }
}
