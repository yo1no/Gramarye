package com.yo1no.gramarye.magic.definition.submission;

import java.util.Objects;

/** Maps completed or short-circuited C1-C3 stage tokens to the public preparation result. */
final class SkillSubmissionOutcomeMapper {
    private SkillSubmissionOutcomeMapper() {
    }

    static SkillSubmissionOutcome.Invalid from(DraftSubmissionPrecheck.Invalid invalid) {
        Objects.requireNonNull(invalid, "invalid");
        return new SkillSubmissionOutcome.Invalid(invalid.report());
    }

    static SkillSubmissionOutcome.IdentityRejected from(
            SubmissionAuthorityCheck.IdentityRejected rejected) {
        Objects.requireNonNull(rejected, "rejected");
        return new SkillSubmissionOutcome.IdentityRejected(
                rejected.rejection(), rejected.report());
    }

    static SkillSubmissionOutcome.Conflict from(SubmissionAuthorityCheck.Conflict conflict) {
        Objects.requireNonNull(conflict, "conflict");
        return new SkillSubmissionOutcome.Conflict(conflict.conflict(), conflict.report());
    }

    static SkillSubmissionOutcome from(SubmissionPreparationCheck preparation) {
        Objects.requireNonNull(preparation, "preparation");
        return switch (preparation) {
            case SubmissionPreparationCheck.Prepared prepared ->
                    new SkillSubmissionOutcome.Prepared(
                            SkillSubmissionPlan.from(prepared), prepared.report());
            case SubmissionPreparationCheck.Invalid invalid ->
                    new SkillSubmissionOutcome.Invalid(invalid.report());
            case SubmissionPreparationCheck.RevisionExhausted exhausted ->
                    new SkillSubmissionOutcome.RevisionExhausted(
                            exhausted.latest(), exhausted.report());
        };
    }
}
