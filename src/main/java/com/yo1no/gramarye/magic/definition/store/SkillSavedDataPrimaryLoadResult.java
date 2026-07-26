package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Total result of one bounded primary-path inspection and, when present, parse. */
sealed interface SkillSavedDataPrimaryLoadResult
        permits SkillSavedDataPrimaryLoadResult.Absent,
                SkillSavedDataPrimaryLoadResult.Ready,
                SkillSavedDataPrimaryLoadResult.Failure {
    enum Absent implements SkillSavedDataPrimaryLoadResult {
        INSTANCE
    }

    record Ready(SkillSavedDataReadyCandidate candidate)
            implements SkillSavedDataPrimaryLoadResult {
        public Ready {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record Failure(SkillSavedDataPrimaryFailure failure)
            implements SkillSavedDataPrimaryLoadResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
