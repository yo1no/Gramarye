package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Objects;

/** Shared programming invariants for C2 authority checks and stage tokens. */
final class SubmissionAuthorityInvariants {
    private SubmissionAuthorityInvariants() {
    }

    static void requireMatchingSkillId(
            SkillDraft draft,
            SkillSubmissionAuthorizationResult authorization) {
        Objects.requireNonNull(authorization, "authorization");
        requireMatchingSkillId(draft, authorization.skillId());
    }

    static void requireMatchingSkillId(SkillDraft draft, AuthorizedSkillState state) {
        Objects.requireNonNull(state, "state");
        requireMatchingSkillId(draft, state.skillId());
    }

    static ValidationResult requireWarningOnly(ValidationResult report) {
        Objects.requireNonNull(report, "report");
        if (report.hasErrors()) {
            throw new IllegalArgumentException("authority-stage report cannot contain an error");
        }
        return report;
    }

    private static void requireMatchingSkillId(SkillDraft draft, SkillId describedSkillId) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(describedSkillId, "describedSkillId");
        if (!draft.skillId().equals(describedSkillId)) {
            throw new IllegalArgumentException(
                    "authorization snapshot does not describe the submitted Draft SkillId");
        }
    }
}
