package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkillSubmissionPreparationPipelineTest {
    @Test
    void invalidPrecheckInvokesOnlyDirectPrecheckAndOneTerminalMapper() {
        var stages = stages();
        var pipeline = new SkillSubmissionPreparationPipeline(stages);
        var draft = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION + 1,
                SubmissionAuthorityTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(),
                com.yo1no.gramarye.magic.definition.document.AppearanceDocument.Default.INSTANCE);

        var invalid = assertInstanceOf(
                DraftSubmissionPrecheck.Invalid.class,
                pipeline.precheck(draft));
        var outcome = pipeline.map(invalid);

        assertInstanceOf(SkillSubmissionOutcome.Invalid.class, outcome);
        assertSame(invalid.report(), outcome.report());
        assertSame(draft, stages.lastInput.draft());
        assertTrue(stages.lastInput.readReport().facts().isEmpty());
        assertCounts(stages, 1, 0, 0, 1, 0, 0, 0);
    }

    @Test
    void identityAndConflictPathsShortCircuitPreparationAndMapOnce() {
        var identityStages = stages();
        var identityPipeline = new SkillSubmissionPreparationPipeline(identityStages);
        var draft = SubmissionAuthorityTestFixtures.draft(Optional.empty());
        var ready = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                identityPipeline.precheck(draft));
        var rejected = assertInstanceOf(
                SubmissionAuthorityCheck.IdentityRejected.class,
                identityPipeline.checkAuthority(
                        ready,
                        new SkillSubmissionAuthorizationResult.Rejected(
                                draft.skillId(), SkillIdentityRejectionCode.NOT_AUTHORIZED)));
        var identityOutcome = identityPipeline.map(rejected);
        assertInstanceOf(SkillSubmissionOutcome.IdentityRejected.class, identityOutcome);
        assertSame(rejected.report(), identityOutcome.report());
        assertCounts(identityStages, 1, 1, 0, 0, 1, 0, 0);

        var conflictStages = stages();
        var conflictPipeline = new SkillSubmissionPreparationPipeline(conflictStages);
        var conflictDraft = SubmissionAuthorityTestFixtures.draft(
                Optional.of(new SkillRevision(4)));
        var conflictReady = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                conflictPipeline.precheck(conflictDraft));
        var conflict = assertInstanceOf(
                SubmissionAuthorityCheck.Conflict.class,
                conflictPipeline.checkAuthority(
                        conflictReady,
                        SubmissionAuthorityTestFixtures.authorizedNew(conflictDraft.skillId())));
        var conflictOutcome = conflictPipeline.map(conflict);
        assertInstanceOf(SkillSubmissionOutcome.Conflict.class, conflictOutcome);
        assertSame(conflict.report(), conflictOutcome.report());
        assertCounts(conflictStages, 1, 1, 0, 0, 0, 1, 0);
    }

    @Test
    void passedPathInvokesEveryHighLevelStageAndPreparationMapperExactlyOnce() {
        var stages = stages();
        var pipeline = new SkillSubmissionPreparationPipeline(stages);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var ready = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                pipeline.precheck(draft));
        var passed = assertInstanceOf(
                SubmissionAuthorityCheck.Passed.class,
                pipeline.checkAuthority(
                        ready,
                        SubmissionAuthorityTestFixtures.authorizedNew(draft.skillId())));

        var outcome = pipeline.prepareAndMap(
                passed, SubmissionPreparationTestFixtures.CONTEXT);

        var prepared = assertInstanceOf(SkillSubmissionOutcome.Prepared.class, outcome);
        assertSame(stages.lastPreparation.report(), prepared.report());
        assertCounts(stages, 1, 1, 1, 0, 0, 0, 1);
    }

    @Test
    void preparationInvalidAndRevisionExhaustedEachMapOnceAtTheTerminalStage() {
        var invalidStages = stages();
        var invalidPipeline = new SkillSubmissionPreparationPipeline(invalidStages);
        var incomplete = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var incompleteReady = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                invalidPipeline.precheck(incomplete));
        var incompletePassed = assertInstanceOf(
                SubmissionAuthorityCheck.Passed.class,
                invalidPipeline.checkAuthority(
                        incompleteReady,
                        SubmissionAuthorityTestFixtures.authorizedNew(incomplete.skillId())));
        assertInstanceOf(
                SkillSubmissionOutcome.Invalid.class,
                invalidPipeline.prepareAndMap(
                        incompletePassed, SubmissionPreparationTestFixtures.CONTEXT));
        assertInstanceOf(SubmissionPreparationCheck.Invalid.class,
                invalidStages.lastPreparation);
        assertCounts(invalidStages, 1, 1, 1, 0, 0, 0, 1);

        var exhaustedStages = stages();
        var exhaustedPipeline = new SkillSubmissionPreparationPipeline(exhaustedStages);
        var exhaustedDraft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.of(new SkillRevision(Integer.MAX_VALUE)));
        var exhaustedReady = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                exhaustedPipeline.precheck(exhaustedDraft));
        var exhaustedPassed = assertInstanceOf(
                SubmissionAuthorityCheck.Passed.class,
                exhaustedPipeline.checkAuthority(
                        exhaustedReady,
                        SubmissionAuthorityTestFixtures.authorizedExisting(
                                exhaustedDraft.skillId(), Integer.MAX_VALUE)));
        assertInstanceOf(
                SkillSubmissionOutcome.RevisionExhausted.class,
                exhaustedPipeline.prepareAndMap(
                        exhaustedPassed, SubmissionPreparationTestFixtures.CONTEXT));
        assertInstanceOf(SubmissionPreparationCheck.RevisionExhausted.class,
                exhaustedStages.lastPreparation);
        assertCounts(exhaustedStages, 1, 1, 1, 0, 0, 0, 1);
    }

    @Test
    void productionAdapterPathUsesTheExistingStagesWithoutRawIngress() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var pipeline = new SkillSubmissionPreparationPipeline(
                components.resolver, components.analyzer, components.projector);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var ready = assertInstanceOf(
                DraftSubmissionPrecheck.Ready.class,
                pipeline.precheck(draft));
        var passed = assertInstanceOf(
                SubmissionAuthorityCheck.Passed.class,
                pipeline.checkAuthority(
                        ready,
                        SubmissionAuthorityTestFixtures.authorizedNew(draft.skillId())));

        assertInstanceOf(
                SkillSubmissionOutcome.Prepared.class,
                pipeline.prepareAndMap(passed, SubmissionPreparationTestFixtures.CONTEXT));
    }

    @Test
    void nullBoundariesDoNotInvokeDownstreamStages() {
        var stages = stages();
        var pipeline = new SkillSubmissionPreparationPipeline(stages);
        assertThrows(NullPointerException.class, () -> pipeline.precheck(null));
        assertThrows(NullPointerException.class, () ->
                new SkillSubmissionPreparationPipeline(
                        (SkillSubmissionPreparationPipeline.Stages) null));
        assertCounts(stages, 0, 0, 0, 0, 0, 0, 0);
    }

    private static CountingStages stages() {
        return new CountingStages(
                SubmissionPreparationTestFixtures.validPipeline().productionPreparer());
    }

    private static void assertCounts(
            CountingStages stages,
            int precheck,
            int authority,
            int prepare,
            int mapInvalid,
            int mapIdentity,
            int mapConflict,
            int mapPreparation) {
        assertEquals(precheck, stages.precheckCalls);
        assertEquals(authority, stages.authorityCalls);
        assertEquals(prepare, stages.prepareCalls);
        assertEquals(mapInvalid, stages.mapInvalidCalls);
        assertEquals(mapIdentity, stages.mapIdentityCalls);
        assertEquals(mapConflict, stages.mapConflictCalls);
        assertEquals(mapPreparation, stages.mapPreparationCalls);
    }

    private static final class CountingStages
            implements SkillSubmissionPreparationPipeline.Stages {
        private final DraftSubmissionPrechecker prechecker = new DraftSubmissionPrechecker();
        private final SubmissionAuthorityChecker authorityChecker =
                new SubmissionAuthorityChecker();
        private final SkillSubmissionPreparer preparer;
        private int precheckCalls;
        private int authorityCalls;
        private int prepareCalls;
        private int mapInvalidCalls;
        private int mapIdentityCalls;
        private int mapConflictCalls;
        private int mapPreparationCalls;
        private SkillSubmissionInput lastInput;
        private SubmissionPreparationCheck lastPreparation;

        private CountingStages(SkillSubmissionPreparer preparer) {
            this.preparer = preparer;
        }

        @Override
        public DraftSubmissionPrecheck precheck(SkillSubmissionInput input) {
            precheckCalls++;
            lastInput = input;
            return prechecker.check(input);
        }

        @Override
        public SubmissionAuthorityCheck checkAuthority(
                DraftSubmissionPrecheck.Ready ready,
                SkillSubmissionAuthorizationResult authorization) {
            authorityCalls++;
            return authorityChecker.check(ready, authorization);
        }

        @Override
        public SubmissionPreparationCheck prepare(
                SubmissionAuthorityCheck.Passed passed,
                ValidationContext context) {
            prepareCalls++;
            lastPreparation = preparer.prepare(passed, context);
            return lastPreparation;
        }

        @Override
        public SkillSubmissionOutcome map(DraftSubmissionPrecheck.Invalid invalid) {
            mapInvalidCalls++;
            return SkillSubmissionOutcomeMapper.from(invalid);
        }

        @Override
        public SkillSubmissionOutcome map(
                SubmissionAuthorityCheck.IdentityRejected rejected) {
            mapIdentityCalls++;
            return SkillSubmissionOutcomeMapper.from(rejected);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionAuthorityCheck.Conflict conflict) {
            mapConflictCalls++;
            return SkillSubmissionOutcomeMapper.from(conflict);
        }

        @Override
        public SkillSubmissionOutcome map(SubmissionPreparationCheck preparation) {
            mapPreparationCalls++;
            return SkillSubmissionOutcomeMapper.from(preparation);
        }
    }
}
