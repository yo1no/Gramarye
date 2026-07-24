package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Bounded machine-readable reason why a Store reclaim attempt was rejected. */
public sealed interface SkillReclaimFailure
        permits SkillReclaimFailure.IncompleteRootSnapshot,
                SkillReclaimFailure.TruncatedRootSnapshot,
                SkillReclaimFailure.RootCapacityExceeded,
                SkillReclaimFailure.MissingExternalRoot {
    enum IncompleteRootSnapshot implements SkillReclaimFailure {
        INSTANCE
    }

    enum TruncatedRootSnapshot implements SkillReclaimFailure {
        INSTANCE
    }

    record RootCapacityExceeded(int observedAtLeast, int maximum)
            implements SkillReclaimFailure {
        public RootCapacityExceeded {
            if (observedAtLeast < 0 || maximum < 0 || observedAtLeast <= maximum) {
                throw new IllegalArgumentException(
                        "capacity metadata requires non-negative observedAtLeast > maximum");
            }
            if (maximum != MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM) {
                throw new IllegalArgumentException(
                        "maximum must match the canonical retention-root ceiling");
            }
        }
    }

    record MissingExternalRoot(SkillReference reference) implements SkillReclaimFailure {
        public MissingExternalRoot {
            Objects.requireNonNull(reference, "reference");
        }
    }
}
