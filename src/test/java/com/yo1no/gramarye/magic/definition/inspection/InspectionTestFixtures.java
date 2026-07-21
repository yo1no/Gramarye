package com.yo1no.gramarye.magic.definition.inspection;

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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

final class InspectionTestFixtures {
    private static final DefinitionFailure UNKNOWN_FAILURE = DefinitionFailure.of(
            DefinitionFailure.Code.UNKNOWN_TYPE, "unknown");
    private static final DefinitionFailure DECODE_FAILURE = DefinitionFailure.of(
            DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, "decode");

    private InspectionTestFixtures() {
    }

    record TriggerData(int sourceNode) implements TriggerPayload {
        static final MapCodec<TriggerData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.fieldOf("source_node").forGetter(TriggerData::sourceNode))
                .apply(instance, TriggerData::new));
    }

    record ActionData(int sourceNode) implements ActionPayload {
        static final MapCodec<ActionData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.INT.fieldOf("source_node").forGetter(ActionData::sourceNode))
                .apply(instance, ActionData::new));
    }

    static final class TriggerDescriptor implements TriggerType<TriggerData> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(id("inspection_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        private final Supplier<Optional<TriggerPayloadInspector<TriggerData>>> inspectorAccessor;
        private final AtomicInteger payloadCodecCalls = new AtomicInteger();

        TriggerDescriptor(TriggerPayloadInspector<TriggerData> inspector) {
            this(() -> Optional.of(inspectionRequired(inspector)));
        }

        private TriggerDescriptor(
                Supplier<Optional<TriggerPayloadInspector<TriggerData>>> inspectorAccessor) {
            this.inspectorAccessor = inspectionRequired(inspectorAccessor);
        }

        static TriggerDescriptor missingInspector() {
            return new TriggerDescriptor(Optional::empty);
        }

        static TriggerDescriptor withInspectorAccessor(
                Supplier<Optional<TriggerPayloadInspector<TriggerData>>> inspectorAccessor) {
            return new TriggerDescriptor(inspectorAccessor);
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public Optional<TriggerPayloadInspector<TriggerData>> payloadInspector() {
            return inspectorAccessor.get();
        }

        @Override
        public MapCodec<TriggerData> payloadCodec() {
            payloadCodecCalls.incrementAndGet();
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

        int payloadCodecCalls() {
            return payloadCodecCalls.get();
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

        private final Supplier<Optional<ActionPayloadInspector<ActionData>>> inspectorAccessor;
        private final AtomicInteger payloadCodecCalls = new AtomicInteger();

        ActionDescriptor(ActionPayloadInspector<ActionData> inspector) {
            this(() -> Optional.of(inspectionRequired(inspector)));
        }

        private ActionDescriptor(
                Supplier<Optional<ActionPayloadInspector<ActionData>>> inspectorAccessor) {
            this.inspectorAccessor = inspectionRequired(inspectorAccessor);
        }

        static ActionDescriptor missingInspector() {
            return new ActionDescriptor(Optional::empty);
        }

        static ActionDescriptor withInspectorAccessor(
                Supplier<Optional<ActionPayloadInspector<ActionData>>> inspectorAccessor) {
            return new ActionDescriptor(inspectorAccessor);
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public Optional<ActionPayloadInspector<ActionData>> payloadInspector() {
            return inspectorAccessor.get();
        }

        @Override
        public MapCodec<ActionData> payloadCodec() {
            payloadCodecCalls.incrementAndGet();
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

        int payloadCodecCalls() {
            return payloadCodecCalls.get();
        }
    }

    static ResolvedSkillCandidate candidate(ResolvedNodeCandidate... nodes) {
        return new ResolvedSkillCandidate(
                0,
                new SkillReference(
                        new SkillId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
                        new SkillRevision(0)),
                List.of(nodes),
                AppearanceDocument.defaultAppearance(),
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false));
    }

    static ResolvedNodeCandidate node(
            int index,
            TriggerResolution trigger,
            ActionResolution action) {
        return new ResolvedNodeCandidate(
                index,
                trigger,
                action,
                AppearanceOverrideDocument.none());
    }

    static TriggerResolution resolvedTrigger(TriggerDescriptor descriptor, int value) {
        var envelope = envelope("trigger", value);
        return new TriggerResolution.Resolved<>(
                envelope,
                new ResolvedTriggerDefinition<>(descriptor, 0, new TriggerData(value)));
    }

    static ActionResolution resolvedAction(ActionDescriptor descriptor, int value) {
        var envelope = envelope("action", value);
        return new ActionResolution.Resolved<>(
                envelope,
                new ResolvedActionDefinition<>(descriptor, 0, new ActionData(value)));
    }

    static TriggerResolution unknownTrigger() {
        return new TriggerResolution.Unknown(new UnknownTriggerDefinition(
                envelope("unknown_trigger", 0), UNKNOWN_FAILURE));
    }

    static ActionResolution unknownAction() {
        return new ActionResolution.Unknown(new UnknownActionDefinition(
                envelope("unknown_action", 0), UNKNOWN_FAILURE));
    }

    static TriggerResolution migrationFailedTrigger(TriggerDescriptor descriptor) {
        return new TriggerResolution.MigrationFailed(
                envelope("migration_trigger", 0), descriptor, migrationFailure());
    }

    static ActionResolution migrationFailedAction(ActionDescriptor descriptor) {
        return new ActionResolution.MigrationFailed(
                envelope("migration_action", 0), descriptor, migrationFailure());
    }

    static TriggerResolution decodeFailedTrigger(TriggerDescriptor descriptor) {
        return new TriggerResolution.DecodeFailed(
                envelope("decode_trigger", 0), descriptor, DECODE_FAILURE);
    }

    static ActionResolution decodeFailedAction(ActionDescriptor descriptor) {
        return new ActionResolution.DecodeFailed(
                envelope("decode_action", 0), descriptor, DECODE_FAILURE);
    }

    private static PayloadMigrationFailure migrationFailure() {
        return new PayloadMigrationFailure(
                PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                OptionalInt.of(0),
                OptionalInt.of(1),
                OptionalInt.empty(),
                OptionalInt.of(0),
                Optional.empty());
    }

    private static DefinitionEnvelope envelope(String path, int value) {
        var payload = new JsonObject();
        payload.addProperty("source_node", value);
        return new DefinitionEnvelope(id(path), 0, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private static <T> T inspectionRequired(T value) {
        return java.util.Objects.requireNonNull(value, "inspector");
    }
}
