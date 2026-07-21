package com.yo1no.gramarye.magic.definition.migration;

import java.util.Objects;

/** Non-persistent outcome after a safe raw snapshot has entered skill-level migration. */
public sealed interface SkillMigrationResult
        permits SkillMigrationResult.Success, SkillMigrationResult.Failure {
    record Success(
            RawSkillDocumentSnapshot migratedSnapshot,
            PipelineFactReport factReport) implements SkillMigrationResult {
        public Success {
            Objects.requireNonNull(migratedSnapshot, "migratedSnapshot");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    record Failure(
            RawSkillDocumentSnapshot originalSnapshot,
            SkillMigrationFailure failure,
            PipelineFactReport factReport) implements SkillMigrationResult {
        public Failure {
            Objects.requireNonNull(originalSnapshot, "originalSnapshot");
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }
    }
}
