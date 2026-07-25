package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.validation.P4A3ProbeValidatedDefinitionFactory;
import java.util.Optional;

/** Test-only legal-plan factory that retains every production plan invariant. */
public final class P4A3ProbePlanFactory {
    private P4A3ProbePlanFactory() {
    }

    public static SkillSubmissionPlan forNew(
            SkillOwnerId owner,
            SkillDocument proposedDocument) {
        if (proposedDocument.revision().value() != 0) {
            throw new IllegalArgumentException("new plan must propose revision zero");
        }
        return create(
                owner,
                proposedDocument,
                Optional.empty(),
                new AuthorizedSkillState.New(proposedDocument.skillId()));
    }

    public static SkillSubmissionPlan forExisting(
            SkillOwnerId owner,
            SkillReference latest,
            SkillDocument proposedDocument) {
        return create(
                owner,
                proposedDocument,
                Optional.of(latest.revision()),
                new AuthorizedSkillState.Existing(latest));
    }

    private static SkillSubmissionPlan create(
            SkillOwnerId owner,
            SkillDocument document,
            Optional<com.yo1no.gramarye.magic.api.id.SkillRevision> baseRevision,
            AuthorizedSkillState state) {
        var draftNodes = document.nodes().stream()
                .map(node -> new DraftNode(
                        DraftTriggerSlot.present(node.trigger()),
                        DraftActionSlot.present(node.action()),
                        node.appearanceOverride()))
                .toList();
        var draft = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                document.skillId(),
                baseRevision,
                draftNodes,
                document.appearance());
        var input = SkillSubmissionInput.direct(draft);
        var precheck = (DraftSubmissionPrecheck.Ready)
                new DraftSubmissionPrechecker().check(input);
        var authority = (SubmissionAuthorityCheck.Passed)
                new SubmissionAuthorityChecker().check(
                        precheck,
                        new SkillSubmissionAuthorizationResult.Authorized(owner, state));
        var prepared = new SubmissionPreparationCheck.Prepared(
                authority,
                document,
                P4A3ProbeValidatedDefinitionFactory.create(document),
                authority.report());
        return SkillSubmissionPlan.from(prepared);
    }
}
