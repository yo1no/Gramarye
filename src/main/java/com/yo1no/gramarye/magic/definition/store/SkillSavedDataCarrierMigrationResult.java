package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.Objects;

/** Total result of raw-free SavedData outer-carrier migration and reinsertion. */
sealed interface SkillSavedDataCarrierMigrationResult
        permits SkillSavedDataCarrierMigrationResult.Success,
                SkillSavedDataCarrierMigrationResult.Failure {
    record Success(
            ReinsertedSavedDataCarrier carrier,
            PipelineFactReport factReport,
            boolean migrated) implements SkillSavedDataCarrierMigrationResult {
        public Success {
            Objects.requireNonNull(carrier, "carrier");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    record Failure(
            SkillSavedDataCarrierMigrationFailure failure,
            PipelineFactReport factReport) implements SkillSavedDataCarrierMigrationResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }
    }
}
