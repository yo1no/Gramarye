package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import java.util.Optional;

/** Single optimistic-base classification policy shared by the checker and Passed token. */
final class SubmissionConcurrency {
    private SubmissionConcurrency() {
    }

    static Optional<SkillSubmissionConflict> classify(
            SkillDraft draft,
            AuthorizedSkillState state) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(state, "state");
        SubmissionAuthorityInvariants.requireMatchingSkillId(draft, state);
        return switch (state) {
            case AuthorizedSkillState.New newState -> classifyNew(draft, newState);
            case AuthorizedSkillState.Existing existing ->
                    classifyExisting(draft, existing.latestStoredRevision());
        };
    }

    static void requireAccepted(SkillDraft draft, AuthorizedSkillState state) {
        if (classify(draft, state).isPresent()) {
            throw new IllegalArgumentException(
                    "authority-stage token requires an accepted optimistic base revision");
        }
    }

    private static Optional<SkillSubmissionConflict> classifyNew(
            SkillDraft draft,
            AuthorizedSkillState.New state) {
        if (draft.baseRevision().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SkillSubmissionConflict.BaseRevisionForNew(
                state.skillId(), draft.baseRevision().orElseThrow()));
    }

    private static Optional<SkillSubmissionConflict> classifyExisting(
            SkillDraft draft,
            SkillReference latest) {
        if (draft.baseRevision().isEmpty()) {
            return Optional.of(new SkillSubmissionConflict.MissingBaseForExisting(latest));
        }
        var supplied = draft.baseRevision().orElseThrow();
        var comparison = Integer.compare(supplied.value(), latest.revision().value());
        if (comparison < 0) {
            return Optional.of(new SkillSubmissionConflict.StaleBase(supplied, latest));
        }
        if (comparison > 0) {
            return Optional.of(new SkillSubmissionConflict.FutureBase(supplied, latest));
        }
        return Optional.empty();
    }
}
