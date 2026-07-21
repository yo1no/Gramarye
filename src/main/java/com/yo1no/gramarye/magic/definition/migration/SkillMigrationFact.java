package com.yo1no.gramarye.magic.definition.migration;

import java.util.Objects;
import java.util.OptionalInt;

/** Bounded non-persistent provenance for one applied adjacent migration step. */
public record SkillMigrationFact(
        SkillMigrationFactCode code,
        int fromVersion,
        int toVersion,
        OptionalInt stepIndex) {
    public SkillMigrationFact {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(stepIndex, "stepIndex");
        if (fromVersion < 0 || fromVersion == Integer.MAX_VALUE || toVersion != fromVersion + 1) {
            throw new IllegalArgumentException("fact versions must describe an adjacent non-negative edge");
        }
        if (stepIndex.isPresent() && stepIndex.getAsInt() < 0) {
            throw new IllegalArgumentException("stepIndex must be non-negative");
        }
    }
}
