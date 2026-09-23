package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P10TemplateValidationTest {
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void currentAAndBAndRealLegacyMigrationUseTheRegisteredProductionDescriptors() throws IOException {
        for (var magnitude : new int[] {4000, 5000}) {
            var json = builtIn();
            damage(json).getAsJsonObject("payload").addProperty("magnitude", magnitude);
            var body = body(json);
            var result = validation().validate(body, CONTEXT, ValidationResult.valid());
            assertEquals(P10TemplateValidation.Classification.ACCEPTED, result.classification());
            assertTrue(result.ready());
            assertFalse(result.report().hasErrors());
            assertEquals(1, body.nodes().get(1).action().schemaVersion());
        }
        var legacy = builtIn();
        damage(legacy).addProperty("schema_version", 0);
        var body = body(legacy);
        assertTrue(validation().validate(body, CONTEXT, ValidationResult.valid()).ready());
        assertEquals(0, body.nodes().get(1).action().schemaVersion());
        assertEquals(damage(legacy).get("payload"), body.nodes().get(1).action().copyRawPayload().getValue());
    }

    @Test
    void actualUnknownMigrationDecodeFutureAndSemanticFactsRemainExclusiveAfterCollectorFills() throws IOException {
        var cases = new JsonObject[5];
        for (var index = 0; index < cases.length; index++) cases[index] = builtIn();
        damage(cases[0]).addProperty("type", "gramarye:unregistered");
        damage(cases[0]).addProperty("schema_version", 999);
        damage(cases[1]).addProperty("schema_version", 0);
        damage(cases[1]).getAsJsonObject("payload").addProperty("magnitude", 5000);
        damage(cases[2]).getAsJsonObject("payload").addProperty("unknown", true);
        damage(cases[3]).addProperty("schema_version", 2);
        damage(cases[4]).getAsJsonObject("payload").addProperty("magnitude", 4500);
        var expected = new P10TemplateValidation.Classification[] {
            P10TemplateValidation.Classification.UNKNOWN_TYPE,
            P10TemplateValidation.Classification.MIGRATION_FAILED,
            P10TemplateValidation.Classification.DECODE_FAILED,
            P10TemplateValidation.Classification.FUTURE_SCHEMA,
            P10TemplateValidation.Classification.SEMANTIC_INVALID
        };
        var full = warningPrefix(1024);
        for (var index = 0; index < cases.length; index++) {
            var candidate = body(cases[index]);
            var ordinary = validation().validate(candidate, CONTEXT, ValidationResult.valid());
            var capped = validation().validate(candidate, CONTEXT, full);
            assertEquals(expected[index], ordinary.classification());
            assertEquals(expected[index], capped.classification());
            assertFalse(capped.ready());
            assertTrue(capped.report().hasErrors());
            assertTrue(capped.report().truncated());
            assertTrue(capped.report().omittedError());
            assertEquals(full.issues(), capped.report().issues());
            assertEquals(1024, capped.report().issues().size());
            assertTrue(capped.report().issues().stream().allMatch(issue -> issue.severity() == ValidationSeverity.WARNING));
        }
    }

    @Test
    void actualEarlyDescriptorWarningsFillThePipelineBeforeEveryLateProductionFatal() throws IOException {
        for (var classification : List.of(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                P10TemplateValidation.Classification.MIGRATION_FAILED,
                P10TemplateValidation.Classification.DECODE_FAILED,
                P10TemplateValidation.Classification.FUTURE_SCHEMA,
                P10TemplateValidation.Classification.SEMANTIC_INVALID)) {
            var result = saturatedProductionFailure(classification);
            assertEquals(classification, result.classification());
            assertFalse(result.ready());
            assertEquals(1024, result.report().issues().size());
            assertTrue(result.report().hasErrors());
            assertTrue(result.report().truncated());
            assertTrue(result.report().omittedError());
            for (var index = 0; index < 1024; index++) {
                var issue = result.report().issues().get(index);
                assertEquals(ValidationSeverity.WARNING, issue.severity());
                assertEquals(ValidationPath.empty().field("nodes").index(0).field("trigger")
                        .field("payload").field("early").index(index), issue.path());
            }
        }
    }

    @Test
    void envelopeOrderSelectsFirstProductionFatalInsteadOfLaterSeverityOrMapOrder() throws IOException {
        var json = builtIn();
        var first = json.getAsJsonArray("nodes").get(0).getAsJsonObject();
        first.getAsJsonObject("action").getAsJsonObject("payload").addProperty("profile_code", 1);
        damage(json).addProperty("type", "gramarye:unknown_later");
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID,
                validation().validate(body(json), CONTEXT, warningPrefix(1024)).classification());
        first.getAsJsonObject("trigger").addProperty("type", "gramarye:unknown_first");
        assertEquals(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                validation().validate(body(json), CONTEXT, warningPrefix(1024)).classification());
        first.getAsJsonObject("trigger").addProperty("type", "gramarye:active_cast");
        first.getAsJsonObject("trigger").addProperty("schema_version", 1);
        assertEquals(P10TemplateValidation.Classification.FUTURE_SCHEMA,
                validation().validate(body(json), CONTEXT, warningPrefix(1024)).classification());
    }

    @Test
    void nodeZeroSemanticRunsBeforeNextEnvelopeLookup() throws IOException {
        var calls = new ArrayList<String>();
        var descriptor = new TriggerType<P9ActiveCastTriggerPayloadV0>() {
            private final P9ActiveCastTriggerType delegate = P9ActiveCastTriggerType.INSTANCE;

            @Override
            public int currentPayloadSchemaVersion() {
                return delegate.currentPayloadSchemaVersion();
            }

            @Override
            public PayloadMigrationPlan payloadMigrationPlan() {
                return delegate.payloadMigrationPlan();
            }

            @Override
            public Optional<TriggerPayloadInspector<P9ActiveCastTriggerPayloadV0>> payloadInspector() {
                return delegate.payloadInspector();
            }

            @Override
            public MapCodec<P9ActiveCastTriggerPayloadV0> payloadCodec() {
                return delegate.payloadCodec();
            }

            @Override
            public TriggerCapabilities capabilities() {
                return delegate.capabilities();
            }

            @Override
            public ValidationResult validate(P9ActiveCastTriggerPayloadV0 payload, ValidationContext context) {
                calls.add("node0.trigger.semantic");
                return delegate.validate(payload, context);
            }
        };
        var triggers = new TriggerTypeLookup() {
            private final ExactTriggerLookup delegate = new ExactTriggerLookup();

            @Override
            public Optional<TriggerType<?>> find(ResourceLocation typeId) {
                calls.add("lookup:" + typeId);
                return P9StarterSkillContent.ACTIVE_CAST_ID.equals(typeId)
                        ? Optional.of(descriptor) : delegate.find(typeId);
            }

            @Override
            public Optional<ResourceLocation> keyOf(TriggerType<?> type) {
                return type == descriptor ? Optional.of(P9StarterSkillContent.ACTIVE_CAST_ID) : delegate.keyOf(type);
            }
        };
        var actions = new ActionTypeLookup() {
            private final ExactActionLookup delegate = new ExactActionLookup();

            @Override
            public Optional<ActionType<?>> find(ResourceLocation typeId) {
                calls.add("lookup:" + typeId);
                return delegate.find(typeId);
            }

            @Override
            public Optional<ResourceLocation> keyOf(ActionType<?> type) {
                return delegate.keyOf(type);
            }
        };
        var validation = new P10TemplateValidation(SkillSubmissionPolicyProvider.defaults(),
                P8ServerPresentationService.create(), new SkillCandidateResolver(triggers, actions),
                ProfileAvailabilityView.unknown());
        validation.validate(body(builtIn()), CONTEXT, ValidationResult.valid());

        var semantic = calls.indexOf("node0.trigger.semantic");
        var nextLookup = calls.indexOf("lookup:" + P9StarterSkillContent.SPAWN_PROJECTILE_ID);
        assertTrue(semantic >= 0, "The production descriptor semantic stage must run: " + calls);
        assertTrue(nextLookup >= 0, "The next production envelope lookup must run: " + calls);
        assertTrue(semantic < nextLookup,
                "The actual node0 trigger semantic stage must precede the next envelope lookup: " + calls);
        assertEquals(1, calls.stream().filter("node0.trigger.semantic"::equals).count(),
                "The whole-skill pass must not repeat the descriptor semantic stage");
    }

    @Test
    void warningsIncludingAFullCollectorDoNotMakeAnOtherwiseReadyBodyFail() throws IOException {
        var full = warningPrefix(1024);
        var result = validation().validate(body(builtIn()), CONTEXT, full);
        assertTrue(result.ready());
        assertFalse(result.report().hasErrors());
        assertEquals(full.issues(), result.report().issues());
    }

    @Test
    void supportedShapeUsesWholeSkillRulesAndCurrentPolicyAfterDescriptorResolution() throws IOException {
        var json = builtIn();
        json.getAsJsonArray("nodes").remove(1);
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID,
                validation().validate(body(json), CONTEXT, ValidationResult.valid()).classification());
        var defaults = MagicPolicyLimits.DEFAULTS;
        var oneNode = new MagicPolicyLimits(1, defaults.maxStringLength(), defaults.maxRawPayloadBytes(),
                defaults.maxRuntimeTags(), defaults.maxVisitedTargets(), defaults.maxAppearanceIntensity(),
                defaults.maxUnparsedAppearanceDepth(), defaults.maxUnparsedAppearanceNodes(),
                defaults.maxSkillDocumentDepth(), defaults.maxSkillDocumentBytes(), defaults.maxSkillDocumentTreeNodes());
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID,
                validation().validate(body(builtIn()), new ValidationContext(oneNode), warningPrefix(1024)).classification());
    }

    @Test
    void structuralNodeAdmissionDoesNotMakeABoundedTypedPrefixReady() throws IOException {
        for (var count : List.of(0, 1, 2, 3, 64)) {
            var json = builtIn();
            var original = json.getAsJsonArray("nodes");
            var nodes = new com.google.gson.JsonArray();
            for (int index = 0; index < count; index++) {
                nodes.add(original.get(Math.min(index, 1)).deepCopy());
            }
            json.add("nodes", nodes);
            var candidate = body(json);
            assertEquals(count, candidate.nodes().size(), "Ingress retains the entire bounded raw body");
            var result = validation().validate(candidate, CONTEXT, ValidationResult.valid());
            assertEquals(count == 2 ? P10TemplateValidation.Classification.ACCEPTED
                    : P10TemplateValidation.Classification.SEMANTIC_INVALID, result.classification());
            assertEquals(count == 2, result.ready());
        }
    }

    @Test
    void extraUnknownNodeIsWholeShapeInvalidButAnAuthorizedEnvelopeFatalStillWins() throws IOException {
        var json = builtIn();
        var third = json.getAsJsonArray("nodes").get(1).deepCopy().getAsJsonObject();
        third.getAsJsonObject("trigger").addProperty("type", "gramarye:unresolved_third_trigger");
        third.getAsJsonObject("action").addProperty("type", "gramarye:unresolved_third_action");
        json.getAsJsonArray("nodes").add(third);
        var result = validation().validate(body(json), CONTEXT, ValidationResult.valid());
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID, result.classification());
        assertFalse(result.ready());
        assertFalse(result.report().hasErrors(), "No third-node typed lookup contributes diagnostics");
        damage(json).addProperty("type", "gramarye:unresolved_authorized_action");
        assertEquals(P10TemplateValidation.Classification.UNKNOWN_TYPE,
                validation().validate(body(json), CONTEXT, ValidationResult.valid()).classification());
    }

    @Test
    void malformedThirdNodeIsRejectedByIngressBeforeAnyAuthorizedTypedFailure() throws IOException {
        var json = builtIn();
        json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("trigger")
                .addProperty("type", "gramarye:unresolved_first_trigger");
        var third = json.getAsJsonArray("nodes").get(1).deepCopy().getAsJsonObject();
        third.remove("action");
        json.getAsJsonArray("nodes").add(third);
        var rejected = assertInstanceOf(P10TemplateCodec.Rejected.class,
                P10TemplateCodec.decode(new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8))));
        assertEquals(P10TemplateCodec.DecodeReason.SHELL, rejected.reason());
    }

    @Test
    void extraNodeCannotPublishTheValidPrefixAfterActualDescriptorWarningsFillTheCollector() throws IOException {
        var json = builtIn();
        json.getAsJsonArray("nodes").add(json.getAsJsonArray("nodes").get(1).deepCopy());
        var result = saturatedProductionFailure(body(json));
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID, result.classification());
        assertFalse(result.ready());
        assertEquals(1024, result.report().issues().size());
        assertFalse(result.report().hasErrors(), "The independent count fatal must survive a warning-only report");
    }

    @Test
    void unsupportedProductShapeIsNotReadyEvenWhenGenericReportHasNoErrors() throws IOException {
        var json = builtIn();
        json.getAsJsonArray("nodes").remove(1);
        var result = validation().validate(body(json), CONTEXT, ValidationResult.valid());
        assertEquals(P10TemplateValidation.Classification.SEMANTIC_INVALID, result.classification());
        assertFalse(result.ready());
        assertFalse(result.report().hasErrors());
        assertTrue(result.report().issues().isEmpty());
    }

    @Test
    void profileContextIsQueriedAfreshAndUnexpectedFailurePropagatesUnchanged() throws IOException {
        var json = builtIn();
        json.add("appearance", JsonParser.parseString(
                "{\"sound_profile\":{\"mode\":\"specified\",\"id\":\"gramarye:test_sound\"}}"));
        var candidate = body(json);
        var calls = new int[1];
        var available = validation((field, id) -> {
            calls[0]++;
            return ProfileAvailability.AVAILABLE;
        });
        assertTrue(available.validate(candidate, CONTEXT, ValidationResult.valid()).ready());
        assertTrue(calls[0] > 0);
        var priorCalls = calls[0];
        assertTrue(available.validate(candidate, CONTEXT, ValidationResult.valid()).ready());
        assertTrue(calls[0] > priorCalls);
        var failure = new AssertionError("profile owner failure");
        var failing = validation((field, id) -> { throw failure; });
        assertSame(failure, assertThrows(AssertionError.class,
                () -> failing.validate(candidate, CONTEXT, warningPrefix(1024))));
    }

    @Test
    void profileRuntimeFaultPropagatesSameObjectInsteadOfBecomingReadyWarning() throws IOException {
        var json = builtIn();
        json.add("appearance", JsonParser.parseString(
                "{\"sound_profile\":{\"mode\":\"specified\",\"id\":\"gramarye:test_sound\"}}"));
        var candidate = body(json);
        var failure = new IllegalStateException("unexpected profile owner runtime failure");
        var failing = validation((field, id) -> { throw failure; });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> failing.validate(candidate, CONTEXT, ValidationResult.valid())));
    }

    private static P10TemplateValidation validation() {
        return validation(ProfileAvailabilityView.unknown());
    }

    /** Shared test-only real pipeline result; no external prefix collector or fabricated rejection. */
    static P10TemplateValidation.Result saturatedProductionFailure(
            P10TemplateValidation.Classification classification) throws IOException {
        return saturatedProductionFailure(saturatedProductionBody(classification));
    }

    static P10TemplateBody saturatedProductionBody(
            P10TemplateValidation.Classification classification) throws IOException {
        var json = builtIn();
        switch (classification) {
            case ACCEPTED -> { /* The built-in body stays valid after the warning-only descriptor. */ }
            case UNKNOWN_TYPE -> damage(json).addProperty("type", "gramarye:unknown_after_warnings");
            case MIGRATION_FAILED -> {
                damage(json).addProperty("schema_version", 0);
                damage(json).getAsJsonObject("payload").addProperty("magnitude", 5000);
            }
            case DECODE_FAILED -> damage(json).getAsJsonObject("payload").addProperty("unknown", true);
            case FUTURE_SCHEMA -> damage(json).addProperty("schema_version", 2);
            case SEMANTIC_INVALID -> damage(json).getAsJsonObject("payload").addProperty("magnitude", 4500);
            default -> throw new IllegalArgumentException("Expected acceptance or a late production fatal classification");
        }
        return body(json);
    }

    static P10TemplateValidation.Result saturatedProductionFailure(P10TemplateBody body) {
        var warnings = new ArrayList<ValidationIssue>(1024);
        for (var index = 0; index < 1024; index++) {
            warnings.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "early.warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().field("early").index(index),
                    ValidationIssueMetadata.none()));
        }
        var descriptorReport = new ValidationResult(warnings, false, false);
        var semanticCalls = new int[1];
        var descriptor = new TriggerType<P9ActiveCastTriggerPayloadV0>() {
            private final P9ActiveCastTriggerType delegate = P9ActiveCastTriggerType.INSTANCE;

            @Override
            public int currentPayloadSchemaVersion() { return delegate.currentPayloadSchemaVersion(); }
            @Override
            public PayloadMigrationPlan payloadMigrationPlan() { return delegate.payloadMigrationPlan(); }
            @Override
            public Optional<TriggerPayloadInspector<P9ActiveCastTriggerPayloadV0>> payloadInspector() {
                return delegate.payloadInspector();
            }
            @Override
            public MapCodec<P9ActiveCastTriggerPayloadV0> payloadCodec() { return delegate.payloadCodec(); }
            @Override
            public TriggerCapabilities capabilities() { return delegate.capabilities(); }
            @Override
            public ValidationResult validate(P9ActiveCastTriggerPayloadV0 payload, ValidationContext context) {
                semanticCalls[0]++;
                assertEquals(ValidationResult.valid(), delegate.validate(payload, context));
                return descriptorReport;
            }
        };
        var triggers = new TriggerTypeLookup() {
            private final ExactTriggerLookup delegate = new ExactTriggerLookup();

            @Override
            public Optional<TriggerType<?>> find(ResourceLocation id) {
                return P9StarterSkillContent.ACTIVE_CAST_ID.equals(id)
                        ? Optional.of(descriptor) : delegate.find(id);
            }
            @Override
            public Optional<ResourceLocation> keyOf(TriggerType<?> type) {
                return type == descriptor ? Optional.of(P9StarterSkillContent.ACTIVE_CAST_ID) : delegate.keyOf(type);
            }
        };
        var validation = new P10TemplateValidation(SkillSubmissionPolicyProvider.defaults(),
                P8ServerPresentationService.create(), new SkillCandidateResolver(triggers, new ExactActionLookup()),
                ProfileAvailabilityView.unknown());
        var result = validation.validate(body, CONTEXT, ValidationResult.valid());
        assertEquals(1, semanticCalls[0]);
        return result;
    }

    private static P10TemplateValidation validation(ProfileAvailabilityView profiles) {
        return new P10TemplateValidation(SkillSubmissionPolicyProvider.defaults(),
                P8ServerPresentationService.create(),
                new SkillCandidateResolver(new ExactTriggerLookup(), new ExactActionLookup()), profiles);
    }

    private static ValidationResult warningPrefix(int count) {
        var collector = new ValidationCollector();
        for (var index = 0; index < count; index++) {
            collector.add(new ValidationIssue(ValidationIssueCode.fromNamespaceAndPath("gramarye", "prefix.warning"),
                    ValidationSeverity.WARNING, ValidationPath.empty().field("prefix").index(index),
                    ValidationIssueMetadata.none()));
        }
        return collector.result();
    }

    private static JsonObject builtIn() throws IOException {
        try (var stream = P10TemplateValidationTest.class.getResourceAsStream(
                "/data/gramarye/gramarye/skill_templates/starter_bolt_v0.json")) {
            if (stream == null) throw new IOException("Missing fixed production template");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject damage(JsonObject root) {
        return root.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("action");
    }

    private static P10TemplateBody body(JsonObject root) throws IOException {
        return assertInstanceOf(P10TemplateCodec.Decoded.class,
                P10TemplateCodec.decode(new ByteArrayInputStream(root.toString().getBytes(StandardCharsets.UTF_8)))).body();
    }

    private static final class ExactTriggerLookup implements TriggerTypeLookup {
        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            if (P9StarterSkillContent.ACTIVE_CAST_ID.equals(typeId)) return Optional.of(P9ActiveCastTriggerType.INSTANCE);
            if (P9StarterSkillContent.EFFECT_HIT_ID.equals(typeId)) return Optional.of(P9EffectHitTriggerType.INSTANCE);
            return Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            if (descriptor == P9ActiveCastTriggerType.INSTANCE) return Optional.of(P9StarterSkillContent.ACTIVE_CAST_ID);
            if (descriptor == P9EffectHitTriggerType.INSTANCE) return Optional.of(P9StarterSkillContent.EFFECT_HIT_ID);
            return Optional.empty();
        }
    }

    private static final class ExactActionLookup implements ActionTypeLookup {
        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            if (P9StarterSkillContent.SPAWN_PROJECTILE_ID.equals(typeId)) return Optional.of(P9SpawnProjectileActionType.INSTANCE);
            if (P9StarterSkillContent.DAMAGE_ID.equals(typeId)) return Optional.of(P9DamageActionType.INSTANCE);
            return Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            if (descriptor == P9SpawnProjectileActionType.INSTANCE) return Optional.of(P9StarterSkillContent.SPAWN_PROJECTILE_ID);
            if (descriptor == P9DamageActionType.INSTANCE) return Optional.of(P9StarterSkillContent.DAMAGE_ID);
            return Optional.empty();
        }
    }
}
