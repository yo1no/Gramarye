package com.yo1no.gramarye.magic.type;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TypeDescriptorTest {
    private static final ValidationContext CONTEXT = new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void payloadMarkersAndDescriptorBoundsRemainIsolated() {
        assertAll(
                () -> assertFalse(TriggerPayload.class.isAssignableFrom(TestActionPayload.class)),
                () -> assertFalse(ActionPayload.class.isAssignableFrom(TestTriggerPayload.class)),
                () -> assertEquals(
                        TriggerPayload.class,
                        TriggerType.class.getTypeParameters()[0].getBounds()[0]),
                () -> assertEquals(
                        ActionPayload.class,
                        ActionType.class.getTypeParameters()[0].getBounds()[0]));
    }

    @Test
    void descriptorsDoNotDuplicateRegistryTypeIds() {
        assertAll(
                () -> assertFalse(Arrays.stream(TriggerType.class.getMethods())
                        .anyMatch(method -> method.getName().equals("id"))),
                () -> assertFalse(Arrays.stream(ActionType.class.getMethods())
                        .anyMatch(method -> method.getName().equals("id"))));
    }

    @Test
    void descriptorCodecsRoundTripTheirOwnPayloadTypes() {
        assertAll(
                () -> assertCodecRoundTrip(
                        TestTriggerDescriptor.INSTANCE.payloadCodec(),
                        new TestTriggerPayload(7)),
                () -> assertCodecRoundTrip(
                        TestActionDescriptor.INSTANCE.payloadCodec(),
                        new TestActionPayload(11)));
    }

    @Test
    void semanticValidatorsReturnWarningsAndErrorsWithoutThrowingForUserData() {
        var warning = assertDoesNotThrow(() -> TestTriggerDescriptor.INSTANCE.validate(
                new TestTriggerPayload(0),
                CONTEXT));
        var error = assertDoesNotThrow(() -> TestActionDescriptor.INSTANCE.validate(
                new TestActionPayload(-1),
                CONTEXT));

        assertAll(
                () -> assertTrue(warning.isValid()),
                () -> assertEquals(1, warning.warnings().size()),
                () -> assertFalse(error.isValid()),
                () -> assertEquals(1, error.errors().size()));
    }

    private static <P> void assertCodecRoundTrip(MapCodec<P> codec, P expected) {
        var encoded = codec.codec().encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        var decoded = codec.codec().parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertEquals(expected, decoded);
    }

    private record TestTriggerPayload(int threshold) implements TriggerPayload {
        private static final MapCodec<TestTriggerPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("threshold").forGetter(TestTriggerPayload::threshold))
                .apply(instance, TestTriggerPayload::new));
    }

    private record TestActionPayload(int strength) implements ActionPayload {
        private static final MapCodec<TestActionPayload> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("strength").forGetter(TestActionPayload::strength))
                .apply(instance, TestActionPayload::new));
    }

    private enum TestTriggerDescriptor implements TriggerType<TestTriggerPayload> {
        INSTANCE;

        private static final TriggerCapabilities CAPABILITIES = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.NONE,
                false,
                Set.of(new TriggerEventKind(ResourceLocation.fromNamespaceAndPath("gramarye", "test_event"))),
                Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                Set.of(TriggerGranularity.PER_EVENT));

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<TestTriggerPayload> payloadCodec() {
            return TestTriggerPayload.CODEC;
        }

        @Override
        public TriggerCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TestTriggerPayload payload, ValidationContext context) {
            if (payload.threshold() == 0) {
                return ValidationResult.of(new ValidationIssue(
                        "test.trigger.zero_threshold",
                        ValidationSeverity.WARNING,
                        "$.threshold",
                        "A zero threshold is accepted with a warning"));
            }
            if (payload.threshold() < 0) {
                return ValidationResult.of(new ValidationIssue(
                        "test.trigger.negative_threshold",
                        ValidationSeverity.ERROR,
                        "$.threshold",
                        "Threshold must not be negative"));
            }
            return ValidationResult.valid();
        }
    }

    private enum TestActionDescriptor implements ActionType<TestActionPayload> {
        INSTANCE;

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

        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<TestActionPayload> payloadCodec() {
            return TestActionPayload.CODEC;
        }

        @Override
        public ActionCapabilities capabilities() {
            return CAPABILITIES;
        }

        @Override
        public ValidationResult validate(TestActionPayload payload, ValidationContext context) {
            if (payload.strength() < 0) {
                return ValidationResult.of(new ValidationIssue(
                        "test.action.negative_strength",
                        ValidationSeverity.ERROR,
                        "$.strength",
                        "Strength must not be negative"));
            }
            return ValidationResult.valid();
        }
    }
}
