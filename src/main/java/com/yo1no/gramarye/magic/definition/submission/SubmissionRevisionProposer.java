package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Pure proposal policy; formal revision allocation belongs to the future P3-D Store commit. */
final class SubmissionRevisionProposer {
    private SubmissionRevisionProposer() {
    }

    static SubmissionRevisionProposal propose(AuthorizedSkillState state) {
        Objects.requireNonNull(state, "state");
        return switch (state) {
            case AuthorizedSkillState.New ignored ->
                    new SubmissionRevisionProposal.Proposed(new SkillRevision(0));
            case AuthorizedSkillState.Existing existing ->
                    proposeAfter(existing.latestStoredRevision());
        };
    }

    private static SubmissionRevisionProposal proposeAfter(SkillReference latest) {
        return latest.revision().successor()
                .<SubmissionRevisionProposal>map(SubmissionRevisionProposal.Proposed::new)
                .orElseGet(() -> new SubmissionRevisionProposal.Exhausted(latest));
    }
}
