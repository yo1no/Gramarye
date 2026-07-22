package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import java.util.Objects;

/** Raw-free top-level appearance state retained by a validated definition. */
public sealed interface RuntimeNeutralAppearance
        permits RuntimeNeutralAppearance.Default,
                RuntimeNeutralAppearance.Typed,
                RuntimeNeutralAppearance.Fallback {
    enum Default implements RuntimeNeutralAppearance {
        INSTANCE
    }

    record Typed(AppearanceDefinition definition) implements RuntimeNeutralAppearance {
        public Typed {
            Objects.requireNonNull(definition, "definition");
        }
    }

    record Fallback(AppearanceFallbackReason reason) implements RuntimeNeutralAppearance {
        public Fallback {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
