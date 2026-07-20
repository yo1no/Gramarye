package com.yo1no.gramarye.magic.definition.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.definition.action.ActionDefinition;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.action.UnknownActionDefinition;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure.Code;
import com.yo1no.gramarye.magic.definition.lookup.ActionTypeLookup;
import java.util.Objects;

/** Registry-aware Action definition resolution and canonical typed encoding. */
public final class ActionDefinitionCodec {
    private ActionDefinitionCodec() {
    }

    public static Codec<ActionDefinition> create(ActionTypeLookup lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return Codec.of(new Encoder<>() {
            @Override
            public <T> DataResult<T> encode(
                    ActionDefinition input,
                    DynamicOps<T> ops,
                    T prefix) {
                return encodeDefinition(input, lookup, ops, prefix);
            }
        }, new Decoder<>() {
            @Override
            public <T> DataResult<Pair<ActionDefinition, T>> decode(DynamicOps<T> ops, T input) {
                return DefinitionEnvelope.CODEC.decode(ops, input)
                        .map(pair -> Pair.of(resolve(pair.getFirst(), lookup), pair.getSecond()));
            }
        });
    }

    /** Resolves a previously decoded envelope without invoking semantic validation. */
    public static ActionDefinition resolve(DefinitionEnvelope envelope, ActionTypeLookup lookup) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(lookup, "lookup");
        var descriptor = lookup.find(envelope.typeId());
        if (descriptor.isEmpty()) {
            return unknown(envelope, Code.UNKNOWN_TYPE, "Unknown action type: " + envelope.typeId());
        }
        return resolveKnown(envelope, descriptor.orElseThrow());
    }

    private static <P extends ActionPayload> ActionDefinition resolveKnown(
            DefinitionEnvelope envelope,
            ActionType<P> descriptor) {
        try {
            var currentSchemaVersion = descriptor.currentPayloadSchemaVersion();
            if (currentSchemaVersion < 0) {
                return unknown(envelope, Code.CODEC_EXCEPTION, "Action descriptor returned a negative schema version");
            }
            if (envelope.schemaVersion() != currentSchemaVersion) {
                return unknown(
                        envelope,
                        Code.UNSUPPORTED_SCHEMA_VERSION,
                        "Unsupported action payload schema version " + envelope.schemaVersion()
                                + "; current version is " + currentSchemaVersion);
            }

            var decoded = descriptor.payloadCodec().codec().parse(envelope.copyRawPayload());
            var payload = decoded.result();
            if (payload.isEmpty()) {
                return unknown(
                        envelope,
                        Code.PAYLOAD_DECODE_ERROR,
                        decoded.error().map(DataResult.Error::message)
                                .orElse("Action payload Codec returned no complete result"));
            }
            return new ResolvedActionDefinition<>(descriptor, currentSchemaVersion, payload.orElseThrow());
        } catch (RuntimeException exception) {
            return unknown(envelope, Code.CODEC_EXCEPTION, exceptionDiagnostic(exception));
        }
    }

    private static <T> DataResult<T> encodeDefinition(
            ActionDefinition definition,
            ActionTypeLookup lookup,
            DynamicOps<T> ops,
            T prefix) {
        Objects.requireNonNull(definition, "definition");
        if (definition instanceof UnknownActionDefinition unknown) {
            return DefinitionEnvelope.CODEC.encode(unknown.envelope(), ops, prefix);
        }
        if (definition instanceof ResolvedActionDefinition<?> resolved) {
            return encodeResolved(resolved, lookup, ops, prefix);
        }
        return DataResult.error(() -> "Unsupported ActionDefinition implementation");
    }

    private static <T, P extends ActionPayload> DataResult<T> encodeResolved(
            ResolvedActionDefinition<P> resolved,
            ActionTypeLookup lookup,
            DynamicOps<T> ops,
            T prefix) {
        var typeId = lookup.keyOf(resolved.descriptor());
        if (typeId.isEmpty()) {
            return DataResult.error(() -> "Action descriptor is not registered");
        }

        try {
            var currentSchemaVersion = resolved.descriptor().currentPayloadSchemaVersion();
            if (currentSchemaVersion < 0) {
                return DataResult.error(() -> "Action descriptor returned a negative schema version");
            }
            if (resolved.schemaVersion() != currentSchemaVersion) {
                return DataResult.error(() -> "Resolved action schema version does not match its descriptor");
            }

            return resolved.descriptor().payloadCodec().codec().encodeStart(ops, resolved.payload())
                    .mapError(ActionDefinitionCodec::boundedDiagnostic)
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

    private static UnknownActionDefinition unknown(
            DefinitionEnvelope envelope,
            Code code,
            String diagnostic) {
        return new UnknownActionDefinition(envelope, DefinitionFailure.of(code, diagnostic));
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
