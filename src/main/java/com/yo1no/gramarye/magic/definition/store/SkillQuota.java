package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

/**
 * Immutable per-owner policy quota snapshot supplied for one commit attempt.
 *
 * <p>The snapshot is not an authorization credential, and neither is the submission plan. The
 * caller must freshly reauthorize before commit. A Store must not retain this snapshot or query a
 * policy provider while committing.</p>
 */
public sealed interface SkillQuota permits SkillQuota.Unlimited, SkillQuota.Limited {
    /** No additional per-owner policy limit; technical Store ceilings still apply. */
    enum Unlimited implements SkillQuota {
        INSTANCE
    }

    /** A per-owner policy limit on distinct active committed skill identities. */
    record Limited(int maxCommittedSkills) implements SkillQuota {
        public Limited {
            if (maxCommittedSkills < 0
                    || maxCommittedSkills > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER) {
                throw new IllegalArgumentException(
                        "maxCommittedSkills must be within the Store owner hard ceiling");
            }
        }
    }
}
