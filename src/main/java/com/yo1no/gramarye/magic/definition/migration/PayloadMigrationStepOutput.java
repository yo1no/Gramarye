package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Data-only payload migration output; the orchestrator owns schema advancement and facts. */
public record PayloadMigrationStepOutput<T>(Dynamic<T> migratedPayload) {
    public PayloadMigrationStepOutput {
        Objects.requireNonNull(migratedPayload, "migratedPayload");
    }

    @Override
    public String toString() {
        return "PayloadMigrationStepOutput[ops="
                + migratedPayload.getOps().getClass().getName() + "]";
    }
}
