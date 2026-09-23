package com.yo1no.gramarye.magic.definition.migration;

import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.ACTION_ID;
import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.EMPTY_READ_REPORT;
import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.TRIGGER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SkillCandidateResolverOrderedTest {
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void everyEnvelopeCompletesMigrationDecodeAndSemanticOnceBeforeTheNextLookup() {
        var trace = new ArrayList<String>();
        var trigger = new Probe("trigger", trace);
        var action = new Probe("action", trace);
        var one = document();
        var two = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                List.of(one.nodes().getFirst(), one.nodes().getFirst()), one.appearance());
        var result = resolver(trigger, action).resolveAndValidate(
                two, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());

        assertEquals(List.of("trigger.lookup", "trigger.migration", "trigger.codec", "trigger.semantic",
                "action.lookup", "action.migration", "action.codec", "action.semantic",
                "trigger.lookup", "trigger.migration", "trigger.codec", "trigger.semantic",
                "action.lookup", "action.migration", "action.codec", "action.semantic"),
                trace.stream().filter(value -> value.endsWith(".lookup") || value.endsWith(".migration")
                        || value.endsWith(".codec") || value.endsWith(".semantic")).toList());
        assertEquals(2, result.analysis().sourceCandidate().nodes().size());
        assertEquals(2, trace.stream().filter("trigger.semantic"::equals).count());
        assertEquals(2, trace.stream().filter("action.semantic"::equals).count());
        assertTrue(trace.indexOf("trigger.inspector") > trace.lastIndexOf("action.semantic"));
    }

    @Test
    void unexpectedRuntimeAndErrorPropagateUnchangedFromEveryActualOwnerStage() {
        for (var actionSide : new boolean[] {false, true}) {
            for (var stage : List.of("lookup", "schema", "plan", "migration", "codecAccess", "codec",
                    "semantic", "inspector", "inspect", "capabilities")) {
                for (var failure : new Throwable[] {
                        new IllegalStateException("same runtime " + stage), new AssertionError("same error " + stage)}) {
                    var trace = new ArrayList<String>();
                    var trigger = new Probe("trigger", trace);
                    var action = new Probe("action", trace);
                    var failing = actionSide ? action : trigger;
                    failing.failureStage = stage;
                    failing.failure = failure;
                    var resolver = resolver(trigger, action);
                    assertSame(failure, assertThrows(failure.getClass(), () -> resolver.resolveAndValidate(
                            document(), EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown())),
                            "side=" + failing.name + ", stage=" + stage + ", trace=" + trace);
                }
            }
        }
    }

    @Test
    void futureSchemaIsDecidedBeforeMigrationPlanOrCodecIsConsulted() {
        var trace = new ArrayList<String>();
        var trigger = new Probe("trigger", trace);
        trigger.failureStage = "plan";
        trigger.failure = new AssertionError("future data must not enter migration");
        var future = P3B2TestFixtures.document(P3B2TestFixtures.triggerEnvelope(2, 1),
                P3B2TestFixtures.actionEnvelope(0, 1));
        var result = resolver(trigger, new Probe("action", trace)).resolveAndValidate(
                future, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
        assertEquals(SkillCandidateResolver.FirstFailure.FUTURE_SCHEMA, result.firstFailure());
        assertFalse(trace.contains("trigger.plan"));
        assertFalse(trace.contains("trigger.codec"));
        assertFalse(trace.contains("trigger.semantic"));
    }

    @Test
    void negativeDescriptorSchemaIsAnInternalFaultNotAFutureDataClassification() {
        for (var actionSide : new boolean[] {false, true}) {
            var trace = new ArrayList<String>();
            var trigger = new Probe("trigger", trace);
            var action = new Probe("action", trace);
            var failing = actionSide ? action : trigger;
            failing.currentVersion = -1;
            assertThrows(IllegalArgumentException.class,
                    () -> resolver(trigger, action).resolveAndValidate(
                            document(), EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown()));
            assertTrue(trace.contains(failing.name + ".schema"));
            assertFalse(trace.contains(failing.name + ".plan"));
            assertFalse(trace.contains(failing.name + ".codec"));
            assertFalse(trace.contains(failing.name + ".semantic"));
        }
    }

    @Test
    void hiddenSemanticFatalPrecedesLaterUnknownAndDoesNotDependOnRetainedIssues() {
        var trace = new ArrayList<String>();
        var trigger = new Probe("trigger", trace);
        var warnings = new ValidationCollector();
        for (var index = 0; index < 1024; index++) {
            warnings.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "ordered.warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().index(index), ValidationIssueMetadata.none()));
        }
        trigger.semantic = warnings.result();
        var action = new Probe("action", trace);
        action.semantic = new ValidationCollector().add(issue(ValidationSeverity.ERROR)).result();
        var laterUnknown = P3B2TestFixtures.document(P3B2TestFixtures.triggerEnvelope(0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 1));
        var first = document();
        var source = new SkillDocument(first.schemaVersion(), first.skillId(), first.revision(),
                List.of(first.nodes().getFirst(), laterUnknown.nodes().getFirst()), first.appearance());
        var result = resolver(trigger, action).resolveAndValidate(
                source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
        assertEquals(SkillCandidateResolver.FirstFailure.SEMANTIC_INVALID, result.firstFailure());
        assertEquals(1024, result.analysis().report().issues().size());
        assertTrue(result.analysis().report().issues().stream()
                .allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
        assertTrue(result.analysis().report().omittedError());
        assertTrue(result.analysis().report().truncated());
    }

    @Test
    void earlierResolutionFatalsAreRetainedBeforeLaterSemanticWarningsFillTheCollector() {
        for (var expected : List.of(SkillCandidateResolver.FirstFailure.UNKNOWN_TYPE,
                SkillCandidateResolver.FirstFailure.FUTURE_SCHEMA,
                SkillCandidateResolver.FirstFailure.MIGRATION_FAILED,
                SkillCandidateResolver.FirstFailure.DECODE_FAILED)) {
            var trace = new ArrayList<String>();
            var trigger = new Probe("trigger", trace);
            var action = new Probe("action", trace);
            var warnings = new ValidationCollector();
            for (var index = 0; index < 1024; index++) {
                warnings.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "later.warning"),
                        ValidationSeverity.WARNING, ValidationPath.empty().index(index), ValidationIssueMetadata.none()));
            }
            action.semantic = warnings.result();
            var envelope = P3B2TestFixtures.triggerEnvelope(0, 1);
            if (expected == SkillCandidateResolver.FirstFailure.UNKNOWN_TYPE) {
                envelope = P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 999, 1);
            } else if (expected == SkillCandidateResolver.FirstFailure.FUTURE_SCHEMA) {
                envelope = P3B2TestFixtures.triggerEnvelope(2, 1);
            } else if (expected == SkillCandidateResolver.FirstFailure.MIGRATION_FAILED) {
                trigger.currentVersion = 2; // The registered plan contains only 0 -> 1.
            } else {
                var malformed = new JsonObject();
                malformed.addProperty("value", "not an integer");
                envelope = new DefinitionEnvelope(TRIGGER_ID, 0, new Dynamic<>(JsonOps.INSTANCE, malformed));
            }
            var source = P3B2TestFixtures.document(envelope, P3B2TestFixtures.actionEnvelope(0, 1));
            var result = resolver(trigger, action).resolveAndValidate(
                    source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
            assertEquals(expected, result.firstFailure());
            var report = result.analysis().report();
            assertEquals(1024, report.issues().size());
            assertEquals(ValidationSeverity.ERROR, report.issues().getFirst().severity(), expected.name());
            assertEquals(ValidationPath.empty().field("nodes").index(0).field("trigger"),
                    report.issues().getFirst().path(), expected.name());
            assertTrue(report.issues().subList(1, 1024).stream()
                    .allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
            assertTrue(report.truncated());
        }
    }

    @Test
    void bothUnresolvedSidesPrecedeTheNextNodesSemanticDiagnostics() {
        var trace = new ArrayList<String>();
        var trigger = new Probe("trigger", trace);
        var warnings = new ValidationCollector();
        for (var index = 0; index < 1024; index++) {
            warnings.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "later.warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().index(index), ValidationIssueMetadata.none()));
        }
        trigger.semantic = warnings.result();
        var unresolved = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 1));
        var known = document();
        var source = new SkillDocument(known.schemaVersion(), known.skillId(), known.revision(),
                List.of(unresolved.nodes().getFirst(), known.nodes().getFirst()), known.appearance());
        var result = resolver(trigger, new Probe("action", trace)).resolveAndValidate(
                source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
        assertEquals(SkillCandidateResolver.FirstFailure.UNKNOWN_TYPE, result.firstFailure());
        var issues = result.analysis().report().issues();
        assertEquals(1024, issues.size());
        assertEquals(ValidationPath.empty().field("nodes").index(0).field("trigger"), issues.get(0).path());
        assertEquals(ValidationPath.empty().field("nodes").index(0).field("action"), issues.get(1).path());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
        assertEquals(ValidationSeverity.ERROR, issues.get(1).severity());
        assertTrue(issues.subList(2, 1024).stream().allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
        assertTrue(result.analysis().report().truncated());
    }

    @Test
    void unexpectedFaultStillEscapesAfterAnEarlierExpectedFatal() {
        var trace = new ArrayList<String>();
        var action = new Probe("action", trace);
        action.failureStage = "semantic";
        action.failure = new IllegalStateException("later internal failure overrides expected data failure");
        var source = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.actionEnvelope(0, 1));
        assertSame(action.failure, assertThrows(IllegalStateException.class,
                () -> resolver(new Probe("trigger", trace), action).resolveAndValidate(
                        source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown())));
    }

    @Test
    void orderedTypedWorkNeverExceedsTwoNodesAndExactCountRejectsAtWholeStage() {
        var one = document();
        for (var count : List.of(0, 1, 2, 3, 64)) {
            var trace = new ArrayList<String>();
            var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                    java.util.Collections.nCopies(count, one.nodes().getFirst()), one.appearance());
            var result = resolver(new Probe("trigger", trace), new Probe("action", trace))
                    .resolveAndValidate(source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
            assertEquals(Math.min(count, 2), result.analysis().sourceCandidate().nodes().size());
            assertEquals(Math.min(count, 2), trace.stream().filter("trigger.lookup"::equals).count());
            assertEquals(Math.min(count, 2), trace.stream().filter("action.lookup"::equals).count());
            assertEquals(Math.min(count, 2), trace.stream().filter("trigger.semantic"::equals).count());
            assertEquals(Math.min(count, 2), trace.stream().filter("action.semantic"::equals).count());
            assertEquals(count == 2 ? SkillCandidateResolver.FirstFailure.NONE
                    : SkillCandidateResolver.FirstFailure.SEMANTIC_INVALID, result.firstFailure());
        }
    }

    @Test
    void anyAuthorizedEnvelopeFatalPrecedesExtraNodeWholeShapeRejection() {
        var one = document();
        for (int coordinate = 0; coordinate < 4; coordinate++) {
            var nodes = new ArrayList<>(java.util.Collections.nCopies(3, one.nodes().getFirst()));
            var index = coordinate / 2;
            var existing = nodes.get(index);
            var unknown = P3B2TestFixtures.envelope(coordinate % 2 == 0
                    ? P3B2TestFixtures.UNKNOWN_TRIGGER_ID : P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 1);
            nodes.set(index, new com.yo1no.gramarye.magic.definition.document.NodeDocument(
                    coordinate % 2 == 0 ? unknown : existing.trigger(),
                    coordinate % 2 == 1 ? unknown : existing.action(), existing.appearanceOverride()));
            var trace = new ArrayList<String>();
            var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(), nodes, one.appearance());
            var result = resolver(new Probe("trigger", trace), new Probe("action", trace))
                    .resolveAndValidate(source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
            assertEquals(SkillCandidateResolver.FirstFailure.UNKNOWN_TYPE, result.firstFailure());
            assertEquals(2, trace.stream().filter("trigger.lookup"::equals).count());
            assertEquals(2, trace.stream().filter("action.lookup"::equals).count());
            assertEquals(ValidationPath.empty().field("nodes").index(index)
                    .field(coordinate % 2 == 0 ? "trigger" : "action"),
                    result.analysis().report().issues().getFirst().path());
        }
    }

    @Test
    void thirdUnknownEnvelopeIsNeverLookedUpAndCannotReplaceExactCountPrimary() {
        var one = document();
        var unknown = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 1));
        var trace = new ArrayList<String>();
        var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                List.of(one.nodes().getFirst(), one.nodes().getFirst(), unknown.nodes().getFirst()), one.appearance());
        var result = resolver(new Probe("trigger", trace), new Probe("action", trace))
                .resolveAndValidate(source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
        assertEquals(SkillCandidateResolver.FirstFailure.SEMANTIC_INVALID, result.firstFailure());
        assertEquals(2, trace.stream().filter("trigger.lookup"::equals).count());
        assertEquals(2, trace.stream().filter("action.lookup"::equals).count());
        assertTrue(result.analysis().report().issues().stream().noneMatch(issue -> issue.code().equals(
                com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes.DEFINITION_UNKNOWN_TYPE)));
    }

    @Test
    void genericPolicyDiagnosticUsesOriginalCountNotTheBoundedTypedPrefix() {
        var one = document();
        var defaults = MagicPolicyLimits.DEFAULTS;
        var context = new ValidationContext(new MagicPolicyLimits(1, defaults.maxStringLength(),
                defaults.maxRawPayloadBytes(), defaults.maxRuntimeTags(), defaults.maxVisitedTargets(),
                defaults.maxAppearanceIntensity(), defaults.maxUnparsedAppearanceDepth(), defaults.maxUnparsedAppearanceNodes(),
                defaults.maxSkillDocumentDepth(), defaults.maxSkillDocumentBytes(), defaults.maxSkillDocumentTreeNodes()));
        for (var count : List.of(3, 64)) {
            var trace = new ArrayList<String>();
            var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                    java.util.Collections.nCopies(count, one.nodes().getFirst()), one.appearance());
            var result = resolver(new Probe("trigger", trace), new Probe("action", trace))
                    .resolveAndValidate(source, EMPTY_READ_REPORT, context, ProfileAvailabilityView.unknown());
            var issue = result.analysis().report().issues().stream().filter(value -> value.code().equals(
                    com.yo1no.gramarye.magic.definition.validation.SkillValidationIssueCodes.SKILL_NODE_COUNT_POLICY_EXCEEDED))
                    .findFirst().orElseThrow();
            assertEquals(new ValidationIssueMetadata.Limit(count, 1), issue.metadata());
            assertEquals(2, result.analysis().sourceCandidate().nodes().size());
        }
    }

    @Test
    void exactCountFatalSurvivesARealFullCollectorWithoutRequiringAnExtraIssue() {
        var trace = new ArrayList<String>();
        var trigger = new Probe("trigger", trace);
        var warnings = new ValidationCollector();
        for (int index = 0; index < 1024; index++) {
            warnings.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "count.warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().index(index), ValidationIssueMetadata.none()));
        }
        trigger.semantic = warnings.result();
        var one = document();
        var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                java.util.Collections.nCopies(3, one.nodes().getFirst()), one.appearance());
        var result = resolver(trigger, new Probe("action", trace))
                .resolveAndValidate(source, EMPTY_READ_REPORT, CONTEXT, ProfileAvailabilityView.unknown());
        assertEquals(SkillCandidateResolver.FirstFailure.SEMANTIC_INVALID, result.firstFailure());
        assertEquals(1024, result.analysis().report().issues().size());
        assertTrue(result.analysis().report().issues().stream().allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
        assertEquals(2, trace.stream().filter("trigger.semantic"::equals).count());
        assertEquals(2, result.analysis().sourceCandidate().nodes().size());
    }

    @Test
    void legacyResolveAndAnalyzeStillProcessEveryAdmittedNode() {
        var one = document();
        var trace = new ArrayList<String>();
        var source = new SkillDocument(one.schemaVersion(), one.skillId(), one.revision(),
                java.util.Collections.nCopies(3, one.nodes().getFirst()), one.appearance());
        var candidate = resolver(new Probe("trigger", trace), new Probe("action", trace)).resolve(source, EMPTY_READ_REPORT);
        assertEquals(3, candidate.nodes().size());
        assertEquals(3, trace.stream().filter("trigger.lookup"::equals).count());
        var analysis = new SkillValidationAnalyzer(new NodeProjectionResolver(), ProfileAvailabilityView.unknown())
                .analyze(candidate, CONTEXT);
        assertEquals(3, analysis.sourceCandidate().nodes().size());
        assertEquals(3, trace.stream().filter("trigger.semantic"::equals).count());
    }

    @Test
    void legacyResolutionAndAnalysisStillContainRuntimeFailuresAtTheirOriginalBoundaries() {
        for (var stage : List.of("migration", "codec", "semantic", "inspector", "inspect", "capabilities")) {
            var trace = new ArrayList<String>();
            var trigger = new Probe("trigger", trace);
            trigger.failureStage = stage;
            trigger.failure = new IllegalStateException("legacy contained " + stage);
            var candidate = resolver(trigger, new Probe("action", trace)).resolve(document(), EMPTY_READ_REPORT);
            if (stage.equals("migration")) {
                assertInstanceOf(TriggerResolution.MigrationFailed.class, candidate.nodes().getFirst().trigger());
            } else if (stage.equals("codec")) {
                assertInstanceOf(TriggerResolution.DecodeFailed.class, candidate.nodes().getFirst().trigger());
            } else {
                assertInstanceOf(TriggerResolution.Resolved.class, candidate.nodes().getFirst().trigger());
            }
            assertTrue(new SkillValidationAnalyzer(new NodeProjectionResolver(), ProfileAvailabilityView.unknown())
                    .analyze(candidate, CONTEXT).report().hasErrors());
        }
    }

    @Test
    void semanticCompletionHandoffCannotBeConstructedOutsideItsResolverOwner() {
        assertTrue(Modifier.isFinal(SkillCandidateResolver.OrderedSemanticHandoff.class.getModifiers()));
        assertEquals(1, SkillCandidateResolver.OrderedSemanticHandoff.class.getDeclaredConstructors().length);
        assertTrue(Arrays.stream(SkillCandidateResolver.OrderedSemanticHandoff.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertTrue(Arrays.stream(SkillCandidateResolver.OrderedSemanticHandoff.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers())));
    }

    @Test
    void orderedPublicSurfaceIsExactlyTheIntegratedEntryAndOwnerBoundHandoff() throws ReflectiveOperationException {
        assertEquals(Set.of("resolve", "resolveFromRaw", "resolveAndValidate"),
                Arrays.stream(SkillCandidateResolver.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        var entry = SkillCandidateResolver.class.getDeclaredMethod("resolveAndValidate", SkillDocument.class,
                com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport.class,
                ValidationContext.class, ProfileAvailabilityView.class);
        assertEquals(SkillCandidateResolver.OrderedValidationResult.class, entry.getReturnType());
        assertEquals(Set.of("FirstFailure", "OrderedValidationResult", "OrderedSemanticHandoff"),
                Arrays.stream(SkillCandidateResolver.class.getDeclaredClasses()).filter(type -> !type.isSynthetic())
                        .map(Class::getSimpleName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of("NONE", "UNKNOWN_TYPE", "FUTURE_SCHEMA", "MIGRATION_FAILED", "DECODE_FAILED",
                        "SEMANTIC_INVALID"),
                Arrays.stream(SkillCandidateResolver.FirstFailure.values()).map(Enum::name).toList());
        assertEquals(List.of("analysis:com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis",
                        "firstFailure:" + SkillCandidateResolver.FirstFailure.class.getName()),
                Arrays.stream(SkillCandidateResolver.OrderedValidationResult.class.getRecordComponents())
                        .map(component -> component.getName() + ":" + component.getType().getName()).toList());
        assertEquals(Set.of("candidate", "context", "ownsDiagnostics", "firstFailure", "originalNodeCount"),
                Arrays.stream(SkillCandidateResolver.OrderedSemanticHandoff.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        assertEquals(boolean.class, SkillCandidateResolver.OrderedSemanticHandoff.class
                .getDeclaredMethod("ownsDiagnostics", ValidationCollector.class).getReturnType());
        assertEquals(int.class, SkillCandidateResolver.OrderedSemanticHandoff.class
                .getDeclaredMethod("originalNodeCount").getReturnType());
        assertEquals(com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis.class,
                SkillValidationAnalyzer.class.getDeclaredMethod("analyzeOrdered",
                        SkillCandidateResolver.OrderedSemanticHandoff.class, ValidationCollector.class).getReturnType());
        for (var resolution : List.of(TriggerResolution.class, ActionResolution.class)) {
            assertEquals(boolean.class, SkillValidationAnalyzer.class.getDeclaredMethod("validateOrderedEnvelope",
                    resolution, ValidationContext.class, int.class, ValidationCollector.class).getReturnType());
        }
        assertFalse(Arrays.stream(SkillValidationAnalyzer.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("validateOrderedSemantic")));
    }

    private static SkillDocument document() {
        return P3B2TestFixtures.document(P3B2TestFixtures.triggerEnvelope(0, 1),
                P3B2TestFixtures.actionEnvelope(0, 1));
    }

    private static ValidationIssue issue(ValidationSeverity severity) {
        return new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "ordered.test"),
                severity, ValidationPath.empty(), ValidationIssueMetadata.none());
    }

    private static SkillCandidateResolver resolver(Probe trigger, Probe action) {
        var triggerType = new TriggerDescriptor(trigger);
        var actionType = new ActionDescriptor(action);
        return new SkillCandidateResolver(new TriggerTypeLookup() {
            @Override
            public Optional<TriggerType<?>> find(ResourceLocation id) {
                trigger.visit("lookup");
                return TRIGGER_ID.equals(id) ? Optional.of(triggerType) : Optional.empty();
            }

            @Override
            public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
                return descriptor == triggerType ? Optional.of(TRIGGER_ID) : Optional.empty();
            }
        }, new ActionTypeLookup() {
            @Override
            public Optional<ActionType<?>> find(ResourceLocation id) {
                action.visit("lookup");
                return ACTION_ID.equals(id) ? Optional.of(actionType) : Optional.empty();
            }

            @Override
            public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
                return descriptor == actionType ? Optional.of(ACTION_ID) : Optional.empty();
            }
        });
    }

    private static final class Probe {
        private final String name;
        private final List<String> trace;
        private String failureStage;
        private Throwable failure;
        private int currentVersion = 1;
        private ValidationResult semantic = ValidationResult.valid();

        private Probe(String name, List<String> trace) {
            this.name = name;
            this.trace = trace;
        }

        private void visit(String stage) {
            trace.add(name + "." + stage);
            if (stage.equals(failureStage)) {
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw (Error) failure;
            }
        }

        private PayloadMigrationPlan plan() {
            visit("plan");
            return new PayloadMigrationPlan(List.of(new PayloadMigrationStep() {
                @Override
                public int fromVersion() { return 0; }
                @Override
                public int toVersion() { return 1; }
                @Override
                public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(Dynamic<T> input) {
                    visit("migration");
                    return DataResult.success(new PayloadMigrationStepOutput<>(input));
                }
            }));
        }
    }

    private record TriggerDescriptor(Probe probe) implements TriggerType<P3B2TestFixtures.TriggerData> {
        @Override
        public int currentPayloadSchemaVersion() { probe.visit("schema"); return probe.currentVersion; }
        @Override
        public PayloadMigrationPlan payloadMigrationPlan() { return probe.plan(); }
        @Override
        public MapCodec<P3B2TestFixtures.TriggerData> payloadCodec() {
            probe.visit("codecAccess");
            return P3B2TestFixtures.TriggerData.CODEC.xmap(value -> {
                probe.visit("codec"); return value;
            }, value -> value);
        }
        @Override
        public TriggerCapabilities capabilities() {
            probe.visit("capabilities");
            return new P3B2TestFixtures.TriggerDescriptor(1, PayloadMigrationPlan.empty()).capabilities();
        }
        @Override
        public ValidationResult validate(P3B2TestFixtures.TriggerData payload, ValidationContext context) {
            probe.visit("semantic"); return probe.semantic;
        }
        @Override
        public Optional<TriggerPayloadInspector<P3B2TestFixtures.TriggerData>> payloadInspector() {
            probe.visit("inspector");
            return Optional.of(payload -> {
                probe.visit("inspect");
                return new PayloadInspectionResult.Success<>(new TriggerReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false, List.of()));
            });
        }
    }

    private record ActionDescriptor(Probe probe) implements ActionType<P3B2TestFixtures.ActionData> {
        @Override
        public int currentPayloadSchemaVersion() { probe.visit("schema"); return probe.currentVersion; }
        @Override
        public PayloadMigrationPlan payloadMigrationPlan() { return probe.plan(); }
        @Override
        public MapCodec<P3B2TestFixtures.ActionData> payloadCodec() {
            probe.visit("codecAccess");
            return P3B2TestFixtures.ActionData.CODEC.xmap(value -> {
                probe.visit("codec"); return value;
            }, value -> value);
        }
        @Override
        public ActionCapabilities capabilities() {
            probe.visit("capabilities");
            return new P3B2TestFixtures.ActionDescriptor(1, PayloadMigrationPlan.empty()).capabilities();
        }
        @Override
        public ValidationResult validate(P3B2TestFixtures.ActionData payload, ValidationContext context) {
            probe.visit("semantic"); return probe.semantic;
        }
        @Override
        public Optional<ActionPayloadInspector<P3B2TestFixtures.ActionData>> payloadInspector() {
            probe.visit("inspector");
            return Optional.of(payload -> {
                probe.visit("inspect");
                return new PayloadInspectionResult.Success<>(new ActionReferenceProjection(
                        SourceSelection.NONE, TargetSelection.SELF, List.of(), Set.of()));
            });
        }
    }
}
