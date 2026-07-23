package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;
import java.util.Optional;

/** Test-only bridge that obtains legal plans through the complete C1-C4 preparation path. */
public final class SubmissionPlanTestFactory {
    private SubmissionPlanTestFactory() {
    }

    public static SkillSubmissionPlan newPlan(SkillId skillId, SkillOwnerId owner) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(owner, "owner");
        return prepare(
                skillId,
                Optional.empty(),
                new AuthorizedSkillState.New(skillId),
                owner);
    }

    public static SkillSubmissionPlan existingPlan(
            SkillId skillId,
            SkillOwnerId owner,
            SkillRevision latestRevision) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(latestRevision, "latestRevision");
        return prepare(
                skillId,
                Optional.of(latestRevision),
                new AuthorizedSkillState.Existing(
                        new SkillReference(skillId, latestRevision)),
                owner);
    }

    private static SkillSubmissionPlan prepare(
            SkillId skillId,
            Optional<SkillRevision> baseRevision,
            AuthorizedSkillState state,
            SkillOwnerId owner) {
        var draft = SubmissionPreparationTestFixtures.completeDraft(skillId, baseRevision);
        var input = SkillSubmissionInput.direct(draft);
        var authority = SubmissionPreparationTestFixtures.passed(
                input,
                new SkillSubmissionAuthorizationResult.Authorized(owner, state));
        var preparation = SubmissionPreparationTestFixtures.validPipeline()
                .productionPreparer()
                .prepare(authority, SubmissionPreparationTestFixtures.CONTEXT);
        var outcome = SkillSubmissionOutcomeMapper.from(preparation);
        if (!(outcome instanceof SkillSubmissionOutcome.Prepared prepared)) {
            throw new AssertionError("Legal test fixture did not produce a prepared submission");
        }
        return prepared.plan();
    }
}
