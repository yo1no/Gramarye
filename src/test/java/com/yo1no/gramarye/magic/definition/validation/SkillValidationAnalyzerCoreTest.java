package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.ReadFact;
import com.yo1no.gramarye.magic.definition.document.ReadFactCode;
import com.yo1no.gramarye.magic.definition.document.ReadLocationKind;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionFailure;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionFailureCodes;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationFailure;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFact;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFactCode;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillValidationAnalyzerCoreTest {
    private static final ValidationContext DEFAULT_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void currentSchemaRunsEachResolvedSeamOnceWithoutReentry() {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, true));
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        var candidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(action)));

        var analysis = analyzer().analyze(candidate, DEFAULT_CONTEXT);

        assertSame(candidate, analysis.sourceCandidate());
        assertTrue(analysis.inspection().isPresent());
        assertFalse(analysis.report().hasErrors());
        assertEquals(1, trigger.inspectorAccessorCalls());
        assertEquals(1, action.inspectorAccessorCalls());
        assertEquals(1, trigger.capabilityCalls());
        assertEquals(1, action.capabilityCalls());
        assertEquals(1, trigger.validatorCalls());
        assertEquals(1, action.validatorCalls());
        assertEquals(0, trigger.codecCalls());
        assertEquals(0, action.codecCalls());
    }

    @Test
    void unsupportedSchemaStopsBeforeEveryInjectedOrDescriptorSeam() {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, true));
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        var candidate = SkillValidationTestFixtures.candidate(
                5,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false),
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(action)));
        var profileCalls = new int[1];

        var analysis = analyzer((field, id) -> {
            profileCalls[0]++;
            return ProfileAvailability.AVAILABLE;
        }).analyze(candidate, DEFAULT_CONTEXT);

        assertTrue(analysis.inspection().isEmpty());
        assertTrue(analysis.report().hasErrors());
        var issue = analysis.report().issues().getFirst();
        assertEquals(SkillValidationIssueCodes.SKILL_UNSUPPORTED_SCHEMA, issue.code());
        assertEquals("schema_version", issue.path().render());
        assertEquals(new ValidationIssueMetadata.Schema(5, 0), issue.metadata());
        assertEquals(0, trigger.inspectorAccessorCalls());
        assertEquals(0, action.inspectorAccessorCalls());
        assertEquals(0, trigger.capabilityCalls());
        assertEquals(0, action.capabilityCalls());
        assertEquals(0, trigger.validatorCalls());
        assertEquals(0, action.validatorCalls());
        assertEquals(0, profileCalls[0]);
    }

    @Test
    void emptyAndPolicyNodeCountUseNodesPathAndContextPolicy() {
        var empty = analyzer().analyze(
                SkillValidationTestFixtures.candidate(), DEFAULT_CONTEXT);
        assertEquals(
                List.of(SkillValidationIssueCodes.SKILL_EMPTY_NODES),
                empty.report().issues().stream().map(issue -> issue.code()).toList());
        assertEquals("nodes", empty.report().issues().getFirst().path().render());

        var node = neutralNode(0);
        var exact = analyzer().analyze(
                SkillValidationTestFixtures.candidate(node), contextWithMaxNodes(1));
        assertFalse(hasCode(exact, SkillValidationIssueCodes.SKILL_NODE_COUNT_POLICY_EXCEEDED));

        var over = analyzer().analyze(
                SkillValidationTestFixtures.candidate(neutralNode(0), neutralNode(1)),
                contextWithMaxNodes(1));
        var issue = over.report().issues().stream()
                .filter(value -> value.code().equals(
                        SkillValidationIssueCodes.SKILL_NODE_COUNT_POLICY_EXCEEDED))
                .findFirst().orElseThrow();
        assertEquals("nodes", issue.path().render());
        assertEquals(new ValidationIssueMetadata.Limit(2, 1), issue.metadata());
    }

    @Test
    void everyReadFactMapsExhaustivelyAndTruncationPrecedesGlobalStructure() {
        var facts = List.of(
                fact(ReadFactCode.INTENSITY_CLAMPED_LOW, AppearanceField.INTENSITY_MILLI),
                fact(ReadFactCode.INTENSITY_CLAMPED_HIGH, AppearanceField.INTENSITY_MILLI),
                fact(ReadFactCode.LEGACY_NULL_PROFILE_NORMALIZED, AppearanceField.SOUND_PROFILE),
                fact(ReadFactCode.LEGACY_NULL_SCALAR_NORMALIZED, AppearanceField.PRIMARY_ARGB),
                fact(ReadFactCode.LEGACY_NULL_APPEARANCE_DEFAULTED, null),
                nodeFact(ReadFactCode.LEGACY_NULL_OVERRIDE_NORMALIZED, null),
                fact(ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED, null));
        var candidate = SkillValidationTestFixtures.candidate(
                0,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(facts, true),
                new PipelineFactReport(List.of(), false),
                neutralNode(0),
                neutralNode(1));

        var analysis = analyzer().analyze(candidate, contextWithMaxNodes(1));
        var codes = analysis.report().issues().stream().map(issue -> issue.code()).toList();

        assertEquals(List.of(
                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_LOW,
                SkillValidationIssueCodes.READ_INTENSITY_CLAMPED_HIGH,
                SkillValidationIssueCodes.READ_LEGACY_NULL_PROFILE_NORMALIZED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_SCALAR_NORMALIZED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_APPEARANCE_DEFAULTED,
                SkillValidationIssueCodes.READ_LEGACY_NULL_OVERRIDE_NORMALIZED,
                SkillValidationIssueCodes.READ_UNKNOWN_APPEARANCE_FIELD_IGNORED,
                SkillValidationIssueCodes.READ_REPORT_TRUNCATED,
                SkillValidationIssueCodes.SKILL_NODE_COUNT_POLICY_EXCEEDED), codes);
        assertEquals("appearance.intensity_milli", analysis.report().issues().get(0).path().render());
        assertEquals(
                "nodes[0].appearance_override",
                analysis.report().issues().get(5).path().render());
        assertEquals("", analysis.report().issues().get(7).path().render());
        assertEquals("nodes", analysis.report().issues().getLast().path().render());
        assertEquals(ReadFactCode.values().length, facts.size());
    }

    @Test
    void draftReadLocationIsAProgrammingInvariantViolation() {
        var report = new SkillDocumentReadReport(List.of(new ReadFact(
                ReadFactCode.UNKNOWN_APPEARANCE_FIELD_IGNORED,
                ReadLocationKind.DRAFT_APPEARANCE,
                OptionalInt.empty(),
                Optional.empty())), false);
        var candidate = SkillValidationTestFixtures.candidate(
                0,
                AppearanceDocument.defaultAppearance(),
                report,
                new PipelineFactReport(List.of(), false));

        assertThrows(IllegalStateException.class, () -> analyzer().analyze(candidate, DEFAULT_CONTEXT));
    }

    @Test
    void pipelineFactsNeverBecomeValidationIssues() {
        var pipeline = new PipelineFactReport(List.of(
                new SkillMigrationFact(
                        SkillMigrationFactCode.STEP_APPLIED,
                        0,
                        1,
                        OptionalInt.of(0)),
                new SkillMigrationFact(
                        SkillMigrationFactCode.PAYLOAD_STEP_APPLIED,
                        0,
                        1,
                        OptionalInt.of(0),
                        OptionalInt.of(0))), true);
        var candidate = SkillValidationTestFixtures.candidate(
                0,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                pipeline,
                neutralNode(0));

        var analysis = analyzer().analyze(candidate, DEFAULT_CONTEXT);

        assertTrue(analysis.report().issues().isEmpty());
        assertSame(pipeline, candidate.pipelineFacts());
    }

    @Test
    void resolutionFailuresMapWithoutDiagnosticsAndDoNotInvokeDescriptors() {
        var triggerDescriptor = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false));
        var actionDescriptor = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        var candidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.unknownTrigger(),
                        SkillValidationTestFixtures.migrationFailedAction(
                                actionDescriptor,
                                PayloadMigrationFailure.Code.FUTURE_SCHEMA_VERSION)),
                SkillValidationTestFixtures.node(
                        1,
                        SkillValidationTestFixtures.migrationFailedTrigger(
                                triggerDescriptor,
                                PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE),
                        SkillValidationTestFixtures.decodeFailedAction(
                                actionDescriptor,
                                DefinitionFailure.Code.PAYLOAD_DECODE_ERROR)),
                SkillValidationTestFixtures.node(
                        2,
                        SkillValidationTestFixtures.decodeFailedTrigger(
                                triggerDescriptor,
                                DefinitionFailure.Code.CODEC_EXCEPTION),
                        SkillValidationTestFixtures.unknownAction()));

        var analysis = analyzer().analyze(candidate, DEFAULT_CONTEXT);

        assertEquals(List.of(
                SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE,
                SkillValidationIssueCodes.DEFINITION_PAYLOAD_SCHEMA_FUTURE,
                SkillValidationIssueCodes.DEFINITION_PAYLOAD_MIGRATION_MISSING_EDGE,
                SkillValidationIssueCodes.DEFINITION_PAYLOAD_DECODE_ERROR,
                SkillValidationIssueCodes.DEFINITION_PAYLOAD_CODEC_EXCEPTION,
                SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE),
                analysis.report().issues().stream().map(issue -> issue.code()).toList());
        assertEquals(0, triggerDescriptor.inspectorAccessorCalls());
        assertEquals(0, actionDescriptor.inspectorAccessorCalls());
        assertEquals(0, triggerDescriptor.capabilityCalls());
        assertEquals(0, actionDescriptor.capabilityCalls());
        assertEquals(0, triggerDescriptor.validatorCalls());
        assertEquals(0, actionDescriptor.validatorCalls());
        assertTrue(analysis.report().toString().contains("definition"));
        assertFalse(analysis.report().toString().contains("not retained"));
    }

    @Test
    void nonRoutingPayloadMigrationFailuresUseTheSingleBoundedFailureCode() {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false));
        var candidate = SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.migrationFailedTrigger(
                        trigger, PayloadMigrationFailure.Code.STEP_THREW_EXCEPTION),
                SkillValidationTestFixtures.unknownAction()));

        var analysis = analyzer().analyze(candidate, DEFAULT_CONTEXT);

        assertEquals(
                SkillValidationIssueCodes.DEFINITION_PAYLOAD_MIGRATION_FAILED,
                analysis.report().issues().getFirst().code());
        assertFalse(analysis.report().toString().contains("exception"));
    }

    @Test
    void inspectorMissingAndFailureMapPerSideWhileValidatorsStillRun() {
        var trigger = new SkillValidationTestFixtures.TriggerDescriptor(
                Optional::empty,
                SkillValidationTestFixtures::defaultTriggerCapabilities,
                (payload, context) -> ValidationResult.valid());
        var action = new SkillValidationTestFixtures.ActionDescriptor(
                () -> Optional.of(payload -> new PayloadInspectionResult.Failure<>(
                        new PayloadInspectionFailure(
                                PayloadInspectionFailureCodes.INSPECTOR_CONTRACT_VIOLATION,
                                new ValidationIssueMetadata.Limit(3, 2)))),
                SkillValidationTestFixtures::defaultActionCapabilities,
                (payload, context) -> ValidationResult.valid());
        var candidate = SkillValidationTestFixtures.candidate(
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(action)));

        var analysis = analyzer().analyze(candidate, DEFAULT_CONTEXT);

        assertEquals(List.of(
                SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_MISSING,
                SkillValidationIssueCodes.DESCRIPTOR_INSPECTOR_CONTRACT_VIOLATION),
                analysis.report().issues().stream().map(issue -> issue.code()).toList());
        assertEquals(1, trigger.capabilityCalls());
        assertEquals(1, action.capabilityCalls());
        assertEquals(1, trigger.validatorCalls());
        assertEquals(1, action.validatorCalls());
    }

    @Test
    void repeatedAnalysisIsStructurallyDeterministic() {
        var candidate = SkillValidationTestFixtures.candidate(neutralNode(0), neutralNode(1));
        var analyzer = analyzer();

        var first = analyzer.analyze(candidate, DEFAULT_CONTEXT);
        var second = analyzer.analyze(candidate, DEFAULT_CONTEXT);

        assertEquals(first.report(), second.report());
        assertEquals(first.inspection().orElseThrow(), second.inspection().orElseThrow());
        assertEquals(first.report().issues(), second.report().issues());
        assertEquals(first.report().truncated(), second.report().truncated());
        assertEquals(first.report().omittedError(), second.report().omittedError());
    }

    @Test
    void enumGatesRemainExactAndAnalysisHasNoPersistenceOrExecutionApi() {
        assertEquals(List.of("NONE", "PRIOR_NODE"),
                Arrays.stream(SourceRequirement.values()).map(Enum::name).toList());
        assertEquals(List.of("NONE", "OPTIONAL", "REQUIRED"),
                Arrays.stream(TargetRequirement.values()).map(Enum::name).toList());
        assertEquals(7, ReadFactCode.values().length);
        assertTrue(Arrays.stream(SkillValidationAnalysis.class.getMethods())
                .noneMatch(method -> Set.of("codec", "write", "save", "execute")
                        .contains(method.getName())));
        assertTrue(Arrays.stream(SkillValidationAnalysis.class.getConstructors()).findAny().isEmpty());
    }

    private static SkillValidationAnalyzer analyzer() {
        return analyzer(ProfileAvailabilityView.unknown());
    }

    private static SkillValidationAnalyzer analyzer(ProfileAvailabilityView profiles) {
        return new SkillValidationAnalyzer(new NodeProjectionResolver(), profiles);
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate neutralNode(
            int index) {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, true));
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        return SkillValidationTestFixtures.node(
                index,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action));
    }

    private static ReadFact fact(ReadFactCode code, AppearanceField field) {
        return new ReadFact(
                code,
                ReadLocationKind.SKILL_APPEARANCE,
                OptionalInt.empty(),
                Optional.ofNullable(field));
    }

    private static ReadFact nodeFact(ReadFactCode code, AppearanceField field) {
        return new ReadFact(
                code,
                ReadLocationKind.SKILL_NODE_APPEARANCE_OVERRIDE,
                OptionalInt.of(0),
                Optional.ofNullable(field));
    }

    private static boolean hasCode(
            SkillValidationAnalysis analysis,
            com.yo1no.gramarye.magic.validation.ValidationIssueCode code) {
        return analysis.report().issues().stream().anyMatch(issue -> issue.code().equals(code));
    }

    private static ValidationContext contextWithMaxNodes(int maxNodes) {
        var defaults = MagicPolicyLimits.DEFAULTS;
        return new ValidationContext(new MagicPolicyLimits(
                maxNodes,
                defaults.maxStringLength(),
                defaults.maxRawPayloadBytes(),
                defaults.maxRuntimeTags(),
                defaults.maxVisitedTargets(),
                defaults.maxAppearanceIntensity(),
                defaults.maxUnparsedAppearanceDepth(),
                defaults.maxUnparsedAppearanceNodes(),
                defaults.maxSkillDocumentDepth(),
                defaults.maxSkillDocumentBytes(),
                defaults.maxSkillDocumentTreeNodes()));
    }
}
