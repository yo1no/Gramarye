package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStore;
import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.definition.store.SkillStoreCommitResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillDefinitionStoreCommitBoundaryTest {
    @Test
    void commitUsesThePreparedPlanWithoutRerunningResolutionOrValidation() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));
        var preparation = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
        var outcome = assertInstanceOf(
                SkillSubmissionOutcome.Prepared.class,
                SkillSubmissionOutcomeMapper.from(preparation));
        assertStageCounts(stages, 1, 1, 1, 1);

        var result = new SkillDefinitionStore().commit(
                outcome.plan(), SkillQuota.Unlimited.INSTANCE);

        assertAll(
                () -> assertInstanceOf(SkillStoreCommitResult.Committed.class, result),
                () -> assertStageCounts(stages, 1, 1, 1, 1));
    }

    private static void assertStageCounts(
            SubmissionPreparationTestFixtures.CountingStages stages,
            int formalize,
            int resolve,
            int analyze,
            int project) {
        assertAll(
                () -> assertEquals(formalize, stages.formalizeCalls()),
                () -> assertEquals(resolve, stages.resolveCalls()),
                () -> assertEquals(analyze, stages.analyzeCalls()),
                () -> assertEquals(project, stages.projectCalls()));
    }
}
