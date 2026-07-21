package com.yo1no.gramarye.magic.definition.migration;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

/** One pure adjacent transformation of a descriptor-owned serialized payload. */
public interface PayloadMigrationStep {
    int fromVersion();

    int toVersion();

    <T> DataResult<PayloadMigrationStepOutput<T>> migrate(Dynamic<T> defensivePayloadCopy);
}
