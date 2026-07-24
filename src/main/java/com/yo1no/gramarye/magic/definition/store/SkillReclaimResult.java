package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Typed result of one in-memory Store reclaim attempt. */
public sealed interface SkillReclaimResult
        permits SkillReclaimResult.Completed, SkillReclaimResult.Rejected {
    /**
     * The complete root set was accepted and every history was scanned.
     *
     * <p>A zero-reclaim completion is legal. Future persistence composition should mark dirty
     * only when {@link SkillReclaimReport#revisionsReclaimed()} is greater than zero.</p>
     */
    record Completed(SkillReclaimReport report) implements SkillReclaimResult {
        public Completed {
            Objects.requireNonNull(report, "report");
        }
    }

    /** The attempt was rejected before either Store histories or active pins were modified. */
    record Rejected(SkillReclaimFailure failure) implements SkillReclaimResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
