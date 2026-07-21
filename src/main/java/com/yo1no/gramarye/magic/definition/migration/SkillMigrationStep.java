package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

/**
 * One pure skill-document schema transformation. Plans accept it only when it describes the
 * adjacent edge {@code fromVersion() -> fromVersion() + 1}. Implementations transform the supplied
 * defensive copy without mutating external state, and output must retain the input's exact {@link
 * com.mojang.serialization.DynamicOps} instance.
 */
public interface SkillMigrationStep {
    int fromVersion();

    int toVersion();

    DataResult<SkillMigrationStepOutput> migrate(Dynamic<?> defensiveSourceCopy);
}
