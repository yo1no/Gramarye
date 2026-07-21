package com.yo1no.gramarye.magic.definition.migration;

import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadFailure;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import java.util.Objects;

/** Typed, non-persistent outcome of the raw document-to-candidate orchestration boundary. */
public sealed interface SkillResolutionResult
        permits SkillResolutionResult.Success,
                SkillResolutionResult.RawInputRejected,
                SkillResolutionResult.SkillMigrationFailed,
                SkillResolutionResult.ReadFailed {
    record Success(ResolvedSkillCandidate candidate) implements SkillResolutionResult {
        public Success {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record RawInputRejected(SkillMigrationFailure failure) implements SkillResolutionResult {
        public RawInputRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record SkillMigrationFailed(
            SkillMigrationResult.Failure failure) implements SkillResolutionResult {
        public SkillMigrationFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record ReadFailed(SkillDocumentReadFailure failure) implements SkillResolutionResult {
        public ReadFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
