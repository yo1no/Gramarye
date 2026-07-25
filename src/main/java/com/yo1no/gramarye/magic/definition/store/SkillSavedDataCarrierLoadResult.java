package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.Objects;

/** Total P4-B1 load result; failure can never carry a partial Ready candidate. */
sealed interface SkillSavedDataCarrierLoadResult
        permits SkillSavedDataCarrierLoadResult.Ready,
                SkillSavedDataCarrierLoadResult.Failure {
    record Ready(SkillSavedDataReadyCandidate candidate)
            implements SkillSavedDataCarrierLoadResult {
        public Ready {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record Failure(
            SkillSavedDataCarrierFailure failure,
            PipelineFactReport factReport) implements SkillSavedDataCarrierLoadResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }
    }
}
