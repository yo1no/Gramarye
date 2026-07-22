package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** C2 result whose Passed variant is the only authority token accepted by P3-C3. */
sealed interface SubmissionAuthorityCheck
        permits SubmissionAuthorityCheck.Passed,
                SubmissionAuthorityCheck.IdentityRejected,
                SubmissionAuthorityCheck.Conflict {
    ValidationResult report();

    record Passed(
            DraftSubmissionPrecheck.Ready precheck,
            SkillSubmissionAuthorizationResult.Authorized authorization)
            implements SubmissionAuthorityCheck {
        public Passed {
            Objects.requireNonNull(precheck, "precheck");
            Objects.requireNonNull(authorization, "authorization");
            SubmissionAuthorityInvariants.requireWarningOnly(precheck.report());
            var draft = precheck.input().draft();
            SubmissionAuthorityInvariants.requireMatchingSkillId(draft, authorization);
            SubmissionAuthorityInvariants.requireMatchingSkillId(draft, authorization.state());
            SubmissionConcurrency.requireAccepted(draft, authorization.state());
        }

        @Override
        public ValidationResult report() {
            return precheck.report();
        }
    }

    record IdentityRejected(
            SkillSubmissionAuthorizationResult.Rejected rejection,
            ValidationResult report) implements SubmissionAuthorityCheck {
        public IdentityRejected {
            Objects.requireNonNull(rejection, "rejection");
            report = SubmissionAuthorityInvariants.requireWarningOnly(report);
        }
    }

    record Conflict(
            SkillSubmissionConflict conflict,
            ValidationResult report) implements SubmissionAuthorityCheck {
        public Conflict {
            Objects.requireNonNull(conflict, "conflict");
            report = SubmissionAuthorityInvariants.requireWarningOnly(report);
        }
    }
}
