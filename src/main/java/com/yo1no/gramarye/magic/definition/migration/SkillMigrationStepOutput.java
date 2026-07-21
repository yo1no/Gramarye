package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** A migration step output contains data only; pipeline facts are owned by the orchestrator. */
public record SkillMigrationStepOutput(Dynamic<?> migratedTree) {
    public SkillMigrationStepOutput {
        Objects.requireNonNull(migratedTree, "migratedTree");
    }

    @Override
    public String toString() {
        return "SkillMigrationStepOutput[ops=" + migratedTree.getOps().getClass().getName() + "]";
    }
}
