package com.yo1no.gramarye.magic.definition.tree;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.Objects;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

/** The sole classifier and defensive-copy boundary for supported mutable Dynamic trees. */
public final class SupportedDynamicTrees {
    private SupportedDynamicTrees() {
    }

    /** Inspects both a tree value and its operations context without exposing the value. */
    public static DataResult<SerializedTreeContext> contextOf(Dynamic<?> source) {
        if (source == null) {
            return DataResult.error(() -> "Dynamic tree must not be null");
        }
        return supportedValue(source).map(SupportedValue::context);
    }

    /** Inspects a supported direct or RegistryOps-wrapped operations context. */
    public static DataResult<SerializedTreeContext> contextOf(DynamicOps<?> ops) {
        if (ops == null) {
            return DataResult.error(() -> "DynamicOps must not be null");
        }

        var json = jsonOps(ops);
        if (json.result().isPresent()) {
            return DataResult.success(json.result().orElseThrow().context());
        }
        var nbt = nbtOps(ops);
        if (nbt.result().isPresent()) {
            return DataResult.success(nbt.result().orElseThrow().context());
        }
        return DataResult.error(() -> "Unsupported DynamicOps context: " + ops.getClass().getName());
    }

    /** Returns a new same-family deep copy that retains the exact supported DynamicOps instance. */
    public static DataResult<Dynamic<?>> defensiveCopy(Dynamic<?> source) {
        if (source == null) {
            return DataResult.error(() -> "Dynamic tree must not be null");
        }
        return supportedValue(source).map(SupportedValue::defensiveCopy);
    }

    private static DataResult<SupportedValue> supportedValue(Dynamic<?> source) {
        var value = source.getValue();
        if (value instanceof JsonElement jsonValue) {
            return jsonOps(source.getOps()).map(match ->
                    (SupportedValue) new JsonValue(match.ops(), jsonValue, match.context()));
        }
        if (value instanceof Tag nbtValue) {
            return nbtOps(source.getOps()).map(match ->
                    (SupportedValue) new NbtValue(match.ops(), nbtValue, match.context()));
        }
        return DataResult.error(() -> "Unsupported Dynamic value family: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static DataResult<JsonOpsMatch> jsonOps(DynamicOps<?> ops) {
        if (ops instanceof JsonOps jsonOps) {
            return DataResult.success(new JsonOpsMatch(
                    jsonOps,
                    new SerializedTreeContext(
                            SerializedTreeFamily.JSON,
                            false,
                            jsonOps.compressMaps())));
        }
        if (ops instanceof RegistryOps<?> registryOps) {
            var parent = registryOps.compressMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
            var typed = registryOps.withParent(parent);
            if (registryOps.equals(typed)) {
                return DataResult.success(new JsonOpsMatch(
                        typed,
                        new SerializedTreeContext(
                                SerializedTreeFamily.JSON,
                                true,
                                parent.compressMaps())));
            }
        }
        return DataResult.error(() -> "DynamicOps is not a supported JSON context");
    }

    private static DataResult<NbtOpsMatch> nbtOps(DynamicOps<?> ops) {
        if (ops instanceof NbtOps nbtOps) {
            return DataResult.success(new NbtOpsMatch(
                    nbtOps,
                    new SerializedTreeContext(SerializedTreeFamily.NBT, false, false)));
        }
        if (ops instanceof RegistryOps<?> registryOps) {
            var typed = registryOps.withParent(NbtOps.INSTANCE);
            if (registryOps.equals(typed)) {
                return DataResult.success(new NbtOpsMatch(
                        typed,
                        new SerializedTreeContext(SerializedTreeFamily.NBT, true, false)));
            }
        }
        return DataResult.error(() -> "DynamicOps is not a supported NBT context");
    }

    private sealed interface SupportedValue permits JsonValue, NbtValue {
        SerializedTreeContext context();

        Dynamic<?> defensiveCopy();
    }

    private record JsonValue(
            DynamicOps<JsonElement> ops,
            JsonElement value,
            SerializedTreeContext context) implements SupportedValue {
        private JsonValue {
            Objects.requireNonNull(ops, "ops");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(context, "context");
        }

        @Override
        public Dynamic<JsonElement> defensiveCopy() {
            return new Dynamic<>(ops, value.deepCopy());
        }
    }

    private record NbtValue(
            DynamicOps<Tag> ops,
            Tag value,
            SerializedTreeContext context) implements SupportedValue {
        private NbtValue {
            Objects.requireNonNull(ops, "ops");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(context, "context");
        }

        @Override
        public Dynamic<Tag> defensiveCopy() {
            return new Dynamic<>(ops, value.copy());
        }
    }

    private record JsonOpsMatch(
            DynamicOps<JsonElement> ops,
            SerializedTreeContext context) {
    }

    private record NbtOpsMatch(
            DynamicOps<Tag> ops,
            SerializedTreeContext context) {
    }
}
