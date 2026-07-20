package com.yo1no.gramarye.magic.capability;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Extensible structural event kind that P5 internal events will declare. */
public record TriggerEventKind(ResourceLocation key) {
    public TriggerEventKind {
        Objects.requireNonNull(key, "key");
    }
}
