package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Persistent node-level appearance override state. */
public sealed interface AppearanceOverrideDocument
        permits AppearanceOverrideDocument.None,
                AppearanceOverrideDocument.Decoded,
                AppearanceOverrideDocument.Unparsed,
                AppearanceOverrideDocument.Rejected {
    static AppearanceOverrideDocument none() {
        return None.INSTANCE;
    }

    static AppearanceOverrideDocument decoded(AppearanceOverride override) {
        return override.isEmpty() ? None.INSTANCE : new Decoded(override);
    }

    enum None implements AppearanceOverrideDocument {
        INSTANCE
    }

    record Decoded(AppearanceOverride override) implements AppearanceOverrideDocument {
        public Decoded {
            Objects.requireNonNull(override, "override");
            if (override.isEmpty()) {
                throw new IllegalArgumentException("Empty appearance override must use None");
            }
        }
    }

    final class Unparsed implements AppearanceOverrideDocument {
        private final AppearanceRawSnapshot snapshot;

        Unparsed(AppearanceRawSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public Dynamic<?> copyRawAppearance() {
            return snapshot.copyDynamic();
        }

        SerializedTreeFamily rawFamily() {
            return snapshot.family();
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Unparsed unparsed && snapshot.structurallyEquals(unparsed.snapshot);
        }

        @Override
        public int hashCode() {
            return snapshot.structuralHashCode();
        }

        @Override
        public String toString() {
            return "AppearanceOverrideDocument.Unparsed[family=" + snapshot.family() + "]";
        }
    }

    record Rejected(AppearanceRejectionCode reason) implements AppearanceOverrideDocument {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
