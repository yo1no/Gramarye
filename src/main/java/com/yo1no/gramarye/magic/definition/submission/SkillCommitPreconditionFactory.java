package com.yo1no.gramarye.magic.definition.submission;

import java.util.Objects;

/** Derives a commit precondition from one authoritative preparation snapshot. */
final class SkillCommitPreconditionFactory {
    private SkillCommitPreconditionFactory() {
    }

    static SkillCommitPrecondition from(AuthorizedSkillState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case AuthorizedSkillState.New newState ->
                    new SkillCommitPrecondition.ExpectedAbsent(newState.skillId());
            case AuthorizedSkillState.Existing existing ->
                    new SkillCommitPrecondition.ExpectedLatest(
                            existing.latestStoredRevision());
        };
    }
}
