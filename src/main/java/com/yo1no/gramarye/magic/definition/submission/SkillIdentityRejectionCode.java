package com.yo1no.gramarye.magic.definition.submission;

/** Privacy-preserving reasons for refusing a skill identity at submission admission. */
public enum SkillIdentityRejectionCode {
    /**
     * Opaque authorization rejection. It does not reveal whether the requested SkillId exists,
     * who owns it, or its latest revision.
     */
    NOT_AUTHORIZED,

    /**
     * The authenticated submitter has reached an applicable skill quota.
     *
     * <p>P3-C2 defines this as dormant machine-readable vocabulary only. This phase has no
     * production branch that produces it and performs no quota lookup. A future authoritative
     * adapter may perform an advisory check; P3-D commit must perform the final check against
     * authoritative committed state. This reason concerns the authenticated submitter's own
     * quota and reveals nothing about another skill's existence, owner, or revision.
     */
    QUOTA_EXCEEDED
}
