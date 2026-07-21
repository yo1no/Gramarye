package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Persistent three-state selection for a presentation profile. */
public sealed interface ProfileSelection
        permits ProfileSelection.Inherit, ProfileSelection.Disabled, ProfileSelection.Specified {
    static ProfileSelection inherit() {
        return Inherit.INSTANCE;
    }

    static ProfileSelection disabled() {
        return Disabled.INSTANCE;
    }

    static ProfileSelection specified(ResourceLocation id) {
        return new Specified(id);
    }

    enum Inherit implements ProfileSelection {
        INSTANCE
    }

    enum Disabled implements ProfileSelection {
        INSTANCE
    }

    record Specified(ResourceLocation id) implements ProfileSelection {
        public Specified {
            Objects.requireNonNull(id, "id");
        }
    }
}
