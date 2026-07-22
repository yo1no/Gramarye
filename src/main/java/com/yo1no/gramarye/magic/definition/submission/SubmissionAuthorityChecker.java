package com.yo1no.gramarye.magic.definition.submission;

import java.util.Objects;

/** Applies authoritative identity pairing and optimistic-base checks without side effects. */
final class SubmissionAuthorityChecker {
    SubmissionAuthorityCheck check(
            DraftSubmissionPrecheck.Ready precheck,
            SkillSubmissionAuthorizationResult authorization) {
        Objects.requireNonNull(precheck, "precheck");
        Objects.requireNonNull(authorization, "authorization");
        var draft = precheck.input().draft();
        SubmissionAuthorityInvariants.requireMatchingSkillId(draft, authorization);

        return switch (authorization) {
            case SkillSubmissionAuthorizationResult.Rejected rejected ->
                    new SubmissionAuthorityCheck.IdentityRejected(
                            rejected, precheck.report());
            case SkillSubmissionAuthorizationResult.Authorized authorized -> {
                SubmissionAuthorityInvariants.requireMatchingSkillId(draft, authorized.state());
                var conflict = SubmissionConcurrency.classify(draft, authorized.state());
                yield conflict.<SubmissionAuthorityCheck>map(value ->
                                new SubmissionAuthorityCheck.Conflict(
                                        value, precheck.report()))
                        .orElseGet(() -> new SubmissionAuthorityCheck.Passed(
                                precheck, authorized));
            }
        };
    }
}
