package com.yo1no.gramarye.magic.definition;

import static com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.ACTION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.FakeActionTypeLookup;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.TestActionDescriptor;
import com.yo1no.gramarye.magic.definition.DefinitionTestFixtures.TestActionPayload;
import com.yo1no.gramarye.magic.definition.action.ActionDefinition;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.codec.ActionDefinitionCodec;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ActionDefinitionCodecTest {
    private static final int SCHEMA_VERSION = 5;
    private static final ValidationContext VALIDATION_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void unknownTypeProducesUnknownAndReencodesOriginalEnvelope() {
        var envelope = envelope(
                ResourceLocation.fromNamespaceAndPath("missing", "action"),
                SCHEMA_VERSION,
                json("{\"nested\": {\"kept\": true}}"));
        var codec = ActionDefinitionCodec.create(new FakeActionTypeLookup());

        var unknown = requireUnknown(decode(codec, envelope));
        assertEquals(DefinitionFailure.Code.UNKNOWN_TYPE, unknown.failure().code());
        assertEquals(envelope, unknown.envelope());
        assertEquals(encodeEnvelope(envelope), codec.encodeStart(JsonOps.INSTANCE, unknown).getOrThrow());
    }

    @Test
    void unsupportedSchemaPreservesEnvelopeWithoutTryingPayloadCodec() {
        var descriptor = descriptor(TestActionPayload.CODEC);
        var envelope = envelope(ACTION_ID, SCHEMA_VERSION + 1, json("{\"strength\": 7}"));

        var unknown = requireUnknown(decode(
                ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, descriptor)),
                envelope));

        assertEquals(DefinitionFailure.Code.UNSUPPORTED_SCHEMA_VERSION, unknown.failure().code());
        assertEquals(envelope, unknown.envelope());
    }

    @Test
    void payloadErrorAndPartialResultRemainUnknown() {
        var descriptor = descriptor(TestActionPayload.CODEC);
        var malformed = envelope(ACTION_ID, SCHEMA_VERSION, json("{\"strength\": \"bad\"}"));
        var malformedUnknown = requireUnknown(decode(
                ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, descriptor)),
                malformed));

        var partialCodec = TestActionPayload.CODEC.flatXmap(
                payload -> DataResult.error(() -> "partial action payload", payload),
                payload -> DataResult.success(payload));
        var partialDescriptor = descriptor(partialCodec);
        var partialEnvelope = envelope(ACTION_ID, SCHEMA_VERSION, json("{\"strength\": 7}"));
        var partialUnknown = requireUnknown(decode(
                ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, partialDescriptor)),
                partialEnvelope));

        assertEquals(DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, malformedUnknown.failure().code());
        assertEquals(malformed, malformedUnknown.envelope());
        assertEquals(DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, partialUnknown.failure().code());
        assertEquals(partialEnvelope, partialUnknown.envelope());
    }

    @Test
    void codecRuntimeExceptionIsIsolatedAndDiagnosticIsBounded() {
        var runtimeCodec = TestActionPayload.CODEC.<TestActionPayload>xmap(payload -> {
            throw new IllegalStateException("x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH + 100));
        }, payload -> payload);
        var descriptor = descriptor(runtimeCodec);
        var envelope = envelope(ACTION_ID, SCHEMA_VERSION, json("{\"strength\": 7}"));

        var unknown = requireUnknown(decode(
                ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, descriptor)),
                envelope));

        assertEquals(DefinitionFailure.Code.CODEC_EXCEPTION, unknown.failure().code());
        assertEquals(MagicSafetyCeilings.MAX_STRING_LENGTH, unknown.failure().diagnostic().length());
        assertEquals(envelope, unknown.envelope());
    }

    @Test
    void knownTypeResolvesConcretePayloadWithoutImplicitSemanticValidation() {
        var descriptor = descriptor(TestActionPayload.CODEC);
        var codec = ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, descriptor));

        var resolved = requireResolved(decode(
                codec,
                envelope(ACTION_ID, SCHEMA_VERSION, json("{\"strength\": 7}"))));

        assertEquals(descriptor, resolved.descriptor());
        assertEquals(SCHEMA_VERSION, resolved.schemaVersion());
        assertEquals(new TestActionPayload(7), resolved.payload());
        assertEquals(0, descriptor.validationCalls());
        resolved.validate(VALIDATION_CONTEXT);
        assertEquals(1, descriptor.validationCalls());
    }

    @Test
    void resolvedEncodingUsesLookupKeyAndDescriptorCurrentSchema() {
        var descriptor = descriptor(TestActionPayload.CODEC);
        var codec = ActionDefinitionCodec.create(new FakeActionTypeLookup(ACTION_ID, descriptor));
        var resolved = new ResolvedActionDefinition<>(
                descriptor,
                SCHEMA_VERSION,
                new TestActionPayload(9));

        var encoded = codec.encodeStart(JsonOps.INSTANCE, resolved).getOrThrow();
        var envelope = DefinitionEnvelope.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(ACTION_ID, envelope.typeId());
        assertEquals(SCHEMA_VERSION, envelope.schemaVersion());
        assertEquals(json("{\"strength\": 9}"), envelope.copyRawPayload().getValue());
    }

    @Test
    void resolvedEncodingFailsForUnregisteredDescriptorOrPayloadEncodeError() {
        var descriptor = descriptor(TestActionPayload.CODEC);
        var unregistered = new ResolvedActionDefinition<>(
                descriptor,
                SCHEMA_VERSION,
                new TestActionPayload(9));
        var unregisteredResult = ActionDefinitionCodec.create(new FakeActionTypeLookup())
                .encodeStart(JsonOps.INSTANCE, unregistered);

        var encodeErrorCodec = TestActionPayload.CODEC.flatXmap(
                payload -> DataResult.success(payload),
                payload -> DataResult.error(() -> "action encode rejected"));
        var encodeErrorDescriptor = descriptor(encodeErrorCodec);
        var encodeError = new ResolvedActionDefinition<>(
                encodeErrorDescriptor,
                SCHEMA_VERSION,
                new TestActionPayload(9));
        var encodeErrorResult = ActionDefinitionCodec.create(
                        new FakeActionTypeLookup(ACTION_ID, encodeErrorDescriptor))
                .encodeStart(JsonOps.INSTANCE, encodeError);

        assertTrue(unregisteredResult.error().isPresent());
        assertTrue(encodeErrorResult.error().isPresent());
        assertTrue(unregisteredResult.error().orElseThrow().message().contains("not registered"));
        assertTrue(encodeErrorResult.error().orElseThrow().message().contains("action encode rejected"));
    }

    private static TestActionDescriptor descriptor(
            com.mojang.serialization.MapCodec<TestActionPayload> codec) {
        return new TestActionDescriptor(SCHEMA_VERSION, codec);
    }

    private static ActionDefinition decode(
            com.mojang.serialization.Codec<ActionDefinition> codec,
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

    private static UnknownActionDefinition requireUnknown(ActionDefinition definition) {
        if (definition instanceof UnknownActionDefinition unknown) {
            return unknown;
        }
        return fail("Expected UnknownActionDefinition but got " + definition.getClass().getSimpleName());
    }

    private static ResolvedActionDefinition<?> requireResolved(ActionDefinition definition) {
        if (definition instanceof ResolvedActionDefinition<?> resolved) {
            return resolved;
        }
        return fail("Expected ResolvedActionDefinition but got " + definition.getClass().getSimpleName());
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
