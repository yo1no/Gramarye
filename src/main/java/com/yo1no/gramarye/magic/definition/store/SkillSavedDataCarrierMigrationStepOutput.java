package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.Dynamic;
import java.util.Objects;
import net.minecraft.nbt.Tag;

/** Defensive step output for the raw-free outer-carrier migration tree. */
record SkillSavedDataCarrierMigrationStepOutput(Dynamic<Tag> migratedTree) {
    SkillSavedDataCarrierMigrationStepOutput {
        Objects.requireNonNull(migratedTree, "migratedTree");
    }

    @Override
    public String toString() {
        return "SkillSavedDataCarrierMigrationStepOutput[treePresent=true]";
    }
}
