package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Machine-readable result of one in-memory Store commit attempt. */
public sealed interface SkillStoreCommitResult
        permits SkillStoreCommitResult.Committed,
                SkillStoreCommitResult.Conflict,
                SkillStoreCommitResult.QuotaRejected,
                SkillStoreCommitResult.CapacityRejected,
                SkillStoreCommitResult.OwnerRejected {
    /**
     * The revision was formally allocated and inserted into the current in-memory domain Store
     * aggregate.
     *
     * <p>This does not mean that SavedData has been encoded or written, that {@code setDirty()} has
     * been called, that a player Attachment has been updated, or that cross-location
     * reconciliation has completed.</p>
     */
    record Committed(SkillReference committed) implements SkillStoreCommitResult {
        public Committed {
            Objects.requireNonNull(committed, "committed");
        }
    }

    record Conflict(SkillStoreCommitConflict conflict) implements SkillStoreCommitResult {
        public Conflict {
            Objects.requireNonNull(conflict, "conflict");
        }
    }

    record QuotaRejected(
            SkillId skillId,
            int current,
            int maximum) implements SkillStoreCommitResult {
        public QuotaRejected {
            Objects.requireNonNull(skillId, "skillId");
            if (current < 0 || maximum < 0 || current < maximum) {
                throw new IllegalArgumentException(
                        "quota metadata requires non-negative current >= maximum");
            }
            if (maximum > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER) {
                throw new IllegalArgumentException(
                        "quota maximum exceeds the Store owner hard ceiling");
            }
        }
    }

    record CapacityRejected(
            SkillStoreCapacityScope scope,
            int current,
            int maximum) implements SkillStoreCommitResult {
        public CapacityRejected {
            Objects.requireNonNull(scope, "scope");
            if (current < 0 || maximum < 0 || current < maximum) {
                throw new IllegalArgumentException(
                        "capacity metadata requires non-negative current >= maximum");
            }
            if (maximum != scope.canonicalMaximum()) {
                throw new IllegalArgumentException(
                        "capacity maximum must match the canonical scope ceiling");
            }
        }
    }

    record OwnerRejected(SkillId skillId) implements SkillStoreCommitResult {
        public OwnerRejected {
            Objects.requireNonNull(skillId, "skillId");
        }
    }
}
