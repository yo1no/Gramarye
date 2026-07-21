package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.Objects;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

sealed interface AppearanceRawSnapshot permits JsonAppearanceRawSnapshot, NbtAppearanceRawSnapshot {
    Dynamic<?> copyDynamic();

    SerializedTreeFamily family();

    boolean structurallyEquals(AppearanceRawSnapshot other);

    int structuralHashCode();

    static DataResult<AppearanceRawSnapshot> capture(Dynamic<?> rawAppearance) {
        if (rawAppearance == null) {
            return DataResult.error(() -> "Raw appearance must not be null");
        }
        try {
            var ops = rawAppearance.getOps();
            var value = rawAppearance.getValue();
            if (value instanceof JsonElement jsonValue) {
                return jsonOps(ops).map(jsonOps -> new JsonAppearanceRawSnapshot(jsonOps, jsonValue));
            }
            if (value instanceof Tag nbtValue) {
                return nbtOps(ops).map(nbtOps -> new NbtAppearanceRawSnapshot(nbtOps, nbtValue));
            }
            return DataResult.error(() -> DynamicTreeSupport.boundedDiagnostic(
                    "Unsupported raw appearance representation: ops=" + ops.getClass().getName()
                            + ", value=" + (value == null ? "null" : value.getClass().getName())));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> DynamicTreeSupport.boundedDiagnostic(
                    "Unable to snapshot raw appearance: " + exception.getClass().getSimpleName()));
        }
    }

    private static DataResult<DynamicOps<JsonElement>> jsonOps(DynamicOps<?> ops) {
        if (ops instanceof JsonOps jsonOps) {
            return DataResult.success(jsonOps);
        }
        if (ops instanceof RegistryOps<?> registryOps) {
            var parent = registryOps.compressMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
            var typed = registryOps.withParent(parent);
            if (registryOps.equals(typed)) {
                return DataResult.success(typed);
            }
        }
        return DataResult.error(() -> "JSON value is paired with unsupported DynamicOps");
    }

    private static DataResult<DynamicOps<Tag>> nbtOps(DynamicOps<?> ops) {
        if (ops instanceof NbtOps nbtOps) {
            return DataResult.success(nbtOps);
        }
        if (ops instanceof RegistryOps<?> registryOps) {
            var typed = registryOps.withParent(NbtOps.INSTANCE);
            if (registryOps.equals(typed)) {
                return DataResult.success(typed);
            }
        }
        return DataResult.error(() -> "NBT value is paired with unsupported DynamicOps");
    }
}

final class JsonAppearanceRawSnapshot implements AppearanceRawSnapshot {
    private final DynamicOps<JsonElement> ops;
    private final JsonElement value;

    JsonAppearanceRawSnapshot(DynamicOps<JsonElement> ops, JsonElement value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").deepCopy();
    }

    @Override
    public Dynamic<JsonElement> copyDynamic() {
        return new Dynamic<>(ops, value.deepCopy());
    }

    @Override
    public SerializedTreeFamily family() {
        return SerializedTreeFamily.JSON;
    }

    @Override
    public boolean structurallyEquals(AppearanceRawSnapshot other) {
        return other instanceof JsonAppearanceRawSnapshot snapshot && value.equals(snapshot.value);
    }

    @Override
    public int structuralHashCode() {
        return value.hashCode();
    }
}

final class NbtAppearanceRawSnapshot implements AppearanceRawSnapshot {
    private final DynamicOps<Tag> ops;
    private final Tag value;

    NbtAppearanceRawSnapshot(DynamicOps<Tag> ops, Tag value) {
        this.ops = Objects.requireNonNull(ops, "ops");
        this.value = Objects.requireNonNull(value, "value").copy();
    }

    @Override
    public Dynamic<Tag> copyDynamic() {
        return new Dynamic<>(ops, value.copy());
    }

    @Override
    public SerializedTreeFamily family() {
        return SerializedTreeFamily.NBT;
    }

    @Override
    public boolean structurallyEquals(AppearanceRawSnapshot other) {
        return other instanceof NbtAppearanceRawSnapshot snapshot && value.equals(snapshot.value);
    }

    @Override
    public int structuralHashCode() {
        return value.hashCode();
    }
}
