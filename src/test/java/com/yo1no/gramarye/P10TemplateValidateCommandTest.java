package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P10TemplateValidateCommandTest {
    private static final Set<String> SOURCES = Set.of(
            "P10TemplateBody.java", "P10TemplateCodec.java", "P10TemplateService.java",
            "P10TemplateValidation.java", "P10TemplateValidateCommand.java");
    // Derived from the five source declarations and their explicit nested declarations.
    private static final Set<String> CLASSES = Set.of(
            "P10TemplateBody.class",
            "P10TemplateCodec.class", "P10TemplateCodec$Result.class", "P10TemplateCodec$Decoded.class",
            "P10TemplateCodec$Rejected.class", "P10TemplateCodec$DecodeReason.class",
            "P10TemplateCodec$Parsed.class", "P10TemplateCodec$IngressFailure.class",
            "P10TemplateCodec$PayloadLimit.class", "P10TemplateCodec$PayloadByteCounter.class",
            "P10TemplateCodec$Parser.class",
            "P10TemplateService.class", "P10TemplateService$Origin.class",
            "P10TemplateService$Classification.class", "P10TemplateService$Capture.class",
            "P10TemplateService$AttemptStatus.class", "P10TemplateService$Cycle.class",
            "P10TemplateService$Staged.class", "P10TemplateService$Prepared.class",
            "P10TemplateService$TemplateReloadListener.class",
            "P10TemplateService$Ticket.class",
            "P10TemplateValidation.class", "P10TemplateValidation$1.class",
            "P10TemplateValidation$Classification.class",
            "P10TemplateValidation$Result.class",
            "P10TemplateValidateCommand.class", "P10TemplateValidateCommand$Primary.class",
            "P10TemplateValidateCommand$Response.class");

    @Test
    void sourceAndCompiledInventoryAreExactAndTopLevelTypesArePackagePrivate() throws IOException {
        var root = projectRoot();
        try (var files = Files.list(root.resolve("src/main/java/com/yo1no/gramarye"))) {
            assertEquals(SOURCES, files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("P10") && name.endsWith(".java"))
                    .collect(Collectors.toUnmodifiableSet()));
        }
        try (var files = Files.list(root.resolve("build/classes/java/main/com/yo1no/gramarye"))) {
            assertEquals(CLASSES, files.filter(Files::isRegularFile).map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("P10") && name.endsWith(".class"))
                    .collect(Collectors.toUnmodifiableSet()));
        }
        assertEquals(28, CLASSES.size());
        for (var type : List.of(P10TemplateBody.class, P10TemplateCodec.class, P10TemplateService.class,
                P10TemplateValidation.class, P10TemplateValidateCommand.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            assertFalse(Modifier.isProtected(type.getModifiers()), type.getName());
            assertEquals("com.yo1no.gramarye", type.getPackageName());
        }
        for (var name : SOURCES) {
            var source = Files.readString(root.resolve("src/main/java/com/yo1no/gramarye").resolve(name));
            assertFalse(source.contains("net.minecraft.client"), name);
            assertFalse(source.contains("net.neoforged.neoforge.client"), name);
            assertFalse(source.contains("java.lang.reflect"), name);
        }
    }

    @Test
    void commandHasOnlyReadOnlyOwnersAndExactOperatorOnlyFixedIdRegistration() throws IOException {
        assertEquals(Set.of(P10TemplateService.class, P10TemplateValidation.class),
                Arrays.stream(P10TemplateValidateCommand.class.getDeclaredFields())
                        .filter(field -> !field.isSynthetic()).map(java.lang.reflect.Field::getType)
                        .collect(Collectors.toUnmodifiableSet()));
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/P10TemplateValidateCommand.java"));
        assertTrue(source.contains("Commands.literal(\"skill\")"));
        assertTrue(source.contains("Commands.literal(\"validate\").requires(source -> source.hasPermission(2))"));
        assertTrue(source.contains("Commands.literal(\"template\")"));
        assertTrue(source.contains("Commands.argument(\"resource_location\", ResourceLocationArgument.id())"));
        assertTrue(source.contains("ResourceLocationArgument.getId(context, \"resource_location\")"));
        assertTrue(source.contains("id.equals(P10TemplateCodec.TEMPLATE_ID)"));
        assertTrue(source.contains("server.isSameThread()"));
        assertTrue(source.contains("source.sendSuccess(() -> component, false)"));
        assertTrue(source.contains("source.sendFailure(component)"));
        assertEquals(1, source.split("templates.capture\\(server\\)", -1).length - 1);
        for (var forbidden : List.of("SkillDefinitionStore", "PlayerSkillAttachment", "ManaTransaction",
                "SkillRuntimeService", "P6Runtime", "P9WorldEffect", "ServerPlayer", "getPlayerOrException",
                "putDraft(", "setEquipped(", ".submit(", "reloadResources(", "getResource(")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void onlyReadyPrimariesReturnOneIndependentlyOfEveryLatestAttemptCategory() {
        for (var primary : P10TemplateValidateCommand.Primary.values()) {
            for (var classification : P10TemplateService.Classification.values()) {
                var latest = new P10TemplateService.AttemptStatus(classification, List.of(), 0, false);
                var response = P10TemplateValidateCommand.response(primary, ValidationResult.valid(), latest);
                var ready = primary == P10TemplateValidateCommand.Primary.READY_CURRENT
                        || primary == P10TemplateValidateCommand.Primary.READY_LKG;
                assertEquals(ready ? 1 : 0, response.returnCode());
                assertEquals(1, response.components().size());
                assertTrue(response.components().getFirst().getString().startsWith(primary.name()));
                assertTrue(response.components().getFirst().getString().contains("lastAttempt=" + classification));
                assertEquals(0, response.displayOmitted());
            }
        }
    }

    @Test
    void currentIssuesPrecedeLatestSummariesAndShareOneSixteenComponentBudget() {
        var current = issues(10, false);
        var summaries = IntStream.range(0, 16).mapToObj(index -> "latest_" + index).toList();
        var latest = new P10TemplateService.AttemptStatus(P10TemplateService.Classification.UNKNOWN_TYPE,
                summaries, 28, true);
        var response = P10TemplateValidateCommand.response(P10TemplateValidateCommand.Primary.READY_LKG, current, latest);
        assertEquals(1, response.returnCode());
        assertEquals(17, response.components().size());
        for (var index = 0; index < 10; index++) {
            assertTrue(response.components().get(index + 1).getString().contains("current[" + index + "]"));
        }
        for (var index = 0; index < 6; index++) {
            assertEquals("latest_" + index, response.components().get(index + 11).getString());
        }
        assertEquals(22, response.displayOmitted());
        assertEquals(12, latest.retainedButNotSummarized());
        assertTrue(response.upstreamTruncated());
        assertTrue(response.components().getFirst().getString().contains("displayOmitted=22"));
    }

    @Test
    void fatalAfterCapKeepsFirstWarningsAndExactTwoAxisKnownOmittedCount() {
        var current = issues(1024, true);
        var summaries = IntStream.range(0, 16).mapToObj(index -> "latest_" + index).toList();
        var latest = new P10TemplateService.AttemptStatus(P10TemplateService.Classification.MIGRATION_FAILED,
                summaries, 1024, true);
        var response = P10TemplateValidateCommand.response(P10TemplateValidateCommand.Primary.STALE_CONTEXT, current, latest);
        assertEquals(0, response.returnCode());
        assertEquals(2032, response.displayOmitted());
        assertEquals(1008, latest.retainedButNotSummarized());
        assertTrue(response.upstreamTruncated());
        assertEquals(17, response.components().size());
        assertTrue(response.components().stream().skip(1).allMatch(component -> component.getString().contains("current[")));
        assertTrue(response.components().getFirst().getString().contains("lastAttempt=MIGRATION_FAILED"));
    }

    @Test
    void componentsAreSanitizedAndBoundedBeforeSendAtUtf16Coordinate() {
        var latest = new P10TemplateService.AttemptStatus(P10TemplateService.Classification.DECODE_FAILED,
                List.of("x".repeat(1024), "line\n\t😀"), 2, false);
        var response = P10TemplateValidateCommand.response(P10TemplateValidateCommand.Primary.UNAVAILABLE,
                ValidationResult.valid(), latest);
        assertTrue(response.components().stream().allMatch(component -> component.getString().length() <= 1024));
        assertTrue(response.components().stream().mapToInt(component -> component.getString().length()).sum() <= 17408);
        assertEquals(1024, response.components().get(1).getString().length());
        assertTrue(response.components().get(1).getString().endsWith("[truncated]"));
        assertFalse(response.components().get(2).getString().contains("\n"));
        assertFalse(response.components().get(2).getString().contains("\t"));
        assertFalse(response.components().get(2).getString().contains("😀"));
        assertEquals(0, response.displayOmitted());
    }

    @Test
    void outputIsImmutableAndNoDiagnosticMetadataValueIsRendered() {
        var report = issues(1, false);
        var response = P10TemplateValidateCommand.response(P10TemplateValidateCommand.Primary.READY_CURRENT,
                report, new P10TemplateService.AttemptStatus(P10TemplateService.Classification.ACCEPTED, List.of(), 0, false));
        assertThrows(UnsupportedOperationException.class, () -> response.components().clear());
        assertEquals("gramarye:warning current[0]", response.components().get(1).getString());
        assertFalse(response.upstreamTruncated());
    }

    @Test
    void handlerEarlyRoutesCaptureOneTupleWithoutCallingValidationOrCurrentRead() {
        var body = new P10TemplateBody(List.of(), AppearanceDocument.defaultAppearance());
        var latest = new P10TemplateService.AttemptStatus(
                P10TemplateService.Classification.UNKNOWN_TYPE, List.of(), 0, false);
        var expected = List.of("UNSUPPORTED_TEMPLATE_ID", "UNAVAILABLE", "STALE_CONTEXT");
        for (int index = 0; index < expected.size(); index++) {
            var captured = new P10TemplateService.Capture(index == 1 ? Optional.empty() : Optional.of(body),
                    new Object(), index == 1 ? P10TemplateService.Origin.UNAVAILABLE : P10TemplateService.Origin.CURRENT,
                    latest, index != 2);
            var id = index == 0 ? ResourceLocation.fromNamespaceAndPath("gramarye", "unsupported")
                    : P10TemplateCodec.TEMPLATE_ID;
            var calls = new ArrayList<String>();
            var result = P10TemplateValidateCommand.inspectTemplate(id, () -> {
                calls.add("capture");
                return captured;
            }, active -> {
                calls.add("validate");
                throw new AssertionError("early branch must not validate");
            }, snapshot -> {
                calls.add("current");
                throw new AssertionError("early branch must not recheck publication");
            });
            assertEquals(List.of("capture"), calls);
            assertEquals(0, result.returnCode());
            assertEquals(1, result.components().size());
            assertEquals(expected.get(index) + " lastAttempt=UNKNOWN_TYPE displayOmitted=0 upstreamTruncated=false",
                    result.components().getFirst().getString());
        }
    }

    @Test
    void handlerRevalidatesExactActiveBodyThenChecksTheSameTupleForEveryPrimary() {
        var body = new P10TemplateBody(List.of(), AppearanceDocument.defaultAppearance());
        for (var origin : List.of(P10TemplateService.Origin.CURRENT, P10TemplateService.Origin.LKG)) {
            for (var classification : P10TemplateValidation.Classification.values()) {
                for (boolean current : new boolean[] {false, true}) {
                    var latest = new P10TemplateService.AttemptStatus(
                            P10TemplateService.Classification.UNKNOWN_TYPE, List.of(), 0, false);
                    var captured = new P10TemplateService.Capture(Optional.of(body), new Object(), origin, latest, true);
                    var calls = new ArrayList<String>();
                    var result = P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
                        calls.add("capture");
                        return captured;
                    }, active -> {
                        calls.add("validate");
                        assertSame(body, active);
                        return new P10TemplateValidation.Result(classification, ValidationResult.valid());
                    }, snapshot -> {
                        calls.add("current");
                        assertSame(captured, snapshot);
                        return current;
                    });
                    var primary = !current ? "STALE_CONTEXT"
                            : classification == P10TemplateValidation.Classification.CONTEXT_UNAVAILABLE
                                    ? "INTERNAL_UNAVAILABLE"
                                    : classification != P10TemplateValidation.Classification.ACCEPTED
                                            ? "STALE_CONTEXT"
                                            : origin == P10TemplateService.Origin.CURRENT ? "READY_CURRENT" : "READY_LKG";
                    assertEquals(List.of("capture", "validate", "current"), calls);
                    assertEquals(primary.startsWith("READY_") ? 1 : 0, result.returnCode());
                    assertEquals(primary + " lastAttempt=UNKNOWN_TYPE displayOmitted=0 upstreamTruncated=false",
                            result.components().getFirst().getString());
                }
            }
        }
    }

    @Test
    void handlerReadStageFaultsPropagateTheSameThrowableAndNeverRetry() {
        var captured = new P10TemplateService.Capture(Optional.of(new P10TemplateBody(
                List.of(), AppearanceDocument.defaultAppearance())), new Object(), P10TemplateService.Origin.CURRENT,
                new P10TemplateService.AttemptStatus(P10TemplateService.Classification.ACCEPTED, List.of(), 0, false), true);
        for (Throwable fault : List.of(new IllegalStateException("handler read"), new AssertionError("handler read"))) {
            for (var stage : List.of("capture", "validate", "current")) {
                var calls = new ArrayList<String>();
                assertSame(fault, assertThrows(Throwable.class, () ->
                        P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
                            calls.add("capture");
                            if (stage.equals("capture")) throw unchecked(fault);
                            return captured;
                        }, body -> {
                            calls.add("validate");
                            if (stage.equals("validate")) throw unchecked(fault);
                            return new P10TemplateValidation.Result(
                                    P10TemplateValidation.Classification.ACCEPTED, ValidationResult.valid());
                        }, snapshot -> {
                            calls.add("current");
                            throw unchecked(fault);
                        })));
                assertEquals(stage.equals("capture") ? List.of("capture")
                        : stage.equals("validate") ? List.of("capture", "validate")
                                : List.of("capture", "validate", "current"), calls);
            }
        }
    }

    @Test
    void realCollectorSaturationFatalFlowsThroughActualHandlerDecisionAndResponse() throws IOException {
        for (var classification : List.of(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                P10TemplateValidation.Classification.MIGRATION_FAILED, P10TemplateValidation.Classification.DECODE_FAILED,
                P10TemplateValidation.Classification.FUTURE_SCHEMA, P10TemplateValidation.Classification.SEMANTIC_INVALID)) {
            var body = P10TemplateValidationTest.saturatedProductionBody(classification);
            var failure = P10TemplateValidationTest.saturatedProductionFailure(classification);
            assertEquals(1024, failure.report().issues().size());
            assertTrue(failure.report().issues().stream().allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
            assertTrue(failure.report().omittedError());
            var latest = new P10TemplateService.AttemptStatus(
                    P10TemplateService.Classification.valueOf(failure.classification().name()),
                    failure.report().issues().stream().limit(16).map(P10TemplateService::summary).toList(),
                    failure.report().issues().size(), failure.report().truncated());
            for (var origin : List.of(P10TemplateService.Origin.CURRENT, P10TemplateService.Origin.LKG)) {
                var capture = new P10TemplateService.Capture(Optional.of(body), new Object(), origin, latest, true);
                var calls = new ArrayList<String>();
                var result = P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
                    calls.add("capture");
                    return capture;
                }, active -> {
                    calls.add("validate");
                    assertSame(body, active);
                    return P10TemplateValidationTest.saturatedProductionFailure(active);
                }, current -> {
                    calls.add("current");
                    assertSame(capture, current);
                    return true;
                });
                assertEquals(List.of("capture", "validate", "current"), calls);
                assertEquals(0, result.returnCode());
                assertEquals("STALE_CONTEXT lastAttempt=" + classification
                        + " displayOmitted=2032 upstreamTruncated=true", result.components().getFirst().getString());
                assertEquals(17, result.components().size());
                assertEquals(2032, result.displayOmitted());
                assertTrue(result.upstreamTruncated());
            }
        }
    }

    @Test
    void validActiveBodyRevalidationRemainsIndependentOfEveryRealSaturatedRejectedAttempt() throws IOException {
        var activeA = P10TemplateValidationTest.saturatedProductionBody(P10TemplateValidation.Classification.ACCEPTED);
        for (var classification : List.of(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                P10TemplateValidation.Classification.MIGRATION_FAILED, P10TemplateValidation.Classification.DECODE_FAILED,
                P10TemplateValidation.Classification.FUTURE_SCHEMA, P10TemplateValidation.Classification.SEMANTIC_INVALID)) {
            var rejectedB = P10TemplateValidationTest.saturatedProductionFailure(classification);
            var latest = new P10TemplateService.AttemptStatus(
                    P10TemplateService.Classification.valueOf(rejectedB.classification().name()),
                    rejectedB.report().issues().stream().limit(16).map(P10TemplateService::summary).toList(),
                    rejectedB.report().issues().size(), rejectedB.report().truncated());
            var captured = new P10TemplateService.Capture(Optional.of(activeA), new Object(),
                    P10TemplateService.Origin.LKG, latest, true);
            var calls = new ArrayList<String>();
            var ready = P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
                calls.add("capture");
                return captured;
            }, body -> {
                calls.add("validate A");
                assertSame(activeA, body);
                var result = acceptedProductionResult(body);
                assertTrue(result.ready());
                assertEquals(ValidationResult.valid(), result.report());
                return result;
            }, snapshot -> {
                calls.add("current");
                assertSame(captured, snapshot);
                return true;
            });
            assertEquals(List.of("capture", "validate A", "current"), calls);
            assertEquals(1, ready.returnCode());
            assertEquals("READY_LKG lastAttempt=" + classification
                    + " displayOmitted=1008 upstreamTruncated=true", ready.components().getFirst().getString());
            assertEquals(17, ready.components().size());

            calls.clear();
            var absent = new P10TemplateService.Capture(Optional.empty(), new Object(),
                    P10TemplateService.Origin.UNAVAILABLE, latest, false);
            var unavailable = P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
                calls.add("capture");
                return absent;
            }, body -> {
                throw new AssertionError("rejected B must not be revalidated as active A");
            }, snapshot -> {
                throw new AssertionError("no active body needs no currentness read");
            });
            assertEquals(List.of("capture"), calls);
            assertEquals(0, unavailable.returnCode());
            assertEquals("UNAVAILABLE lastAttempt=" + classification
                    + " displayOmitted=1008 upstreamTruncated=true", unavailable.components().getFirst().getString());
        }
    }

    @Test
    void actualProductionValidationKeepsCurrentCapturedBodyReady() throws IOException {
        var body = P10TemplateValidationTest.saturatedProductionBody(P10TemplateValidation.Classification.ACCEPTED);
        var captured = new P10TemplateService.Capture(Optional.of(body), new Object(), P10TemplateService.Origin.CURRENT,
                new P10TemplateService.AttemptStatus(P10TemplateService.Classification.ACCEPTED, List.of(), 0, false), true);
        var calls = new ArrayList<String>();
        var result = P10TemplateValidateCommand.inspectTemplate(P10TemplateCodec.TEMPLATE_ID, () -> {
            calls.add("capture");
            return captured;
        }, active -> {
            calls.add("validate");
            assertSame(body, active);
            var validation = acceptedProductionResult(active);
            assertTrue(validation.ready());
            assertEquals(ValidationResult.valid(), validation.report());
            return validation;
        }, snapshot -> {
            calls.add("current");
            assertSame(captured, snapshot);
            return true;
        });
        assertEquals(List.of("capture", "validate", "current"), calls);
        assertEquals(1, result.returnCode());
        assertEquals(1, result.components().size());
        assertEquals(0, result.displayOmitted());
        assertFalse(result.upstreamTruncated());
        assertEquals("READY_CURRENT lastAttempt=ACCEPTED displayOmitted=0 upstreamTruncated=false",
                result.components().getFirst().getString());
    }

    private static P10TemplateValidation.Result acceptedProductionResult(P10TemplateBody body) {
        // Keep the actual production singleton descriptors: a warning-injecting descriptor
        // wrapper is deliberately not a supported starter, even when its payload is valid.
        var triggers = Map.<ResourceLocation, TriggerType<?>>of(
                P9StarterSkillContent.ACTIVE_CAST_ID, P9ActiveCastTriggerType.INSTANCE,
                P9StarterSkillContent.EFFECT_HIT_ID, P9EffectHitTriggerType.INSTANCE);
        var actions = Map.<ResourceLocation, ActionType<?>>of(
                P9StarterSkillContent.SPAWN_PROJECTILE_ID, P9SpawnProjectileActionType.INSTANCE,
                P9StarterSkillContent.DAMAGE_ID, P9DamageActionType.INSTANCE);
        var resolver = new SkillCandidateResolver(new TriggerTypeLookup() {
            @Override
            public Optional<TriggerType<?>> find(ResourceLocation id) {
                return Optional.ofNullable(triggers.get(id));
            }
            @Override
            public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
                return triggers.entrySet().stream().filter(entry -> entry.getValue() == descriptor)
                        .map(Map.Entry::getKey).findFirst();
            }
        }, new ActionTypeLookup() {
            @Override
            public Optional<ActionType<?>> find(ResourceLocation id) {
                return Optional.ofNullable(actions.get(id));
            }
            @Override
            public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
                return actions.entrySet().stream().filter(entry -> entry.getValue() == descriptor)
                        .map(Map.Entry::getKey).findFirst();
            }
        });
        return new P10TemplateValidation(SkillSubmissionPolicyProvider.defaults(),
                P8ServerPresentationService.create(), resolver, ProfileAvailabilityView.unknown())
                .validate(body, new ValidationContext(MagicPolicyLimits.DEFAULTS), ValidationResult.valid());
    }

    private static RuntimeException unchecked(Throwable failure) {
        if (failure instanceof Error error) throw error;
        return (RuntimeException) failure;
    }

    private static ValidationResult issues(int count, boolean fatalAfterCap) {
        var collector = new ValidationCollector();
        for (var index = 0; index < count; index++) {
            collector.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().field("current").index(index),
                    ValidationIssueMetadata.none()));
        }
        if (fatalAfterCap) {
            collector.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "actual_fatal"),
                    ValidationSeverity.ERROR, ValidationPath.empty(), ValidationIssueMetadata.none()));
        }
        return collector.result();
    }

    private static Path projectRoot() {
        for (var path = Path.of("").toAbsolutePath().normalize(); path != null; path = path.getParent()) {
            if (Files.isRegularFile(path.resolve("settings.gradle"))) return path;
        }
        throw new AssertionError("Project root unavailable");
    }
}
