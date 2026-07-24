package com.yo1no.gramarye.magic.definition.envelope;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

sealed interface RawPayloadSnapshot permits SupportedRawPayloadSnapshot {
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
            var copyResult = SupportedDynamicTrees.defensiveCopy(rawPayload);
            if (copyResult.error().isPresent()) {
                var ops = rawPayload.getOps();
                var value = rawPayload.getValue();
                var diagnostic = boundedDiagnostic(
                        "Unsupported raw payload representation: ops=" + ops.getClass().getName()
                                + ", value=" + (value == null ? "null" : value.getClass().getName()));
                return DataResult.error(() -> diagnostic);
            }
            var copy = copyResult.result().orElseThrow();
            var context = SupportedDynamicTrees.contextOf(copy).result().orElseThrow();
            return DataResult.success(new SupportedRawPayloadSnapshot(context, copy));
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

final class SupportedRawPayloadSnapshot implements RawPayloadSnapshot {
    private final SerializedTreeContext context;
    private final Dynamic<?> value;

    SupportedRawPayloadSnapshot(SerializedTreeContext context, Dynamic<?> value) {
        this.context = Objects.requireNonNull(context, "context");
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public Dynamic<?> copyDynamic() {
        return SupportedDynamicTrees.defensiveCopy(value).getOrThrow();
    }

    @Override
    public boolean sharesValueReference(Dynamic<?> candidate) {
        return candidate.getValue() == value.getValue();
    }

    @Override
    public String familyName() {
        return context.family().serializedName();
    }

    @Override
    public boolean structurallyEquals(RawPayloadSnapshot other) {
        return other instanceof SupportedRawPayloadSnapshot snapshot
                && context.family() == snapshot.context.family()
                && value.getValue().equals(snapshot.value.getValue());
    }

    @Override
    public int structuralHashCode() {
        return value.getValue().hashCode();
    }
}
