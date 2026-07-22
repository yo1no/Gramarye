package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.Objects;

/**
 * Pure data returned by a trusted server authorization adapter for one submission attempt.
 *
 * <p>Public construction does not grant permission. Only a result obtained by trusted server
 * composition may be passed to the submission checker. This type is not a capability token and
 * must never be decoded directly from a client payload. It intentionally has no persistence or
 * network codec.
 */
public sealed interface SkillSubmissionAuthorizationResult
        permits SkillSubmissionAuthorizationResult.Authorized,
                SkillSubmissionAuthorizationResult.Rejected {
    SkillId skillId();

    /** Authorized owner and authoritative new/existing snapshot. */
    record Authorized(
            SkillOwnerId owner,
            AuthorizedSkillState state) implements SkillSubmissionAuthorizationResult {
        public Authorized {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(state, "state");
        }

        @Override
        public SkillId skillId() {
            return state.skillId();
        }
    }

    /** Opaque rejection that retains only the caller-known requested ID and bounded reason. */
    record Rejected(
            SkillId requestedSkillId,
            SkillIdentityRejectionCode reason) implements SkillSubmissionAuthorizationResult {
        public Rejected {
            Objects.requireNonNull(requestedSkillId, "requestedSkillId");
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public SkillId skillId() {
            return requestedSkillId;
        }
    }
}
