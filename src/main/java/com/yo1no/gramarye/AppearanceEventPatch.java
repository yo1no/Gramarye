package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Raw-free trusted event-patch candidate; validation and application are atomic. */
final class AppearanceEventPatch {
    private final AppearanceOverride override;
    private final Map<ResourceLocation, Integer> parameters;
    private final boolean overCapacity;

    AppearanceEventPatch(
            AppearanceOverride override, Map<ResourceLocation, Integer> parameters) {
        this.override = Objects.requireNonNull(override, "override");
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.size() > PresentationLimits.MAX_EVENT_OVERRIDES) {
            this.parameters = Map.of();
            this.overCapacity = true;
            return;
        }
        var copy = new TreeMap<ResourceLocation, Integer>(
                Comparator.nullsFirst(Comparator.comparing(ResourceLocation::toString)));
        copy.putAll(parameters);
        this.parameters = Collections.unmodifiableMap(copy);
        this.overCapacity = false;
    }

    AppearanceOverride override() {
        return override;
    }

    Map<ResourceLocation, Integer> parameters() {
        return parameters;
    }

    boolean overCapacity() {
        return overCapacity;
    }

    boolean isEmpty() {
        return !overCapacity && override.isEmpty() && parameters.isEmpty();
    }
}
