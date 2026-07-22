package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;

/** Server-side port for minting candidate skill identities. */
public interface SkillIdSource {
    /**
     * Returns a newly generated server-side candidate SkillId.
     *
     * <p>Expected call context is the server logic thread. Implementations are not required by
     * this domain contract to be safe for concurrent calls. Implementations should generate a
     * fresh candidate on each invocation rather than replaying a cached or previously issued
     * value. Accidental duplicate IDs remain possible and are resolved only by the future P3-D
     * ExpectedAbsent compare-and-insert boundary.
     *
     * <p>Implementations must not return {@code null}. The returned ID is not a Store reservation,
     * ownership proof, submission credential, or committed identity.
     */
    SkillId nextSkillId();
}
