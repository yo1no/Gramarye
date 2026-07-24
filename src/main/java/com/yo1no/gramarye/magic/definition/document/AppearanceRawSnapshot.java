package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import java.util.Objects;

sealed interface AppearanceRawSnapshot permits SupportedAppearanceRawSnapshot {
    Dynamic<?> copyDynamic();

    SerializedTreeFamily family();

    boolean structurallyEquals(AppearanceRawSnapshot other);

    int structuralHashCode();

    static DataResult<AppearanceRawSnapshot> capture(Dynamic<?> rawAppearance) {
        if (rawAppearance == null) {
            return DataResult.error(() -> "Raw appearance must not be null");
        }
        try {
            var copyResult = SupportedDynamicTrees.defensiveCopy(rawAppearance);
            if (copyResult.error().isPresent()) {
                var ops = rawAppearance.getOps();
                var value = rawAppearance.getValue();
                return DataResult.error(() -> DynamicTreeSupport.boundedDiagnostic(
                        "Unsupported raw appearance representation: ops=" + ops.getClass().getName()
                                + ", value=" + (value == null ? "null" : value.getClass().getName())));
            }
            var copy = copyResult.result().orElseThrow();
            var context = SupportedDynamicTrees.contextOf(copy).result().orElseThrow();
            return DataResult.success(new SupportedAppearanceRawSnapshot(context, copy));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> DynamicTreeSupport.boundedDiagnostic(
                    "Unable to snapshot raw appearance: " + exception.getClass().getSimpleName()));
        }
    }
}

final class SupportedAppearanceRawSnapshot implements AppearanceRawSnapshot {
    private final SerializedTreeContext context;
    private final Dynamic<?> value;

    SupportedAppearanceRawSnapshot(SerializedTreeContext context, Dynamic<?> value) {
        this.context = Objects.requireNonNull(context, "context");
        this.value = Objects.requireNonNull(value, "value");
    }

    @Override
    public Dynamic<?> copyDynamic() {
        return SupportedDynamicTrees.defensiveCopy(value).getOrThrow();
    }

    @Override
    public SerializedTreeFamily family() {
        return context.family();
    }

    @Override
    public boolean structurallyEquals(AppearanceRawSnapshot other) {
        return other instanceof SupportedAppearanceRawSnapshot snapshot
                && context.family() == snapshot.context.family()
                && value.getValue().equals(snapshot.value.getValue());
    }

    @Override
    public int structuralHashCode() {
        return value.getValue().hashCode();
    }
}
