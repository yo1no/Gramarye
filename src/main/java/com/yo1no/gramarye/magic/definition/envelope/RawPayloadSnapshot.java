package com.yo1no.gramarye.magic.definition.envelope;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

sealed interface RawPayloadSnapshot permits JsonRawPayloadSnapshot, NbtRawPayloadSnapshot {
    Dynamic<?> copyDynamic();

    boolean sharesValueReference(Dynamic<?> candidate);

    String familyName();

    boolean structurallyEquals(RawPayloadSnapshot other);

    int structuralHashCode();

    static DataResult<RawPayloadSnapshot> capture(Dynamic<?> rawPayload) {
        if (rawPayload == null) {
            return DataResult.error(() -> "Raw payload must not be null");
        }

        try {
            var ops = rawPayload.getOps();
            var value = rawPayload.getValue();
            if (value instanceof JsonElement jsonValue) {
                if (ops instanceof JsonOps jsonOps) {
                    return DataResult.success(new JsonRawPayloadSnapshot(jsonOps, jsonValue));
                }
                if (ops instanceof RegistryOps<?> registryOps) {
                    var jsonOps = registryOps.compressMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
                    var typedRegistryOps = registryOps.withParent(jsonOps);
                    if (registryOps.equals(typedRegistryOps)) {
                        return DataResult.success(new JsonRawPayloadSnapshot(
                                typedRegistryOps,
                                jsonValue));
                    }
                }
            }
            if (value instanceof Tag nbtValue) {
                if (ops instanceof NbtOps nbtOps) {
                    return DataResult.success(new NbtRawPayloadSnapshot(nbtOps, nbtValue));
                }
                if (ops instanceof RegistryOps<?> registryOps) {
                    var typedRegistryOps = registryOps.withParent(NbtOps.INSTANCE);
                    if (registryOps.equals(typedRegistryOps)) {
                        return DataResult.success(new NbtRawPayloadSnapshot(
                                typedRegistryOps,
                                nbtValue));
                    }
                }
            }
            var diagnostic = boundedDiagnostic(
                    "Unsupported raw payload representation: ops=" + ops.getClass().getName()
                            + ", value=" + (value == null ? "null" : value.getClass().getName()));
            return DataResult.error(() -> diagnostic);
        } catch (RuntimeException exception) {
            var diagnostic = boundedDiagnostic(
                    "Unable to snapshot raw payload: " + exceptionDiagnostic(exception));
            return DataResult.error(() -> diagnostic);
        }
    }

    static RawPayloadSnapshot requireSupported(Dynamic<?> rawPayload) {
        Objects.requireNonNull(rawPayload, "rawPayload");
        var result = capture(rawPayload);
        return result.result().orElseThrow(() -> new IllegalArgumentException(
                result.error().map(DataResult.Error::message)
                        .orElse("Unable to snapshot raw payload")));
    }

    private static String exceptionDiagnostic(RuntimeException exception) {
        var message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }

    private static String boundedDiagnostic(String diagnostic) {
        return diagnostic.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? diagnostic
                : diagnostic.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }
}

final class JsonRawPayloadSnapshot implements RawPayloadSnapshot {
    private final DynamicOps<JsonElement> ops;
    private final JsonElement value;

    JsonRawPayloadSnapshot(DynamicOps<JsonElement> ops, JsonElement value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").deepCopy();
    }

    @Override
    public Dynamic<JsonElement> copyDynamic() {
        return new Dynamic<>(ops, value.deepCopy());
    }

    @Override
    public boolean sharesValueReference(Dynamic<?> candidate) {
        return candidate.getValue() == value;
    }

    @Override
    public String familyName() {
        return "json";
    }

    @Override
    public boolean structurallyEquals(RawPayloadSnapshot other) {
        return other instanceof JsonRawPayloadSnapshot snapshot
                && value.equals(snapshot.value);
    }

    @Override
    public int structuralHashCode() {
        return value.hashCode();
    }
}

final class NbtRawPayloadSnapshot implements RawPayloadSnapshot {
    private final DynamicOps<Tag> ops;
    private final Tag value;

    NbtRawPayloadSnapshot(DynamicOps<Tag> ops, Tag value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").copy();
    }

    @Override
    public Dynamic<Tag> copyDynamic() {
        return new Dynamic<>(ops, value.copy());
    }

    @Override
    public boolean sharesValueReference(Dynamic<?> candidate) {
        return candidate.getValue() == value;
    }

    @Override
    public String familyName() {
        return "nbt";
    }

    @Override
    public boolean structurallyEquals(RawPayloadSnapshot other) {
        return other instanceof NbtRawPayloadSnapshot snapshot
                && value.equals(snapshot.value);
    }

    @Override
    public int structuralHashCode() {
        return value.hashCode();
    }
}
