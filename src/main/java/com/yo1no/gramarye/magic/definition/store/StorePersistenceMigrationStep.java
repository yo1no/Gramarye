package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.Tag;

/** One pure adjacent transformation of the physical Store envelope. */
interface StorePersistenceMigrationStep {
    int fromVersion();

    int toVersion();

    DataResult<StorePersistenceMigrationStepOutput> migrate(Dynamic<Tag> defensiveSourceCopy);
}

record StorePersistenceMigrationStepOutput(Dynamic<Tag> migratedTree) {
    StorePersistenceMigrationStepOutput {
        java.util.Objects.requireNonNull(migratedTree, "migratedTree");
    }

    @Override
    public String toString() {
        return "StorePersistenceMigrationStepOutput[treePresent=true]";
    }
}
