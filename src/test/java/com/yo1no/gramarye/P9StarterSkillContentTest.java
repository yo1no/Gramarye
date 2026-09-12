package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P9StarterSkillContentTest {
    private static final SkillId FIRST_SKILL = new SkillId(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174901"));
    private static final SkillId SECOND_SKILL = new SkillId(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174902"));
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void exactIdsAndDescriptorSingletonsRemainCanonical() {
        assertAll(
                () -> assertEquals(id("active_cast"), P9StarterSkillContent.ACTIVE_CAST_ID),
                () -> assertEquals(id("spawn_projectile"), P9StarterSkillContent.SPAWN_PROJECTILE_ID),
                () -> assertEquals(id("effect_hit"), P9StarterSkillContent.EFFECT_HIT_ID),
                () -> assertEquals(id("damage"), P9StarterSkillContent.DAMAGE_ID),
                () -> assertSame(P9ActiveCastTriggerType.INSTANCE, P9ActiveCastTriggerType.INSTANCE),
                () -> assertSame(P9SpawnProjectileActionType.INSTANCE, P9SpawnProjectileActionType.INSTANCE),
                () -> assertSame(P9EffectHitTriggerType.INSTANCE, P9EffectHitTriggerType.INSTANCE),
                () -> assertSame(P9DamageActionType.INSTANCE, P9DamageActionType.INSTANCE));
    }

    @Test
    void payloadCodecsRoundTripOnlyTheExactCanonicalMaps() {
        assertCodec(
                P9ActiveCastTriggerType.INSTANCE.payloadCodec(),
                P9ActiveCastTriggerPayloadV0.INSTANCE,
                "{}");
        assertCodec(
                P9SpawnProjectileActionType.INSTANCE.payloadCodec(),
                new P9SpawnProjectileActionPayloadV0(0, 0L),
                "{\"profile_code\":0,\"mana_cost\":0}");
        assertCodec(
                P9EffectHitTriggerType.INSTANCE.payloadCodec(),
                new P9EffectHitTriggerPayloadV0(0, 0, true),
                "{\"source_node_index\":0,\"source_output_ordinal\":0,\"include_derived\":true}");
        assertCodec(
                P9DamageActionType.INSTANCE.payloadCodec(),
                new P9DamageActionPayloadV0(4_000L, 0L),
                "{\"magnitude\":4000,\"mana_cost\":0}");
    }

    @Test
    void payloadCodecsRejectMissingExtraWrongTypeFractionalAndOverflowInputs() {
        assertAll(
                () -> assertDecodeRejected(
                        P9ActiveCastTriggerType.INSTANCE.payloadCodec(), "{\"extra\":0}"),
                () -> assertDecodeRejected(
                        P9SpawnProjectileActionType.INSTANCE.payloadCodec(), "{\"profile_code\":0}"),
                () -> assertDecodeRejected(
                        P9SpawnProjectileActionType.INSTANCE.payloadCodec(),
                        "{\"profile_code\":0,\"mana_cost\":0,\"extra\":0}"),
                () -> assertDecodeRejected(
                        P9SpawnProjectileActionType.INSTANCE.payloadCodec(),
                        "{\"profile_code\":0.5,\"mana_cost\":0}"),
                () -> assertDecodeRejected(
                        P9EffectHitTriggerType.INSTANCE.payloadCodec(),
                        "{\"source_node_index\":0,\"source_output_ordinal\":0,\"include_derived\":0}"),
                () -> assertDecodeRejected(
                        P9DamageActionType.INSTANCE.payloadCodec(),
                        "{\"magnitude\":9223372036854775808,\"mana_cost\":0}"));
    }

    @Test
    void schemaZeroUsesOnlyTheInheritedEmptyMigrationPlan() {
        for (var descriptor : List.of(
                P9ActiveCastTriggerType.INSTANCE,
                P9EffectHitTriggerType.INSTANCE,
                P9SpawnProjectileActionType.INSTANCE,
                P9DamageActionType.INSTANCE)) {
            assertEquals(0, schemaVersion(descriptor));
            assertSame(PayloadMigrationPlan.empty(), migrationPlan(descriptor));
            assertTrue(migrationPlan(descriptor).verifyCoverage(0).isSuccess());
        }
    }

    @Test
    void semanticValidationReturnsPayloadRelativeErrorsForEveryClosedValue() {
        var spawn = P9SpawnProjectileActionType.INSTANCE.validate(
                new P9SpawnProjectileActionPayloadV0(1, 1L), CONTEXT);
        var hit = P9EffectHitTriggerType.INSTANCE.validate(
                new P9EffectHitTriggerPayloadV0(1, 1, false), CONTEXT);
        var damage = P9DamageActionType.INSTANCE.validate(
                new P9DamageActionPayloadV0(3_999L, 1L), CONTEXT);

        assertAll(
                () -> assertTrue(P9ActiveCastTriggerType.INSTANCE
                        .validate(P9ActiveCastTriggerPayloadV0.INSTANCE, CONTEXT)
                        .isValid()),
                () -> assertEquals(Set.of("profile_code", "mana_cost"), errorPaths(spawn)),
                () -> assertEquals(
                        Set.of("source_node_index", "source_output_ordinal", "include_derived"),
                        errorPaths(hit)),
                () -> assertEquals(Set.of("magnitude", "mana_cost"), errorPaths(damage)),
                () -> assertTrue(P9SpawnProjectileActionType.INSTANCE
                        .validate(new P9SpawnProjectileActionPayloadV0(0, 0L), CONTEXT)
                        .isValid()),
                () -> assertTrue(P9EffectHitTriggerType.INSTANCE
                        .validate(new P9EffectHitTriggerPayloadV0(0, 0, true), CONTEXT)
                        .isValid()),
                () -> assertTrue(P9DamageActionType.INSTANCE
                        .validate(new P9DamageActionPayloadV0(4_000L, 0L), CONTEXT)
                        .isValid()));
    }

    @Test
    void descriptorCapabilitiesAreTheExactClosedP9Set() {
        assertAll(
                () -> assertEquals(new TriggerCapabilities(
                                SourceRequirement.NONE,
                                TargetRequirement.NONE,
                                false,
                                Set.of(new TriggerEventKind(id("active_cast"))),
                                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                                Set.of(TriggerGranularity.PER_EVENT)),
                        P9ActiveCastTriggerType.INSTANCE.capabilities()),
                () -> assertEquals(new TriggerCapabilities(
                                SourceRequirement.PRIOR_NODE,
                                TargetRequirement.REQUIRED,
                                true,
                                Set.of(new TriggerEventKind(id("effect_hit"))),
                                Set.of(TriggerSourceScope.SOURCE_FAMILY),
                                Set.of(TriggerGranularity.PER_SOURCE)),
                        P9EffectHitTriggerType.INSTANCE.capabilities()),
                () -> assertEquals(spawnCapabilities(),
                        P9SpawnProjectileActionType.INSTANCE.capabilities()),
                () -> assertEquals(damageCapabilities(),
                        P9DamageActionType.INSTANCE.capabilities()));
    }

    @Test
    void descriptorInspectorsProjectTheExactSourceTargetReferenceAndOutputShape() {
        var activeCast = triggerProjection(
                P9ActiveCastTriggerType.INSTANCE,
                P9ActiveCastTriggerPayloadV0.INSTANCE);
        var spawn = actionProjection(
                P9SpawnProjectileActionType.INSTANCE,
                new P9SpawnProjectileActionPayloadV0(0, 0L));
        var hit = triggerProjection(
                P9EffectHitTriggerType.INSTANCE,
                new P9EffectHitTriggerPayloadV0(0, 0, true));
        var damage = actionProjection(
                P9DamageActionType.INSTANCE,
                new P9DamageActionPayloadV0(4_000L, 0L));

        assertAll(
                () -> assertEquals(SourceSelection.NONE, activeCast.sourceSelection()),
                () -> assertEquals(TargetSelection.NONE, activeCast.targetSelection()),
                () -> assertFalse(activeCast.providesCurrentTarget()),
                () -> assertTrue(activeCast.references().isEmpty()),
                () -> assertEquals(SourceSelection.NONE, spawn.sourceSelection()),
                () -> assertEquals(TargetSelection.NONE, spawn.targetSelection()),
                () -> assertTrue(spawn.references().isEmpty()),
                () -> assertEquals(Set.of(ActionOutputKind.PROJECTILE), spawn.producedOutputs()),
                () -> assertEquals(SourceSelection.PRIOR_NODE, hit.sourceSelection()),
                () -> assertEquals(TargetSelection.CURRENT_TARGET, hit.targetSelection()),
                () -> assertTrue(hit.providesCurrentTarget()),
                () -> assertEquals(1, hit.references().size()),
                () -> assertEquals(0, hit.references().getFirst().referencedNodeIndex()),
                () -> assertEquals(ReferenceRole.SOURCE, hit.references().getFirst().role()),
                () -> assertEquals("source_node_index", hit.references().getFirst().payloadPath().render()),
                () -> assertEquals(
                        Optional.of(ActionOutputKind.PROJECTILE),
                        hit.references().getFirst().requiredOutputKind()),
                () -> assertEquals(SourceSelection.NONE, damage.sourceSelection()),
                () -> assertEquals(TargetSelection.CURRENT_TARGET, damage.targetSelection()),
                () -> assertTrue(damage.references().isEmpty()),
                () -> assertTrue(damage.producedOutputs().isEmpty()));
    }

    @Test
    void canonicalDraftIsDeterministicCompleteAndImmutable() {
        var first = P9StarterSkillContent.canonicalDraft(FIRST_SKILL);
        var second = P9StarterSkillContent.canonicalDraft(FIRST_SKILL);

        assertAll(
                () -> assertEquals(first, second),
                () -> assertEquals(SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION, first.draftSchemaVersion()),
                () -> assertEquals(FIRST_SKILL, first.skillId()),
                () -> assertTrue(first.baseRevision().isEmpty()),
                () -> assertEquals(2, first.nodes().size()),
                () -> assertSame(AppearanceDocument.Default.INSTANCE, first.appearance()),
                () -> assertSame(
                        AppearanceOverrideDocument.None.INSTANCE,
                        first.nodes().get(0).appearanceOverride()),
                () -> assertSame(
                        AppearanceOverrideDocument.None.INSTANCE,
                        first.nodes().get(1).appearanceOverride()),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> first.nodes().add(first.nodes().getFirst())));

        var document = formalDocument(first, 0);
        assertAll(
                () -> assertEnvelope(document.nodes().get(0).trigger(), "active_cast", "{}"),
                () -> assertEnvelope(document.nodes().get(0).action(), "spawn_projectile",
                        "{\"profile_code\":0,\"mana_cost\":0}"),
                () -> assertEnvelope(document.nodes().get(1).trigger(), "effect_hit",
                        "{\"source_node_index\":0,\"source_output_ordinal\":0,\"include_derived\":true}"),
                () -> assertEnvelope(document.nodes().get(1).action(), "damage",
                        "{\"magnitude\":4000,\"mana_cost\":0}"));
    }

    @Test
    void canonicalDraftTraversesTheFormalResolutionValidationAndProjectionPath() {
        var document = formalDocument(P9StarterSkillContent.canonicalDraft(FIRST_SKILL), 0);
        var definition = validate(document);

        assertAll(
                () -> assertEquals(2, definition.nodes().size()),
                () -> assertSame(P9ActiveCastTriggerType.INSTANCE,
                        definition.nodes().get(0).trigger().descriptor()),
                () -> assertSame(P9SpawnProjectileActionType.INSTANCE,
                        definition.nodes().get(0).action().descriptor()),
                () -> assertSame(P9EffectHitTriggerType.INSTANCE,
                        definition.nodes().get(1).trigger().descriptor()),
                () -> assertSame(P9DamageActionType.INSTANCE,
                        definition.nodes().get(1).action().descriptor()),
                () -> assertEquals(
                        Set.of(ActionOutputKind.PROJECTILE),
                        definition.nodes().get(0).references().action().producedOutputs()),
                () -> assertEquals(
                        0,
                        definition.nodes().get(1).references().trigger()
                                .references().getFirst().referencedNodeIndex()),
                () -> assertTrue(P9StarterSkillContent.hasCanonicalGameplayFingerprint(definition)));
    }

    @Test
    void fingerprintIsTheExactOrderedGameplayTuple() {
        var definition = validate(formalDocument(
                P9StarterSkillContent.canonicalDraft(FIRST_SKILL), 0));
        var fingerprint = P9StarterSkillContent.fingerprintOf(definition).orElseThrow();

        assertEquals(
                new StarterGameplayFingerprintV0(
                        0,
                        id("active_cast"),
                        id("spawn_projectile"),
                        0,
                        0L,
                        0,
                        ActionOutputKind.PROJECTILE,
                        1,
                        id("effect_hit"),
                        SourceSelection.PRIOR_NODE,
                        0,
                        0,
                        true,
                        id("damage"),
                        4_000L,
                        0L),
                fingerprint);
    }

    @Test
    void fingerprintExcludesIdentityRevisionAndAppearance() {
        var canonical = validate(formalDocument(
                P9StarterSkillContent.canonicalDraft(FIRST_SKILL), 0));
        var customizedDraft = P9StarterSkillContent.canonicalDraft(SECOND_SKILL);
        customizedDraft = new SkillDraft(
                customizedDraft.draftSchemaVersion(),
                customizedDraft.skillId(),
                customizedDraft.baseRevision(),
                customizedDraft.nodes(),
                AppearanceDocument.decoded(new AppearanceDefinition(
                        OptionalInt.of(0x7f00ff00),
                        OptionalInt.empty(),
                        ProfileSelection.inherit(),
                        ProfileSelection.inherit(),
                        ProfileSelection.inherit(),
                        OptionalInt.of(500))));
        var customized = validate(formalDocument(customizedDraft, 77));

        assertAll(
                () -> assertNotEquals(canonical.reference(), customized.reference()),
                () -> assertNotEquals(canonical.appearance(), customized.appearance()),
                () -> assertEquals(
                        P9StarterSkillContent.fingerprintOf(canonical),
                        P9StarterSkillContent.fingerprintOf(customized)),
                () -> assertTrue(P9StarterSkillContent.hasCanonicalGameplayFingerprint(customized)));
    }

    @Test
    void fingerprintRejectsAFormallyValidNoncanonicalGraph() {
        var canonical = formalDocument(P9StarterSkillContent.canonicalDraft(FIRST_SKILL), 0);
        var noncanonical = new SkillDocument(
                canonical.schemaVersion(),
                canonical.skillId(),
                canonical.revision(),
                List.of(canonical.nodes().get(0), canonical.nodes().get(0)),
                canonical.appearance());
        var definition = validate(noncanonical);

        assertAll(
                () -> assertTrue(P9StarterSkillContent.fingerprintOf(definition).isEmpty()),
                () -> assertFalse(P9StarterSkillContent.hasCanonicalGameplayFingerprint(definition)));
    }

    @Test
    void registrationDeclaresExactlyFourEntriesAndRejectsRepeatedBootstrap() {
        var triggerIds = MagicRegistries.TRIGGER_TYPES.getEntries().stream()
                .map(holder -> holder.getId())
                .collect(Collectors.toUnmodifiableSet());
        var actionIds = MagicRegistries.ACTION_TYPES.getEntries().stream()
                .map(holder -> holder.getId())
                .collect(Collectors.toUnmodifiableSet());

        assertAll(
                () -> assertEquals(Set.of(id("active_cast"), id("effect_hit")), triggerIds),
                () -> assertEquals(Set.of(id("spawn_projectile"), id("damage")), actionIds),
                () -> assertThrows(
                        IllegalStateException.class,
                        P9StarterSkillContent::registerDefinitionTypes));
    }

    @Test
    void unknownRegistryKeysRemainUnknownWithoutAliasOrFallback() {
        var unknown = id("unknown_p9_content");
        assertAll(
                () -> assertTrue(new ExactTriggerLookup().find(unknown).isEmpty()),
                () -> assertTrue(new ExactActionLookup().find(unknown).isEmpty()),
                () -> assertTrue(new ExactTriggerLookup()
                        .keyOf(new AlternateTriggerType())
                        .isEmpty()),
                () -> assertTrue(new ExactActionLookup()
                        .keyOf(new AlternateActionType())
                        .isEmpty()));
    }

    private static ActionCapabilities spawnCapabilities() {
        return new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(ActionOutputKind.PROJECTILE),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());
    }

    private static ActionCapabilities damageCapabilities() {
        return new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.REQUIRED,
                false,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());
    }

    private static SkillDocument formalDocument(SkillDraft draft, int revision) {
        var nodes = draft.nodes().stream()
                .map(node -> new NodeDocument(
                        assertInstanceOf(DraftTriggerSlot.Present.class, node.trigger()).definition(),
                        assertInstanceOf(DraftActionSlot.Present.class, node.action()).definition(),
                        node.appearanceOverride()))
                .toList();
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                draft.skillId(),
                new SkillRevision(revision),
                nodes,
                draft.appearance());
    }

    private static ValidatedSkillDefinition validate(SkillDocument document) {
        var resolver = new SkillCandidateResolver(new ExactTriggerLookup(), new ExactActionLookup());
        var candidate = resolver.resolve(document, new SkillDocumentReadReport(List.of(), false));
        var analysis = new SkillValidationAnalyzer(
                        new NodeProjectionResolver(), ProfileAvailabilityView.unknown())
                .analyze(candidate, CONTEXT);
        assertFalse(analysis.report().hasErrors(), () -> analysis.report().toString());
        return assertInstanceOf(
                        SkillValidationOutcome.Accepted.class,
                        new SkillDefinitionProjector().project(analysis))
                .definition();
    }

    private static Set<String> errorPaths(com.yo1no.gramarye.magic.validation.ValidationResult result) {
        assertTrue(result.hasErrors());
        return result.errors().stream()
                .map(issue -> issue.path().render())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int schemaVersion(Object descriptor) {
        return descriptor instanceof TriggerType<?> trigger
                ? trigger.currentPayloadSchemaVersion()
                : ((ActionType<?>) descriptor).currentPayloadSchemaVersion();
    }

    private static PayloadMigrationPlan migrationPlan(Object descriptor) {
        return descriptor instanceof TriggerType<?> trigger
                ? trigger.payloadMigrationPlan()
                : ((ActionType<?>) descriptor).payloadMigrationPlan();
    }

    private static <P> void assertCodec(MapCodec<P> codec, P expected, String json) {
        var encoded = codec.codec().encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        var expectedJson = JsonParser.parseString(json);
        assertAll(
                () -> assertEquals(expectedJson, encoded),
                () -> assertEquals(expected, codec.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow()));
    }

    private static void assertDecodeRejected(MapCodec<?> codec, String json) {
        assertTrue(codec.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }

    private static void assertEnvelope(
            DefinitionEnvelope envelope, String path, String expectedPayload) {
        JsonElement raw = envelope.copyRawPayload().convert(JsonOps.INSTANCE).getValue();
        assertAll(
                () -> assertEquals(id(path), envelope.typeId()),
                () -> assertEquals(0, envelope.schemaVersion()),
                () -> assertEquals(JsonParser.parseString(expectedPayload), raw));
    }

    private static <P extends TriggerPayload> TriggerReferenceProjection triggerProjection(
            TriggerType<P> descriptor, P payload) {
        return assertInstanceOf(
                        PayloadInspectionResult.Success.class,
                        descriptor.payloadInspector().orElseThrow().inspect(payload))
                .projection() instanceof TriggerReferenceProjection projection
                        ? projection
                        : throwUnexpectedProjection();
    }

    private static <P extends ActionPayload> ActionReferenceProjection actionProjection(
            ActionType<P> descriptor, P payload) {
        return assertInstanceOf(
                        PayloadInspectionResult.Success.class,
                        descriptor.payloadInspector().orElseThrow().inspect(payload))
                .projection() instanceof ActionReferenceProjection projection
                        ? projection
                        : throwUnexpectedProjection();
    }

    private static <T> T throwUnexpectedProjection() {
        throw new AssertionError("unexpected inspector projection type");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static final class ExactTriggerLookup implements TriggerTypeLookup {
        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            if (P9StarterSkillContent.ACTIVE_CAST_ID.equals(typeId)) {
                return Optional.of(P9ActiveCastTriggerType.INSTANCE);
            }
            if (P9StarterSkillContent.EFFECT_HIT_ID.equals(typeId)) {
                return Optional.of(P9EffectHitTriggerType.INSTANCE);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            if (descriptor == P9ActiveCastTriggerType.INSTANCE) {
                return Optional.of(P9StarterSkillContent.ACTIVE_CAST_ID);
            }
            if (descriptor == P9EffectHitTriggerType.INSTANCE) {
                return Optional.of(P9StarterSkillContent.EFFECT_HIT_ID);
            }
            return Optional.empty();
        }
    }

    private static final class ExactActionLookup implements ActionTypeLookup {
        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            if (P9StarterSkillContent.SPAWN_PROJECTILE_ID.equals(typeId)) {
                return Optional.of(P9SpawnProjectileActionType.INSTANCE);
            }
            if (P9StarterSkillContent.DAMAGE_ID.equals(typeId)) {
                return Optional.of(P9DamageActionType.INSTANCE);
            }
            return Optional.empty();
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            if (descriptor == P9SpawnProjectileActionType.INSTANCE) {
                return Optional.of(P9StarterSkillContent.SPAWN_PROJECTILE_ID);
            }
            if (descriptor == P9DamageActionType.INSTANCE) {
                return Optional.of(P9StarterSkillContent.DAMAGE_ID);
            }
            return Optional.empty();
        }
    }

    private static final class AlternateTriggerType implements TriggerType<P9ActiveCastTriggerPayloadV0> {
        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<P9ActiveCastTriggerPayloadV0> payloadCodec() {
            return P9ActiveCastTriggerType.INSTANCE.payloadCodec();
        }

        @Override
        public TriggerCapabilities capabilities() {
            return P9ActiveCastTriggerType.INSTANCE.capabilities();
        }

        @Override
        public com.yo1no.gramarye.magic.validation.ValidationResult validate(
                P9ActiveCastTriggerPayloadV0 payload, ValidationContext context) {
            return P9ActiveCastTriggerType.INSTANCE.validate(payload, context);
        }
    }

    private static final class AlternateActionType implements ActionType<P9DamageActionPayloadV0> {
        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<P9DamageActionPayloadV0> payloadCodec() {
            return P9DamageActionType.INSTANCE.payloadCodec();
        }

        @Override
        public ActionCapabilities capabilities() {
            return P9DamageActionType.INSTANCE.capabilities();
        }

        @Override
        public com.yo1no.gramarye.magic.validation.ValidationResult validate(
                P9DamageActionPayloadV0 payload, ValidationContext context) {
            return P9DamageActionType.INSTANCE.validate(payload, context);
        }
    }
}
