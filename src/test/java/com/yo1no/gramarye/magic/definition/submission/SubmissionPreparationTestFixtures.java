package com.yo1no.gramarye.magic.definition.submission;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationPlan;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalysis;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;

final class SubmissionPreparationTestFixtures {
    static final ResourceLocation TRIGGER_ID = id("submission_trigger");
    static final ResourceLocation ACTION_ID = id("submission_action");
    static final SkillId SKILL_ID = skillId("a6f1634c-b9de-40af-9db8-0782a5ed86d6");
    static final SkillId OTHER_SKILL_ID = skillId("6052bd0a-36b5-490a-9858-6cdaeb8a0d44");
    static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("e5af988e-bc24-443d-ae33-7abf63613c01"));
    static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);

    private SubmissionPreparationTestFixtures() {
    }

    record TriggerData(int value) implements TriggerPayload {
        static final MapCodec<TriggerData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.fieldOf("value").forGetter(TriggerData::value))
                .apply(instance, TriggerData::new));
    }

    record ActionData(int value) implements ActionPayload {
        static final MapCodec<ActionData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.fieldOf("value").forGetter(ActionData::value))
                .apply(instance, ActionData::new));
    }

    static final class TriggerDescriptor implements TriggerType<TriggerData> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(id("submission_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));
        private static final TriggerReferenceProjection PROJECTION =
                new TriggerReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, false, List.of());

        private final int currentVersion;
        private final PayloadMigrationPlan migrationPlan;

        TriggerDescriptor(int currentVersion, PayloadMigrationPlan migrationPlan) {
            this.currentVersion = currentVersion;
            this.migrationPlan = Objects.requireNonNull(migrationPlan, "migrationPlan");
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return currentVersion;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return migrationPlan;
        }

        @Override
        public Optional<TriggerPayloadInspector<TriggerData>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<TriggerData> payloadCodec() {
            return TriggerData.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TriggerData payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    static final class ActionDescriptor implements ActionType<ActionData> {
        private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());
        private static final ActionReferenceProjection PROJECTION =
                new ActionReferenceProjection(
                        SourceSelection.NONE, TargetSelection.NONE, List.of(), Set.of());

        private final int currentVersion;
        private final PayloadMigrationPlan migrationPlan;

        ActionDescriptor(int currentVersion, PayloadMigrationPlan migrationPlan) {
            this.currentVersion = currentVersion;
            this.migrationPlan = Objects.requireNonNull(migrationPlan, "migrationPlan");
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return currentVersion;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return migrationPlan;
        }

        @Override
        public Optional<ActionPayloadInspector<ActionData>> payloadInspector() {
            return Optional.of(payload -> new PayloadInspectionResult.Success<>(PROJECTION));
        }

        @Override
        public MapCodec<ActionData> payloadCodec() {
            return ActionData.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(ActionData payload, ValidationContext context) {
            return ValidationResult.valid();
        }
    }

    static final class PipelineComponents {
        final DraftFormalizer formalizer = new DraftFormalizer();
        final SkillCandidateResolver resolver;
        final SkillValidationAnalyzer analyzer = new SkillValidationAnalyzer(
                new NodeProjectionResolver(), ProfileAvailabilityView.unknown());
        final SkillDefinitionProjector projector = new SkillDefinitionProjector();

        PipelineComponents(TriggerTypeLookup triggerLookup, ActionTypeLookup actionLookup) {
            resolver = new SkillCandidateResolver(triggerLookup, actionLookup);
        }

        SkillSubmissionPreparer productionPreparer() {
            return new SkillSubmissionPreparer(formalizer, resolver, analyzer, projector);
        }

        CountingStages countingStages() {
            return new CountingStages(this);
        }
    }

    static final class CountingStages implements SkillSubmissionPreparer.Stages {
        private final PipelineComponents components;
        private final AtomicInteger formalizeCalls = new AtomicInteger();
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private final AtomicInteger analyzeCalls = new AtomicInteger();
        private final AtomicInteger projectCalls = new AtomicInteger();
        private DraftFormalizationResult lastFormalizationResult;
        private SkillDocument lastDocument;
        private SkillDocumentReadReport lastDocumentReadReport;

        CountingStages(PipelineComponents components) {
            this.components = Objects.requireNonNull(components, "components");
        }

        @Override
        public DraftFormalizationResult formalize(DraftSubmissionPrecheck.Ready ready) {
            formalizeCalls.incrementAndGet();
            lastFormalizationResult = components.formalizer.formalize(ready);
            return lastFormalizationResult;
        }

        @Override
        public ResolvedSkillCandidate resolve(
                SkillDocument document,
                SkillDocumentReadReport report) {
            resolveCalls.incrementAndGet();
            lastDocument = document;
            lastDocumentReadReport = report;
            return components.resolver.resolve(document, report);
        }

        @Override
        public SkillValidationAnalysis analyze(
                ResolvedSkillCandidate candidate,
                ValidationContext context) {
            analyzeCalls.incrementAndGet();
            return components.analyzer.analyze(candidate, context);
        }

        @Override
        public SkillValidationOutcome project(SkillValidationAnalysis analysis) {
            projectCalls.incrementAndGet();
            return components.projector.project(analysis);
        }

        int formalizeCalls() {
            return formalizeCalls.get();
        }

        int resolveCalls() {
            return resolveCalls.get();
        }

        int analyzeCalls() {
            return analyzeCalls.get();
        }

        int projectCalls() {
            return projectCalls.get();
        }

        DraftFormalizationResult lastFormalizationResult() {
            return lastFormalizationResult;
        }

        SkillDocument lastDocument() {
            return lastDocument;
        }

        SkillDocumentReadReport lastDocumentReadReport() {
            return lastDocumentReadReport;
        }
    }

    static PipelineComponents validPipeline() {
        return pipelineWithVersions(0, 0);
    }

    static PipelineComponents emptyPipeline() {
        return new PipelineComponents(new TriggerLookup(Map.of()), new ActionLookup(Map.of()));
    }

    static PipelineComponents pipelineWithVersions(int triggerVersion, int actionVersion) {
        var trigger = new TriggerDescriptor(triggerVersion, PayloadMigrationPlan.empty());
        var action = new ActionDescriptor(actionVersion, PayloadMigrationPlan.empty());
        return new PipelineComponents(
                new TriggerLookup(Map.of(TRIGGER_ID, trigger)),
                new ActionLookup(Map.of(ACTION_ID, action)));
    }

    static SkillDraft draft(
            SkillId skillId,
            Optional<SkillRevision> baseRevision,
            List<DraftNode> nodes,
            AppearanceDocument appearance) {
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                skillId,
                baseRevision,
                nodes,
                appearance);
    }

    static SkillDraft completeDraft(SkillId skillId, Optional<SkillRevision> baseRevision) {
        return completeDraft(
                skillId, baseRevision, 1, AppearanceDocument.defaultAppearance());
    }

    static SkillDraft completeDraft(
            SkillId skillId,
            Optional<SkillRevision> baseRevision,
            int nodeCount,
            AppearanceDocument appearance) {
        var nodes = java.util.stream.IntStream.range(0, nodeCount)
                .mapToObj(index -> completeNode(
                        triggerEnvelope(0, index), actionEnvelope(0, index)))
                .toList();
        return draft(skillId, baseRevision, nodes, appearance);
    }

    static SkillDraft completeDraftWithPayloadPadding(
            SkillId skillId,
            Optional<SkillRevision> baseRevision,
            int nodeCount,
            int payloadPaddingBytes) {
        if (nodeCount <= 0 || payloadPaddingBytes <= 0) {
            throw new IllegalArgumentException("padded draft dimensions must be positive");
        }
        var nodes = java.util.stream.IntStream.range(0, nodeCount)
                .mapToObj(index -> completeNode(
                        paddedEnvelope(TRIGGER_ID, index, payloadPaddingBytes),
                        paddedEnvelope(ACTION_ID, index, payloadPaddingBytes)))
                .toList();
        return draft(
                skillId,
                baseRevision,
                nodes,
                AppearanceDocument.defaultAppearance());
    }

    static SkillDraft emptyDraft(SkillId skillId, Optional<SkillRevision> baseRevision) {
        return draft(
                skillId,
                baseRevision,
                List.of(),
                AppearanceDocument.defaultAppearance());
    }

    static DraftNode completeNode(DefinitionEnvelope trigger, DefinitionEnvelope action) {
        return new DraftNode(
                DraftTriggerSlot.present(trigger),
                DraftActionSlot.present(action),
                AppearanceOverrideDocument.none());
    }

    static DefinitionEnvelope triggerEnvelope(int schemaVersion, int value) {
        return envelope(TRIGGER_ID, schemaVersion, value);
    }

    static DefinitionEnvelope actionEnvelope(int schemaVersion, int value) {
        return envelope(ACTION_ID, schemaVersion, value);
    }

    static DefinitionEnvelope malformedEnvelope(ResourceLocation typeId, int schemaVersion) {
        return new DefinitionEnvelope(
                typeId, schemaVersion, new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
    }

    static DefinitionEnvelope envelope(ResourceLocation typeId, int schemaVersion, int value) {
        var payload = new JsonObject();
        payload.addProperty("value", value);
        return new DefinitionEnvelope(
                typeId, schemaVersion, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static DefinitionEnvelope paddedEnvelope(
            ResourceLocation typeId,
            int value,
            int payloadPaddingBytes) {
        var payload = new JsonObject();
        payload.addProperty("value", value);
        payload.addProperty("padding", "x".repeat(payloadPaddingBytes));
        return new DefinitionEnvelope(typeId, 0, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    static SubmissionAuthorityCheck.Passed passedNew(SkillSubmissionInput input) {
        return passed(input, new SkillSubmissionAuthorizationResult.Authorized(
                OWNER, new AuthorizedSkillState.New(input.draft().skillId())));
    }

    static SubmissionAuthorityCheck.Passed passedExisting(
            SkillSubmissionInput input,
            int latestRevision) {
        return passed(input, new SkillSubmissionAuthorizationResult.Authorized(
                OWNER,
                new AuthorizedSkillState.Existing(new com.yo1no.gramarye.magic.definition.document.SkillReference(
                        input.draft().skillId(), new SkillRevision(latestRevision)))));
    }

    static SubmissionAuthorityCheck.Passed passed(
            SkillSubmissionInput input,
            SkillSubmissionAuthorizationResult authorization) {
        var precheck = (DraftSubmissionPrecheck.Ready)
                new DraftSubmissionPrechecker().check(input);
        return (SubmissionAuthorityCheck.Passed)
                new SubmissionAuthorityChecker().check(precheck, authorization);
    }

    private static SkillId skillId(String value) {
        return new SkillId(UUID.fromString(value));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private record TriggerLookup(Map<ResourceLocation, TriggerType<?>> entries)
            implements TriggerTypeLookup {
        private TriggerLookup {
            entries = Map.copyOf(entries);
        }

        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(entries.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            return entries.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }
    }

    private record ActionLookup(Map<ResourceLocation, ActionType<?>> entries)
            implements ActionTypeLookup {
        private ActionLookup {
            entries = Map.copyOf(entries);
        }

        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(entries.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            return entries.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }
    }
}
