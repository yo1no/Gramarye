package com.yo1no.gramarye.magic.definition;

import static com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.TRIGGER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.FakeTriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.TestTriggerDescriptor;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.TestTriggerPayload;
import com.yo1no.gramarye.magic.definition.codec.TriggerDefinitionCodec;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.TriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TriggerDefinitionCodecTest {
    private static final int SCHEMA_VERSION = 3;
    private static final ValidationContext VALIDATION_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void unknownTypeProducesUnknownAndReencodesOriginalEnvelope() {
        var envelope = envelope(
                ResourceLocation.fromNamespaceAndPath("missing", "trigger"),
                SCHEMA_VERSION,
                json("{\"nested\": {\"kept\": true}}"));
        var codec = TriggerDefinitionCodec.create(new FakeTriggerTypeLookup());

        var unknown = requireUnknown(decode(codec, envelope));
        assertEquals(DefinitionFailure.Code.UNKNOWN_TYPE, unknown.failure().code());
        assertEquals(envelope, unknown.envelope());
        assertEquals(encodeEnvelope(envelope), codec.encodeStart(JsonOps.INSTANCE, unknown).getOrThrow());
    }

    @Test
    void unsupportedSchemaPreservesEnvelopeWithoutTryingPayloadCodec() {
        var descriptor = descriptor(TestTriggerPayload.CODEC);
        var envelope = envelope(TRIGGER_ID, SCHEMA_VERSION + 1, json("{\"threshold\": 7}"));

        var unknown = requireUnknown(decode(
                TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, descriptor)),
                envelope));

        assertEquals(DefinitionFailure.Code.UNSUPPORTED_SCHEMA_VERSION, unknown.failure().code());
        assertEquals(envelope, unknown.envelope());
    }

    @Test
    void payloadErrorAndPartialResultRemainUnknown() {
        var descriptor = descriptor(TestTriggerPayload.CODEC);
        var malformed = envelope(TRIGGER_ID, SCHEMA_VERSION, json("{\"threshold\": \"bad\"}"));
        var malformedUnknown = requireUnknown(decode(
                TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, descriptor)),
                malformed));

        var partialCodec = TestTriggerPayload.CODEC.flatXmap(
                payload -> DataResult.error(() -> "partial trigger payload", payload),
                payload -> DataResult.success(payload));
        var partialDescriptor = descriptor(partialCodec);
        var partialEnvelope = envelope(TRIGGER_ID, SCHEMA_VERSION, json("{\"threshold\": 7}"));
        var partialUnknown = requireUnknown(decode(
                TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, partialDescriptor)),
                partialEnvelope));

        assertEquals(DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, malformedUnknown.failure().code());
        assertEquals(malformed, malformedUnknown.envelope());
        assertEquals(DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, partialUnknown.failure().code());
        assertEquals(partialEnvelope, partialUnknown.envelope());
    }

    @Test
    void codecRuntimeExceptionIsIsolatedAndDiagnosticIsBounded() {
        var runtimeCodec = TestTriggerPayload.CODEC.<TestTriggerPayload>xmap(payload -> {
            throw new IllegalStateException("x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH + 100));
        }, payload -> payload);
        var descriptor = descriptor(runtimeCodec);
        var envelope = envelope(TRIGGER_ID, SCHEMA_VERSION, json("{\"threshold\": 7}"));

        var unknown = requireUnknown(decode(
                TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, descriptor)),
                envelope));

        assertEquals(DefinitionFailure.Code.CODEC_EXCEPTION, unknown.failure().code());
        assertEquals(MagicSafetyCeilings.MAX_STRING_LENGTH, unknown.failure().diagnostic().length());
        assertEquals(envelope, unknown.envelope());
    }

    @Test
    void knownTypeResolvesConcretePayloadWithoutImplicitSemanticValidation() {
        var descriptor = descriptor(TestTriggerPayload.CODEC);
        var codec = TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, descriptor));

        var resolved = requireResolved(decode(
                codec,
                envelope(TRIGGER_ID, SCHEMA_VERSION, json("{\"threshold\": 7}"))));

        assertEquals(descriptor, resolved.descriptor());
        assertEquals(SCHEMA_VERSION, resolved.schemaVersion());
        assertEquals(new TestTriggerPayload(7), resolved.payload());
        assertEquals(0, descriptor.validationCalls());
        resolved.validate(VALIDATION_CONTEXT);
        assertEquals(1, descriptor.validationCalls());
    }

    @Test
    void resolvedEncodingUsesLookupKeyAndDescriptorCurrentSchema() {
        var descriptor = descriptor(TestTriggerPayload.CODEC);
        var codec = TriggerDefinitionCodec.create(new FakeTriggerTypeLookup(TRIGGER_ID, descriptor));
        var resolved = new ResolvedTriggerDefinition<>(
                descriptor,
                SCHEMA_VERSION,
                new TestTriggerPayload(9));

        var encoded = codec.encodeStart(JsonOps.INSTANCE, resolved).getOrThrow();
        var envelope = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(TRIGGER_ID, envelope.typeId());
        assertEquals(SCHEMA_VERSION, envelope.schemaVersion());
        assertEquals(json("{\"threshold\": 9}"), envelope.copyRawPayload().getValue());
    }

    @Test
    void resolvedEncodingFailsForUnregisteredDescriptorOrPayloadEncodeError() {
        var descriptor = descriptor(TestTriggerPayload.CODEC);
        var unregistered = new ResolvedTriggerDefinition<>(
                descriptor,
                SCHEMA_VERSION,
                new TestTriggerPayload(9));
        var unregisteredResult = TriggerDefinitionCodec.create(new FakeTriggerTypeLookup())
                .encodeStart(JsonOps.INSTANCE, unregistered);

        var encodeErrorCodec = TestTriggerPayload.CODEC.flatXmap(
                payload -> DataResult.success(payload),
                payload -> DataResult.error(() -> "trigger encode rejected"));
        var encodeErrorDescriptor = descriptor(encodeErrorCodec);
        var encodeError = new ResolvedTriggerDefinition<>(
                encodeErrorDescriptor,
                SCHEMA_VERSION,
                new TestTriggerPayload(9));
        var encodeErrorResult = TriggerDefinitionCodec.create(
                        new FakeTriggerTypeLookup(TRIGGER_ID, encodeErrorDescriptor))
                .encodeStart(JsonOps.INSTANCE, encodeError);

        assertTrue(unregisteredResult.error().isPresent());
        assertTrue(encodeErrorResult.error().isPresent());
        assertTrue(unregisteredResult.error().orElseThrow().message().contains("not registered"));
        assertTrue(encodeErrorResult.error().orElseThrow().message().contains("trigger encode rejected"));
    }

    private static TestTriggerDescriptor descriptor(
            com.mojang.serialization.MapCodec<TestTriggerPayload> codec) {
        return new TestTriggerDescriptor(SCHEMA_VERSION, codec);
    }

    private static TriggerDefinition decode(
            com.mojang.serialization.Codec<TriggerDefinition> codec,
            DefinitionEnvelope envelope) {
        return codec.parse(JsonOps.INSTANCE, encodeEnvelope(envelope)).getOrThrow();
    }

    private static JsonElement encodeEnvelope(DefinitionEnvelope envelope) {
        return DefinitionEnvelope.CODEC.encodeStart(JsonOps.INSTANCE, envelope).getOrThrow();
    }

    private static DefinitionEnvelope envelope(
            ResourceLocation typeId,
            int schemaVersion,
            JsonElement payload) {
        return new DefinitionEnvelope(typeId, schemaVersion, new Dynamic<>(JsonOps.INSTANCE, payload));
    }

    private static UnknownTriggerDefinition requireUnknown(TriggerDefinition definition) {
        if (definition instanceof UnknownTriggerDefinition unknown) {
            return unknown;
        }
        return fail("Expected UnknownTriggerDefinition but got " + definition.getClass().getSimpleName());
    }

    private static ResolvedTriggerDefinition<?> requireResolved(TriggerDefinition definition) {
        if (definition instanceof ResolvedTriggerDefinition<?> resolved) {
            return resolved;
        }
        return fail("Expected ResolvedTriggerDefinition but got " + definition.getClass().getSimpleName());
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
