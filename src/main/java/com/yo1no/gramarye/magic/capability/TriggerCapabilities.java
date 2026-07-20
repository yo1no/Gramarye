package com.yo1no.gramarye.magic.capability;

import java.util.Objects;
import java.util.Set;

public record TriggerCapabilities(
        SourceRequirement sourceRequirement,
        TargetRequirement targetRequirement,
        boolean requiresContinuationState,
        Set<TriggerEventKind> eventKinds,
        Set<TriggerSourceScope> supportedSourceScopes,
        Set<TriggerGranularity> supportedGranularities) {
    public TriggerCapabilities {
        Objects.requireNonNull(sourceRequirement, "sourceRequirement");
        Objects.requireNonNull(targetRequirement, "targetRequirement");
        eventKinds = immutableNonEmpty(eventKinds, "eventKinds");
        supportedSourceScopes = immutableNonEmpty(supportedSourceScopes, "supportedSourceScopes");
        supportedGranularities = immutableNonEmpty(supportedGranularities, "supportedGranularities");
    }

    private static <T> Set<T> immutableNonEmpty(Set<T> values, String name) {
        var copy = Set.copyOf(Objects.requireNonNull(values, name));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return copy;
    }
}
