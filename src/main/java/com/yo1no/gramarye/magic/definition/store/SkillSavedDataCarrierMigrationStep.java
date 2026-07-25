package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.Tag;

/** One pure adjacent transformation of the raw-free SavedData outer carrier view. */
interface SkillSavedDataCarrierMigrationStep {
    int fromVersion();

    int toVersion();

    DataResult<SkillSavedDataCarrierMigrationStepOutput> migrate(
            Dynamic<Tag> defensiveSourceCopy);
}
