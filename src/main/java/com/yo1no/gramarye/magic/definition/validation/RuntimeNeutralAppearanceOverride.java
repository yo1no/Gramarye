package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import java.util.Objects;

/** Raw-free node appearance override retained by a validated definition. */
public sealed interface RuntimeNeutralAppearanceOverride
        permits RuntimeNeutralAppearanceOverride.None,
                RuntimeNeutralAppearanceOverride.Typed,
                RuntimeNeutralAppearanceOverride.Fallback {
    enum None implements RuntimeNeutralAppearanceOverride {
        INSTANCE
    }

    record Typed(AppearanceOverride override) implements RuntimeNeutralAppearanceOverride {
        public Typed {
            Objects.requireNonNull(override, "override");
        }
    }

    record Fallback(AppearanceFallbackReason reason)
            implements RuntimeNeutralAppearanceOverride {
        public Fallback {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
