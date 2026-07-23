package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraftReadResult;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SkillSubmissionPreparerTest {
    @Test
    void acceptedPreparationInvokesEveryStageExactlyOnceAndBuildsTypedDocument() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var preparer = new SkillSubmissionPreparer(stages);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                2,
                AppearanceDocument.defaultAppearance());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));

        var prepared = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 1, 1, 1, 1),
                () -> assertEquals(SkillDocument.CURRENT_SCHEMA_VERSION,
                        prepared.proposedDocument().schemaVersion()),
                () -> assertEquals(draft.skillId(), prepared.proposedDocument().skillId()),
                () -> assertEquals(new SkillRevision(0), prepared.proposedDocument().revision()),
                () -> assertEquals(draft.nodes().size(), prepared.proposedDocument().nodes().size()),
                () -> assertNodeEnvelopeOrder(draft, prepared.proposedDocument()),
                () -> assertSame(draft.appearance(), prepared.proposedDocument().appearance()),
                () -> assertSame(prepared.proposedDocument(), stages.lastDocument()),
                () -> assertTrue(stages.lastDocumentReadReport().facts().isEmpty()),
                () -> assertFalse(stages.lastDocumentReadReport().truncated()),
                () -> assertEquals(prepared.validatedDefinition().reference(),
                        new com.yo1no.gramarye.magic.definition.document.SkillReference(
                                prepared.proposedDocument().skillId(),
                                prepared.proposedDocument().revision())));
    }

    @Test
    void revisionExhaustionShortCircuitsEveryStageAndPreservesReportIdentity() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var preparer = new SkillSubmissionPreparer(stages);
        var revision = new SkillRevision(Integer.MAX_VALUE);
        var draft = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.of(revision));
        var authority = SubmissionPreparationTestFixtures.passedExisting(
                SkillSubmissionInput.direct(draft), Integer.MAX_VALUE);

        var exhausted = assertInstanceOf(
                SubmissionPreparationCheck.RevisionExhausted.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 0, 0, 0, 0),
                () -> assertSame(authority.report(), exhausted.report()),
                () -> assertEquals(revision, exhausted.latest().revision()));
    }

    @Test
    void formalizationInvalidPreservesReadWarningThenShortCircuitsB2AndB3() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var preparer = new SkillSubmissionPreparer(stages);
        var draft = SubmissionPreparationTestFixtures.draft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(SubmissionTestFixtures.missingTrigger()),
                AppearanceDocument.defaultAppearance());
        var fact = new ReadFact(
                ReadFactCode.INTENSITY_CLAMPED_HIGH,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(),
                Optional.of(AppearanceField.INTENSITY_MILLI));
        var sourceReadReport = new SkillDraftReadReport(List.of(fact), false);
        var input = SkillSubmissionInput.fromReadResult(
                new SkillDraftReadResult(draft, sourceReadReport));
        var authority = SubmissionPreparationTestFixtures.passedNew(input);
        var authorityReportBefore = authority.report();

        var invalid = assertInstanceOf(
                SubmissionPreparationCheck.Invalid.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 1, 0, 0, 0),
                () -> assertEquals(List.of(
                                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                                SkillSubmissionIssueCodes.DRAFT_TRIGGER_MISSING),
                        invalid.report().issues().stream().map(issue -> issue.code()).toList()),
                () -> assertEquals(List.of(
                                "appearance.intensity_milli",
                                "nodes[0].trigger"),
                        invalid.report().issues().stream().map(issue -> issue.path().render()).toList()),
                () -> assertTrue(invalid.report().hasErrors()),
                () -> assertSame(sourceReadReport, input.readReport()),
                () -> assertSame(authorityReportBefore, authority.report()),
                () -> assertNotSame(authority.report(), invalid.report()),
                () -> assertSame(
                        assertInstanceOf(
                                DraftFormalizationResult.Invalid.class,
                                stages.lastFormalizationResult()).report(),
                        invalid.report()),
                () -> assertEquals(1, authority.report().issues().size()));
    }

    @Test
    void emptyDraftRunsTheFullPipelineAndB3AloneRejectsIt() {
        var components = SubmissionPreparationTestFixtures.emptyPipeline();
        var stages = components.countingStages();
        var draft = SubmissionPreparationTestFixtures.emptyDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));

        var invalid = assertInstanceOf(
                SubmissionPreparationCheck.Invalid.class,
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 1, 1, 1, 1),
                () -> assertTrue(invalid.report().issues().stream()
                        .anyMatch(issue -> issue.code().equals(
                                SkillValidationIssueCodes.SKILL_EMPTY_NODES))),
                () -> assertFalse(invalid.report().issues().stream()
                        .anyMatch(issue -> issue.code().toString().equals(
                                "gramarye:draft.empty_nodes"))),
                () -> assertTrue(stages.lastDocument().nodes().isEmpty()));
    }

    @Test
    void unknownDefinitionIsB3InvalidAfterExactlyOnePipelinePass() {
        assertPipelineInvalid(
                SubmissionPreparationTestFixtures.emptyPipeline(),
                SubmissionPreparationTestFixtures.completeDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()));
    }

    @Test
    void payloadMigrationFailureIsB3InvalidAfterExactlyOnePipelinePass() {
        assertPipelineInvalid(
                SubmissionPreparationTestFixtures.pipelineWithVersions(1, 0),
                SubmissionPreparationTestFixtures.completeDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()));
    }

    @Test
    void payloadDecodeFailureIsB3InvalidAfterExactlyOnePipelinePass() {
        var draft = SubmissionPreparationTestFixtures.draft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                List.of(SubmissionPreparationTestFixtures.completeNode(
                        SubmissionPreparationTestFixtures.malformedEnvelope(
                                SubmissionPreparationTestFixtures.TRIGGER_ID, 0),
                        SubmissionPreparationTestFixtures.actionEnvelope(0, 1))),
                AppearanceDocument.defaultAppearance());

        assertPipelineInvalid(SubmissionPreparationTestFixtures.validPipeline(), draft);
    }

    @Test
    void appearanceFallbackWarningStillProducesPrepared() {
        var appearance = new AppearanceDocument.Rejected(
                AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID,
                Optional.empty(),
                1,
                appearance);
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();

        var prepared = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                new SkillSubmissionPreparer(stages).prepare(
                        SubmissionPreparationTestFixtures.passedNew(
                                SkillSubmissionInput.direct(draft)),
                        SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 1, 1, 1, 1),
                () -> assertFalse(prepared.report().hasErrors()),
                () -> assertFalse(prepared.report().warnings().isEmpty()),
                () -> assertSame(appearance, prepared.proposedDocument().appearance()));
    }

    @Test
    void productionAndSeamConstructorsAreEquivalentForPreparedAndRejectedPaths() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var production = components.productionPreparer();
        var seam = new SkillSubmissionPreparer(components.countingStages());

        var productionPrepared = production.prepare(
                SubmissionPreparationTestFixtures.passedNew(SkillSubmissionInput.direct(
                        SubmissionPreparationTestFixtures.completeDraft(
                                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()))),
                SubmissionPreparationTestFixtures.CONTEXT);
        var seamPrepared = seam.prepare(
                SubmissionPreparationTestFixtures.passedNew(SkillSubmissionInput.direct(
                        SubmissionPreparationTestFixtures.completeDraft(
                                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()))),
                SubmissionPreparationTestFixtures.CONTEXT);
        assertPreparedEquivalent(
                assertInstanceOf(SubmissionPreparationCheck.Prepared.class, productionPrepared),
                assertInstanceOf(SubmissionPreparationCheck.Prepared.class, seamPrepared));

        var productionInvalid = production.prepare(
                SubmissionPreparationTestFixtures.passedNew(SkillSubmissionInput.direct(
                        SubmissionPreparationTestFixtures.emptyDraft(
                                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()))),
                SubmissionPreparationTestFixtures.CONTEXT);
        var seamInvalid = seam.prepare(
                SubmissionPreparationTestFixtures.passedNew(SkillSubmissionInput.direct(
                        SubmissionPreparationTestFixtures.emptyDraft(
                                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty()))),
                SubmissionPreparationTestFixtures.CONTEXT);
        assertReportEquivalent(
                assertInstanceOf(SubmissionPreparationCheck.Invalid.class, productionInvalid).report(),
                assertInstanceOf(SubmissionPreparationCheck.Invalid.class, seamInvalid).report());
    }

    @Test
    void repeatedPreparationIsDeterministicAndConsumesNoGlobalRevisionState() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = components.countingStages();
        var preparer = new SkillSubmissionPreparer(stages);
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty());
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(draft));

        var first = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));
        var second = assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                preparer.prepare(authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertCounts(stages, 2, 2, 2, 2),
                () -> assertEquals(first.proposedDocument(), second.proposedDocument()),
                () -> assertEquals(first.proposedDocument().revision(),
                        second.proposedDocument().revision()),
                () -> assertEquals(first.report().issues(), second.report().issues()),
                () -> assertEquals(first.report().truncated(), second.report().truncated()),
                () -> assertEquals(first.validatedDefinition().reference(),
                        second.validatedDefinition().reference()),
                () -> assertSame(draft, authority.precheck().input().draft()));
    }

    @Test
    void acceptedDefinitionReferenceMismatchRemainsAProgrammingException() {
        var alternate = prepareOtherSkill();
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var stages = new SkillSubmissionPreparer.Stages() {
            @Override
            public DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready) {
                return components.formalizer.formalize(ready);
            }

            @Override
            public ResolvedSkillCandidate resolve(
                    SkillDocument document,
                    SkillDocumentReadReport report) {
                return components.resolver.resolve(document, report);
            }

            @Override
            public SkillValidationAnalysis analyze(
                    ResolvedSkillCandidate candidate,
                    ValidationContext context) {
                return components.analyzer.analyze(candidate, context);
            }

            @Override
            public SkillValidationOutcome project(SkillValidationAnalysis analysis) {
                var actual = components.projector.project(analysis);
                return new SkillValidationOutcome.Accepted(
                        alternate.validatedDefinition(), actual.report());
            }
        };
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(SubmissionPreparationTestFixtures.completeDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty())));

        assertThrows(IllegalStateException.class, () ->
                new SkillSubmissionPreparer(stages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
    }

    @Test
    void constructorAndPrepareProgrammingBoundariesRejectNull() {
        var components = SubmissionPreparationTestFixtures.validPipeline();
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(SubmissionPreparationTestFixtures.emptyDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty())));
        var preparer = components.productionPreparer();

        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        new SkillSubmissionPreparer(
                                null,
                                components.resolver,
                                components.analyzer,
                                components.projector)),
                () -> assertThrows(NullPointerException.class, () ->
                        new SkillSubmissionPreparer(
                                components.formalizer,
                                null,
                                components.analyzer,
                                components.projector)),
                () -> assertThrows(NullPointerException.class, () ->
                        new SkillSubmissionPreparer(
                                components.formalizer,
                                components.resolver,
                                null,
                                components.projector)),
                () -> assertThrows(NullPointerException.class, () ->
                        new SkillSubmissionPreparer(
                                components.formalizer,
                                components.resolver,
                                components.analyzer,
                                null)),
                () -> assertThrows(NullPointerException.class, () ->
                        new SkillSubmissionPreparer((SkillSubmissionPreparer.Stages) null)),
                () -> assertThrows(NullPointerException.class, () ->
                        preparer.prepare(null, SubmissionPreparationTestFixtures.CONTEXT)),
                () -> assertThrows(NullPointerException.class, () ->
                        preparer.prepare(authority, null)));
    }

    @Test
    void stageErrorAndRuntimeExceptionAreNotConvertedToInvalid() {
        var authority = SubmissionPreparationTestFixtures.passedNew(
                SkillSubmissionInput.direct(SubmissionPreparationTestFixtures.emptyDraft(
                        SubmissionPreparationTestFixtures.SKILL_ID, Optional.empty())));
        var expectedError = new AssertionError("test error");
        var errorStages = failAfterFormalize(ready -> {
            throw expectedError;
        });
        var expectedRuntimeException = new IllegalStateException("test programming exception");
        var exceptionStages = failAfterFormalize(ready -> {
            throw expectedRuntimeException;
        });

        var thrownError = assertThrows(AssertionError.class, () ->
                new SkillSubmissionPreparer(errorStages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));
        var thrownException = assertThrows(IllegalStateException.class, () ->
                new SkillSubmissionPreparer(exceptionStages).prepare(
                        authority, SubmissionPreparationTestFixtures.CONTEXT));

        assertAll(
                () -> assertSame(expectedError, thrownError),
                () -> assertSame(expectedRuntimeException, thrownException));
    }

    private static void assertNodeEnvelopeOrder(
            com.yo1no.gramarye.magic.definition.document.SkillDraft draft,
            SkillDocument document) {
        for (int index = 0; index < draft.nodes().size(); index++) {
            var draftNode = draft.nodes().get(index);
            var documentNode = document.nodes().get(index);
            assertSame(
                    ((com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot.Present)
                            draftNode.trigger()).definition(),
                    documentNode.trigger());
            assertSame(
                    ((com.yo1no.gramarye.magic.definition.document.DraftActionSlot.Present)
                            draftNode.action()).definition(),
                    documentNode.action());
        }
    }

    private static void assertPipelineInvalid(
            SubmissionPreparationTestFixtures.PipelineComponents components,
            com.yo1no.gramarye.magic.definition.document.SkillDraft draft) {
        var stages = components.countingStages();
        var result = new SkillSubmissionPreparer(stages).prepare(
                SubmissionPreparationTestFixtures.passedNew(
                        SkillSubmissionInput.direct(draft)),
                SubmissionPreparationTestFixtures.CONTEXT);

        assertAll(
                () -> assertInstanceOf(SubmissionPreparationCheck.Invalid.class, result),
                () -> assertCounts(stages, 1, 1, 1, 1),
                () -> assertTrue(result.report().hasErrors()));
    }

    private static SubmissionPreparationCheck.Prepared prepareOtherSkill() {
        var draft = SubmissionPreparationTestFixtures.completeDraft(
                SubmissionPreparationTestFixtures.OTHER_SKILL_ID, Optional.empty());
        return assertInstanceOf(
                SubmissionPreparationCheck.Prepared.class,
                SubmissionPreparationTestFixtures.validPipeline()
                        .productionPreparer()
                        .prepare(
                                SubmissionPreparationTestFixtures.passedNew(
                                        SkillSubmissionInput.direct(draft)),
                                SubmissionPreparationTestFixtures.CONTEXT));
    }

    private static SkillSubmissionPreparer.Stages failAfterFormalize(
            java.util.function.Function<DraftSubmissionPrecheck.Ready, DraftFormalizationResult>
                    formalize) {
        return new SkillSubmissionPreparer.Stages() {
            @Override
            public DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready) {
                return formalize.apply(ready);
            }

            @Override
            public ResolvedSkillCandidate resolve(
                    SkillDocument document,
                    SkillDocumentReadReport report) {
                throw new AssertionError("resolve must not run");
            }

            @Override
            public SkillValidationAnalysis analyze(
                    ResolvedSkillCandidate candidate,
                    ValidationContext context) {
                throw new AssertionError("analyze must not run");
            }

            @Override
            public SkillValidationOutcome project(SkillValidationAnalysis analysis) {
                throw new AssertionError("project must not run");
            }
        };
    }

    private static void assertCounts(
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

    private static void assertPreparedEquivalent(
            SubmissionPreparationCheck.Prepared expected,
            SubmissionPreparationCheck.Prepared actual) {
        assertReportEquivalent(expected.report(), actual.report());
        assertAll(
                () -> assertEquals(expected.proposedDocument(), actual.proposedDocument()),
                () -> assertEquals(expected.validatedDefinition().reference(),
                        actual.validatedDefinition().reference()),
                () -> assertEquals(expected.validatedDefinition().nodes().size(),
                        actual.validatedDefinition().nodes().size()),
                () -> assertEquals(
                        expected.validatedDefinition().nodes().stream()
                                .map(node -> node.nodeIndex())
                                .toList(),
                        actual.validatedDefinition().nodes().stream()
                                .map(node -> node.nodeIndex())
                                .toList()),
                () -> assertEquals(
                        expected.validatedDefinition().nodes().stream()
                                .map(node -> node.references())
                                .toList(),
                        actual.validatedDefinition().nodes().stream()
                                .map(node -> node.references())
                                .toList()));
    }

    private static void assertReportEquivalent(
            com.yo1no.gramarye.magic.validation.ValidationResult expected,
            com.yo1no.gramarye.magic.validation.ValidationResult actual) {
        assertAll(
                () -> assertEquals(expected.issues(), actual.issues()),
                () -> assertEquals(expected.truncated(), actual.truncated()),
                () -> assertEquals(expected.omittedError(), actual.omittedError()));
    }
}
