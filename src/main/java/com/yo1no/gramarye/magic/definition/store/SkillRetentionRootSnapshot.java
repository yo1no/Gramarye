package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded declaration of external revision roots for one Store reclaim call.
 *
 * <p>A {@link Complete} value is a caller claim that every authoritative external root was
 * captured completely and within the hard ceiling. It is not an authorization capability. The
 * snapshot is only fresh for immediate use by authoritative server composition in the same
 * Store, world, and logic-thread call chain in which it was captured. It must not cross a tick,
 * restart, reload, Store, or world boundary, and no callback that can create a root may run
 * between capture and reclaim. Network input must not be treated as a complete snapshot.</p>
 */
public sealed interface SkillRetentionRootSnapshot
        permits SkillRetentionRootSnapshot.Complete,
                SkillRetentionRootSnapshot.Incomplete,
                SkillRetentionRootSnapshot.Truncated,
                SkillRetentionRootSnapshot.OverLimit {
    /**
     * Materializes a claimed-complete root sequence with bounded work.
     *
     * <p>Order and duplicates are preserved. At most the hard ceiling plus one input element is
     * consumed; observing that extra element returns {@link OverLimit} immediately.</p>
     */
    static SkillRetentionRootSnapshot fromCompleteRoots(Iterable<SkillReference> roots) {
        Objects.requireNonNull(roots, "roots");
        var maximum = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
        var retained = new ArrayList<SkillReference>();
        var iterator = Objects.requireNonNull(roots.iterator(), "roots.iterator()");
        while (iterator.hasNext()) {
            var root = Objects.requireNonNull(iterator.next(), "root");
            if (retained.size() == maximum) {
                return new OverLimit(maximum + 1, maximum);
            }
            retained.add(root);
        }
        return new Complete(retained);
    }

    /** A bounded, immutable, caller-claimed complete root sequence. */
    final class Complete implements SkillRetentionRootSnapshot {
        private final List<SkillReference> roots;

        private Complete(List<SkillReference> roots) {
            Objects.requireNonNull(roots, "roots");
            if (roots.size() > MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM) {
                throw new IllegalArgumentException(
                        "complete roots exceed the canonical retention-root ceiling");
            }
            this.roots = List.copyOf(roots);
        }

        /** Returns the immutable roots in their original order, including duplicates. */
        public List<SkillReference> roots() {
            return roots;
        }

        @Override
        public String toString() {
            return "Complete[rootCount=" + roots.size() + "]";
        }
    }

    /** The caller cannot prove that every authoritative external root was captured. */
    enum Incomplete implements SkillRetentionRootSnapshot {
        INSTANCE
    }

    /** Root capture stopped before the caller's complete source was exhausted. */
    enum Truncated implements SkillRetentionRootSnapshot {
        INSTANCE
    }

    /** Root capture observed more entries than the canonical reclaim hard ceiling permits. */
    record OverLimit(int observedAtLeast, int maximum) implements SkillRetentionRootSnapshot {
        public OverLimit {
            if (observedAtLeast < 0 || maximum < 0 || observedAtLeast <= maximum) {
                throw new IllegalArgumentException(
                        "over-limit metadata requires non-negative observedAtLeast > maximum");
            }
            if (maximum != MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM) {
                throw new IllegalArgumentException(
                        "maximum must match the canonical retention-root ceiling");
            }
        }
    }
}
