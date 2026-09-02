package com.yo1no.gramarye.magic.runtime.mana;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

record ActionExecutorRegistration(ResourceLocation key, ActionExecutor executor) {
    ActionExecutorRegistration {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(executor, "executor");
    }
}

/** Immutable exact-key dispatch table assembled by a later composition owner. */
final class ActionExecutorRegistry {
    private final Map<ResourceLocation, ActionExecutor> executors;

    ActionExecutorRegistry(List<ActionExecutorRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        Map<ResourceLocation, ActionExecutor> copied = new LinkedHashMap<>();
        for (ActionExecutorRegistration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            if (copied.putIfAbsent(registration.key(), registration.executor()) != null) {
                throw new IllegalArgumentException("duplicate action executor key");
            }
        }
        executors = Map.copyOf(copied);
    }

    Optional<ActionExecutor> find(ResourceLocation key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(executors.get(key));
    }

    int size() {
        return executors.size();
    }
}
