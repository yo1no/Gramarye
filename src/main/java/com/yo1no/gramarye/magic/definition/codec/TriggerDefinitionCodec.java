package com.yo1no.gramarye.magic.definition.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure.Code;
import com.yo1no.gramarye.magic.definition.lookup.TriggerTypeLookup;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.TriggerDefinition;
import com.yo1no.gramarye.magic.definition.trigger.UnknownTriggerDefinition;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.trigger.type.TriggerType;
import java.util.Objects;

/** Registry-aware Trigger definition resolution and canonical typed encoding. */
public final class TriggerDefinitionCodec {
    private TriggerDefinitionCodec() {
    }

    public static Codec<TriggerDefinition> create(TriggerTypeLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return Codec.of(new Encoder<>() {
            @Override
            public <T> DataResult<T> encode(
                    TriggerDefinition input,
                    DynamicOps<T> ops,
                    T prefix) {
                return encodeDefinition(input, lookup, ops, prefix);
            }
        }, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<TriggerDefinition, T>> decode(DynamicOps<T> ops, T input) {
                return DefinitionEnvelope.CODEC.decode(ops, input)
                        .map(pair -> Pair.of(resolve(pair.getFirst(), lookup), pair.getSecond()));
            }
        });
    }

    /** Resolves a previously decoded envelope without invoking semantic validation. */
    public static TriggerDefinition resolve(DefinitionEnvelope envelope, TriggerTypeLookup lookup) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(lookup, "lookup");
        var descriptor = lookup.find(envelope.typeId());
        if (descriptor.isEmpty()) {
            return unknown(envelope, Code.UNKNOWN_TYPE, "Unknown trigger type: " + envelope.typeId());
        }
        return resolveKnown(envelope, descriptor.orElseThrow());
    }

    private static <P extends TriggerPayload> TriggerDefinition resolveKnown(
            DefinitionEnvelope envelope,
            TriggerType<P> descriptor) {
        try {
            var currentSchemaVersion = descriptor.currentPayloadSchemaVersion();
            if (currentSchemaVersion < 0) {
                return unknown(envelope, Code.CODEC_EXCEPTION, "Trigger descriptor returned a negative schema version");
            }
            if (envelope.schemaVersion() != currentSchemaVersion) {
                return unknown(
                        envelope,
                        Code.UNSUPPORTED_SCHEMA_VERSION,
                        "Unsupported trigger payload schema version " + envelope.schemaVersion()
                                + "; current version is " + currentSchemaVersion);
            }

            var decoded = descriptor.payloadCodec().codec().parse(envelope.copyRawPayload());
            var payload = decoded.result();
            if (payload.isEmpty()) {
                return unknown(
                        envelope,
                        Code.PAYLOAD_DECODE_ERROR,
                        decoded.error().map(DataResult.Error::message)
                                .orElse("Trigger payload Codec returned no complete result"));
            }
            return new ResolvedTriggerDefinition<>(descriptor, currentSchemaVersion, payload.orElseThrow());
        } catch (RuntimeException exception) {
            return unknown(envelope, Code.CODEC_EXCEPTION, exceptionDiagnostic(exception));
        }
    }

    private static <T> DataResult<T> encodeDefinition(
            TriggerDefinition definition,
            TriggerTypeLookup lookup,
            DynamicOps<T> ops,
            T prefix) {
        Objects.requireNonNull(definition, "definition");
        if (definition instanceof UnknownTriggerDefinition unknown) {
            return DefinitionEnvelope.CODEC.encode(unknown.envelope(), ops, prefix);
        }
        if (definition instanceof ResolvedTriggerDefinition<?> resolved) {
            return encodeResolved(resolved, lookup, ops, prefix);
        }
        return DataResult.error(() -> "Unsupported TriggerDefinition implementation");
    }

    private static <T, P extends TriggerPayload> DataResult<T> encodeResolved(
            ResolvedTriggerDefinition<P> resolved,
            TriggerTypeLookup lookup,
            DynamicOps<T> ops,
            T prefix) {
        var typeId = lookup.keyOf(resolved.descriptor());
        if (typeId.isEmpty()) {
            return DataResult.error(() -> "Trigger descriptor is not registered");
        }

        try {
            var currentSchemaVersion = resolved.descriptor().currentPayloadSchemaVersion();
            if (currentSchemaVersion < 0) {
                return DataResult.error(() -> "Trigger descriptor returned a negative schema version");
            }
            if (resolved.schemaVersion() != currentSchemaVersion) {
                return DataResult.error(() -> "Resolved trigger schema version does not match its descriptor");
            }

            return resolved.descriptor().payloadCodec().codec().encodeStart(ops, resolved.payload())
                    .mapError(TriggerDefinitionCodec::boundedDiagnostic)
                    .flatMap(rawPayload -> DefinitionEnvelope.CODEC.encode(
                            new DefinitionEnvelope(
                                    typeId.orElseThrow(),
                                    currentSchemaVersion,
                                    new Dynamic<>(ops, rawPayload)),
                            ops,
                            prefix));
        } catch (RuntimeException exception) {
            var diagnostic = boundedDiagnostic(exceptionDiagnostic(exception));
            return DataResult.error(() -> diagnostic);
        }
    }

    private static UnknownTriggerDefinition unknown(
            DefinitionEnvelope envelope,
            Code code,
            String diagnostic) {
        return new UnknownTriggerDefinition(envelope, DefinitionFailure.of(code, diagnostic));
    }

    private static String exceptionDiagnostic(RuntimeException exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private static String boundedDiagnostic(String diagnostic) {
        return DefinitionFailure.of(Code.CODEC_EXCEPTION, diagnostic).diagnostic();
    }
}
