package com.yo1no.gramarye.magic.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.capability.TriggerGranularity;
import com.yo1no.gramarye.magic.capability.TriggerSourceScope;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;

final class DefinitionTestFixtures {
    static final ResourceLocation TRIGGER_ID = ResourceLocation.fromNamespaceAndPath("gramarye", "test_trigger");
    static final ResourceLocation ACTION_ID = ResourceLocation.fromNamespaceAndPath("gramarye", "test_action");

    private DefinitionTestFixtures() {
    }

    record TestTriggerPayload(int threshold) implements TriggerPayload {
        static final MapCodec<TestTriggerPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("threshold").forGetter(TestTriggerPayload::threshold))
                .apply(instance, TestTriggerPayload::new));
    }

    record TestActionPayload(int strength) implements ActionPayload {
        static final MapCodec<TestActionPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("strength").forGetter(TestActionPayload::strength))
                .apply(instance, TestActionPayload::new));
    }

    static final class TestTriggerDescriptor implements TriggerType<TestTriggerPayload> {
        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ResourceLocation.fromNamespaceAndPath("gramarye", "test_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        private final int schemaVersion;
        private final MapCodec<TestTriggerPayload> codec;
        private final AtomicInteger validationCalls = new AtomicInteger();

        TestTriggerDescriptor(int schemaVersion, MapCodec<TestTriggerPayload> codec) {
            this.schemaVersion = schemaVersion;
            this.codec = codec;
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return schemaVersion;
        }

        @Override
        public MapCodec<TestTriggerPayload> payloadCodec() {
            return codec;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TestTriggerPayload payload, ValidationContext context) {
            validationCalls.incrementAndGet();
            return ValidationResult.valid();
        }

        int validationCalls() {
            return validationCalls.get();
        }
    }

    static final class TestActionDescriptor implements ActionType<TestActionPayload> {
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

        private final int schemaVersion;
        private final MapCodec<TestActionPayload> codec;
        private final AtomicInteger validationCalls = new AtomicInteger();

        TestActionDescriptor(int schemaVersion, MapCodec<TestActionPayload> codec) {
            this.schemaVersion = schemaVersion;
            this.codec = codec;
        }

        @Override
        public int currentPayloadSchemaVersion() {
            return schemaVersion;
        }

        @Override
        public MapCodec<TestActionPayload> payloadCodec() {
            return codec;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TestActionPayload payload, ValidationContext context) {
            validationCalls.incrementAndGet();
            return ValidationResult.valid();
        }

        int validationCalls() {
            return validationCalls.get();
        }
    }

    static final class FakeTriggerTypeLookup implements TriggerTypeLookup {
        private final Map<ResourceLocation, TriggerType<?>> descriptors;

        FakeTriggerTypeLookup() {
            descriptors = Map.of();
        }

        FakeTriggerTypeLookup(ResourceLocation id, TriggerType<?> descriptor) {
            descriptors = Map.of(id, descriptor);
        }

        @Override
        public Optional<TriggerType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(descriptors.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(TriggerType<?> descriptor) {
            return descriptors.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }
    }

    static final class FakeActionTypeLookup implements ActionTypeLookup {
        private final Map<ResourceLocation, ActionType<?>> descriptors;

        FakeActionTypeLookup() {
            descriptors = Map.of();
        }

        FakeActionTypeLookup(ResourceLocation id, ActionType<?> descriptor) {
            descriptors = Map.of(id, descriptor);
        }

        @Override
        public Optional<ActionType<?>> find(ResourceLocation typeId) {
            return Optional.ofNullable(descriptors.get(typeId));
        }

        @Override
        public Optional<ResourceLocation> keyOf(ActionType<?> descriptor) {
            return descriptors.entrySet().stream()
                    .filter(entry -> entry.getValue() == descriptor)
                    .map(Map.Entry::getKey)
                    .findFirst();
        }
    }
}
