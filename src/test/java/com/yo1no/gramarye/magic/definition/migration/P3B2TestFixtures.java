package com.yo1no.gramarye.magic.definition.migration;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
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
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;

final class P3B2TestFixtures {
    static final ResourceLocation TRIGGER_ID = id("test_trigger");
    static final ResourceLocation ACTION_ID = id("test_action");
    static final ResourceLocation UNKNOWN_TRIGGER_ID = id("unknown_trigger");
    static final ResourceLocation UNKNOWN_ACTION_ID = id("unknown_action");
    static final SkillDocumentReadReport EMPTY_READ_REPORT =
            new SkillDocumentReadReport(List.of(), false);

    private P3B2TestFixtures() {
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
                Set.of(new TriggerEventKind(id("test_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        private final int currentVersion;
        private final PayloadMigrationPlan plan;
        private final MapCodec<TriggerData> codec;
        private final AtomicInteger validationCalls = new AtomicInteger();

        TriggerDescriptor(int currentVersion, PayloadMigrationPlan plan) {
            this(currentVersion, plan, TriggerData.CODEC);
        }

        TriggerDescriptor(
                int currentVersion,
                PayloadMigrationPlan plan,
                MapCodec<TriggerData> codec) {
            this.currentVersion = currentVersion;
            this.plan = plan;
            this.codec = codec;
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return currentVersion;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return plan;
        }

        @Override
        public MapCodec<TriggerData> payloadCodec() {
            return codec;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TriggerData payload, ValidationContext context) {
            validationCalls.incrementAndGet();
            return ValidationResult.valid();
        }

        int validationCalls() {
            return validationCalls.get();
        }
    }

    static final class ActionDescriptor implements ActionType<ActionData> {
        private static final ActionCapabilities CAPABILITIES = new ActionCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.REQUIRED,
                true,
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

        private final int currentVersion;
        private final PayloadMigrationPlan plan;
        private final MapCodec<ActionData> codec;
        private final AtomicInteger validationCalls = new AtomicInteger();

        ActionDescriptor(int currentVersion, PayloadMigrationPlan plan) {
            this(currentVersion, plan, ActionData.CODEC);
        }

        ActionDescriptor(
                int currentVersion,
                PayloadMigrationPlan plan,
                MapCodec<ActionData> codec) {
            this.currentVersion = currentVersion;
            this.plan = plan;
            this.codec = codec;
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return currentVersion;
        }

        @Override
        public PayloadMigrationPlan payloadMigrationPlan() {
            return plan;
        }

        @Override
        public MapCodec<ActionData> payloadCodec() {
            return codec;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(ActionData payload, ValidationContext context) {
            validationCalls.incrementAndGet();
            return ValidationResult.valid();
        }

        int validationCalls() {
            return validationCalls.get();
        }
    }

    static final class CountingTriggerLookup implements TriggerTypeLookup {
        private final Map<ResourceLocation, TriggerType<?>> descriptors;
        private final AtomicInteger findCalls = new AtomicInteger();

        CountingTriggerLookup() {
            this.descriptors = Map.of();
        }

        CountingTriggerLookup(ResourceLocation id, TriggerType<?> descriptor) {
            this.descriptors = Map.of(id, descriptor);
        }

        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            findCalls.incrementAndGet();
            return Optional.ofNullable(descriptors.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            return descriptors.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        int findCalls() {
            return findCalls.get();
        }
    }

    static final class CountingActionLookup implements ActionTypeLookup {
        private final Map<ResourceLocation, ActionType<?>> descriptors;
        private final AtomicInteger findCalls = new AtomicInteger();

        CountingActionLookup() {
            this.descriptors = Map.of();
        }

        CountingActionLookup(ResourceLocation id, ActionType<?> descriptor) {
            this.descriptors = Map.of(id, descriptor);
        }

        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            findCalls.incrementAndGet();
            return Optional.ofNullable(descriptors.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            return descriptors.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        int findCalls() {
            return findCalls.get();
        }
    }

    static DefinitionEnvelope triggerEnvelope(int schemaVersion, int value) {
        return envelope(TRIGGER_ID, schemaVersion, value);
    }

    static DefinitionEnvelope actionEnvelope(int schemaVersion, int value) {
        return envelope(ACTION_ID, schemaVersion, value);
    }

    static DefinitionEnvelope envelope(
            ResourceLocation typeId,
            int schemaVersion,
            int value) {
        var payload = new JsonObject();
        payload.addProperty("value", value);
        return new DefinitionEnvelope(
                typeId,
                schemaVersion,
                new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    static SkillDocument document(DefinitionEnvelope trigger, DefinitionEnvelope action) {
        return new SkillDocument(
                0,
                new SkillId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
                new SkillRevision(0),
                List.of(new NodeDocument(
                        trigger,
                        action,
                        AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }
}
