package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.util.Objects;

/** Checked control signal preserving compressed-budget precedence through the NBT parser. */
final class P4E1CompressedCapacityRejected extends IOException {
    private final P4E1AuditBudget.Exceeded exceeded;

    P4E1CompressedCapacityRejected(P4E1AuditBudget.Exceeded exceeded) {
        super("P4-E1 compressed capacity exceeded");
        this.exceeded = Objects.requireNonNull(exceeded, "exceeded");
    }

    P4E1AuditBudget.Exceeded exceeded() {
        return exceeded;
    }
}
