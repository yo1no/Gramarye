package com.yo1no.gramarye.magic.definition.validation;

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
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.InspectedSkillCandidate;
import com.yo1no.gramarye.magic.definition.inspection.NodeReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.TriggerReferenceProjection;
import com.yo1no.gramarye.magic.definition.migration.PayloadMigrationFailure;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayloadInspector;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

final class SkillValidationTestFixtures {
    private static final SkillReference SKILL = new SkillReference(
            new SkillId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
            new SkillRevision(0));

    private SkillValidationTestFixtures() {
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
        private final Supplier<Optional<TriggerPayloadInspector<TriggerData>>> inspector;
        private final Supplier<TriggerCapabilities> capabilities;
        private final BiFunction<TriggerData, ValidationContext, ValidationResult> validator;
        private final AtomicInteger inspectorAccessorCalls = new AtomicInteger();
        private final AtomicInteger capabilityCalls = new AtomicInteger();
        private final AtomicInteger validatorCalls = new AtomicInteger();
        private final AtomicInteger codecCalls = new AtomicInteger();

        TriggerDescriptor(
                Supplier<Optional<TriggerPayloadInspector<TriggerData>>> inspector,
                Supplier<TriggerCapabilities> capabilities,
                BiFunction<TriggerData, ValidationContext, ValidationResult> validator) {
            this.inspector = Objects.requireNonNull(inspector, "inspector");
            this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
            this.validator = Objects.requireNonNull(validator, "validator");
        }

        static TriggerDescriptor successful(TriggerReferenceProjection projection) {
            return new TriggerDescriptor(
                    () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(projection)),
                    SkillValidationTestFixtures::defaultTriggerCapabilities,
                    (payload, context) -> ValidationResult.valid());
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public Optional<TriggerPayloadInspector<TriggerData>> payloadInspector() {
            inspectorAccessorCalls.incrementAndGet();
            return inspector.get();
        }

        @Override
        public MapCodec<TriggerData> payloadCodec() {
            codecCalls.incrementAndGet();
            return TriggerData.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            capabilityCalls.incrementAndGet();
            return capabilities.get();
        }

        @Override
        public ValidationResult validate(TriggerData payload, ValidationContext context) {
            validatorCalls.incrementAndGet();
            return validator.apply(payload, context);
        }

        int inspectorAccessorCalls() {
            return inspectorAccessorCalls.get();
        }

        int capabilityCalls() {
            return capabilityCalls.get();
        }

        int validatorCalls() {
            return validatorCalls.get();
        }

        int codecCalls() {
            return codecCalls.get();
        }
    }

    static final class ActionDescriptor implements ActionType<ActionData> {
        private final Supplier<Optional<ActionPayloadInspector<ActionData>>> inspector;
        private final Supplier<ActionCapabilities> capabilities;
        private final BiFunction<ActionData, ValidationContext, ValidationResult> validator;
        private final AtomicInteger inspectorAccessorCalls = new AtomicInteger();
        private final AtomicInteger capabilityCalls = new AtomicInteger();
        private final AtomicInteger validatorCalls = new AtomicInteger();
        private final AtomicInteger codecCalls = new AtomicInteger();

        ActionDescriptor(
                Supplier<Optional<ActionPayloadInspector<ActionData>>> inspector,
                Supplier<ActionCapabilities> capabilities,
                BiFunction<ActionData, ValidationContext, ValidationResult> validator) {
            this.inspector = Objects.requireNonNull(inspector, "inspector");
            this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
            this.validator = Objects.requireNonNull(validator, "validator");
        }

        static ActionDescriptor successful(ActionReferenceProjection projection) {
            return new ActionDescriptor(
                    () -> Optional.of(payload -> new PayloadInspectionResult.Success<>(projection)),
                    SkillValidationTestFixtures::defaultActionCapabilities,
                    (payload, context) -> ValidationResult.valid());
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public Optional<ActionPayloadInspector<ActionData>> payloadInspector() {
            inspectorAccessorCalls.incrementAndGet();
            return inspector.get();
        }

        @Override
        public MapCodec<ActionData> payloadCodec() {
            codecCalls.incrementAndGet();
            return ActionData.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            capabilityCalls.incrementAndGet();
            return capabilities.get();
        }

        @Override
        public ValidationResult validate(ActionData payload, ValidationContext context) {
            validatorCalls.incrementAndGet();
            return validator.apply(payload, context);
        }

        int inspectorAccessorCalls() {
            return inspectorAccessorCalls.get();
        }

        int capabilityCalls() {
            return capabilityCalls.get();
        }

        int validatorCalls() {
            return validatorCalls.get();
        }

        int codecCalls() {
            return codecCalls.get();
        }
    }

    static ResolvedSkillCandidate candidate(
            int schemaVersion,
            AppearanceDocument appearance,
            SkillDocumentReadReport readReport,
            PipelineFactReport pipelineFacts,
            ResolvedNodeCandidate... nodes) {
        return new ResolvedSkillCandidate(
                schemaVersion,
                SKILL,
                List.of(nodes),
                appearance,
                readReport,
                pipelineFacts);
    }

    static ResolvedSkillCandidate candidate(ResolvedNodeCandidate... nodes) {
        return candidate(
                0,
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false),
                nodes);
    }

    static ResolvedNodeCandidate node(
            int index,
            TriggerResolution trigger,
            ActionResolution action) {
        return node(index, trigger, action, AppearanceOverrideDocument.none());
    }

    static ResolvedNodeCandidate node(
            int index,
            TriggerResolution trigger,
            ActionResolution action,
            AppearanceOverrideDocument appearance) {
        return new ResolvedNodeCandidate(index, trigger, action, appearance);
    }

    static NodeReferenceProjection inspectedNode(
            int index,
            TriggerInspectionState trigger,
            ActionInspectionState action) {
        return new NodeReferenceProjection(index, trigger, action);
    }

    static InspectedSkillCandidate inspection(
            ResolvedSkillCandidate candidate,
            NodeReferenceProjection... nodes) {
        return new InspectedSkillCandidate(candidate, List.of(nodes));
    }

    static SkillValidationAnalysis analysis(
            ResolvedSkillCandidate candidate,
            ValidationResult report,
            NodeReferenceProjection... nodes) {
        return new SkillValidationAnalysis(
                candidate,
                Optional.of(inspection(candidate, nodes)),
                report);
    }

    static TriggerResolution resolvedTrigger(TriggerDescriptor descriptor) {
        var source = envelope("trigger");
        return new TriggerResolution.Resolved<>(
                source, new ResolvedTriggerDefinition<>(descriptor, 0, new TriggerData(0)));
    }

    static ActionResolution resolvedAction(ActionDescriptor descriptor) {
        var source = envelope("action");
        return new ActionResolution.Resolved<>(
                source, new ResolvedActionDefinition<>(descriptor, 0, new ActionData(0)));
    }

    static TriggerResolution unknownTrigger() {
        return new TriggerResolution.Unknown(new UnknownTriggerDefinition(
                envelope("unknown_trigger"),
                DefinitionFailure.of(DefinitionFailure.Code.UNKNOWN_TYPE, "not retained")));
    }

    static ActionResolution unknownAction() {
        return new ActionResolution.Unknown(new UnknownActionDefinition(
                envelope("unknown_action"),
                DefinitionFailure.of(DefinitionFailure.Code.UNKNOWN_TYPE, "not retained")));
    }

    static TriggerResolution migrationFailedTrigger(
            TriggerDescriptor descriptor,
            PayloadMigrationFailure.Code code) {
        return new TriggerResolution.MigrationFailed(
                envelope("migration_trigger"), descriptor, migrationFailure(code));
    }

    static ActionResolution migrationFailedAction(
            ActionDescriptor descriptor,
            PayloadMigrationFailure.Code code) {
        return new ActionResolution.MigrationFailed(
                envelope("migration_action"), descriptor, migrationFailure(code));
    }

    static TriggerResolution decodeFailedTrigger(
            TriggerDescriptor descriptor,
            DefinitionFailure.Code code) {
        return new TriggerResolution.DecodeFailed(
                envelope("decode_trigger"), descriptor, DefinitionFailure.of(code, "not retained"));
    }

    static ActionResolution decodeFailedAction(
            ActionDescriptor descriptor,
            DefinitionFailure.Code code) {
        return new ActionResolution.DecodeFailed(
                envelope("decode_action"), descriptor, DefinitionFailure.of(code, "not retained"));
    }

    static TriggerReferenceProjection triggerProjection(
            SourceSelection source,
            TargetSelection target,
            boolean providesCurrentTarget,
            com.yo1no.gramarye.magic.definition.inspection.NodeReference... references) {
        return new TriggerReferenceProjection(
                source, target, providesCurrentTarget, List.of(references));
    }

    static ActionReferenceProjection actionProjection(
            SourceSelection source,
            TargetSelection target,
            Set<com.yo1no.gramarye.magic.capability.ActionOutputKind> outputs,
            com.yo1no.gramarye.magic.definition.inspection.NodeReference... references) {
        return new ActionReferenceProjection(source, target, List.of(references), outputs);
    }

    static TriggerCapabilities triggerCapabilities(
            SourceRequirement source,
            TargetRequirement target,
            boolean continuation) {
        return new TriggerCapabilities(
                source,
                target,
                continuation,
                Set.of(new TriggerEventKind(id("test_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));
    }

    static ActionCapabilities actionCapabilities(
            SourceRequirement source,
            TargetRequirement target,
            boolean allowsSelf,
            Set<com.yo1no.gramarye.magic.capability.ActionOutputKind> outputs,
            boolean split,
            boolean chain,
            boolean repeat) {
        return new ActionCapabilities(
                source,
                target,
                allowsSelf,
                outputs,
                split,
                chain,
                repeat,
                false,
                false,
                false,
                false,
                ControlClass.NONE,
                AppearanceParameterPolicy.none());
    }

    static TriggerCapabilities defaultTriggerCapabilities() {
        return triggerCapabilities(SourceRequirement.NONE, TargetRequirement.OPTIONAL, false);
    }

    static ActionCapabilities defaultActionCapabilities() {
        return actionCapabilities(
                SourceRequirement.NONE, TargetRequirement.OPTIONAL, true, Set.of(), false, false, false);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private static DefinitionEnvelope envelope(String path) {
        var payload = new JsonObject();
        payload.addProperty("value", 0);
        return new DefinitionEnvelope(id(path), 0, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static PayloadMigrationFailure migrationFailure(PayloadMigrationFailure.Code code) {
        return new PayloadMigrationFailure(
                code,
                OptionalInt.of(0),
                OptionalInt.of(1),
                OptionalInt.empty(),
                OptionalInt.of(0),
                Optional.empty());
    }
}
