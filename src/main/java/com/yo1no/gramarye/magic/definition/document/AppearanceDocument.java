package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Persistent top-level appearance state, isolated from gameplay validity. */
public sealed interface AppearanceDocument
        permits AppearanceDocument.Default,
                AppearanceDocument.Decoded,
                AppearanceDocument.Unparsed,
                AppearanceDocument.Rejected {
    static AppearanceDocument defaultAppearance() {
        return Default.INSTANCE;
    }

    static AppearanceDocument decoded(AppearanceDefinition definition) {
        return definition.isEmpty() ? Default.INSTANCE : new Decoded(definition);
    }

    enum Default implements AppearanceDocument {
        INSTANCE
    }

    record Decoded(AppearanceDefinition definition) implements AppearanceDocument {
        public Decoded {
            Objects.requireNonNull(definition, "definition");
            if (definition.isEmpty()) {
                throw new IllegalArgumentException("Empty top-level appearance must use Default");
            }
        }
    }

    final class Unparsed implements AppearanceDocument {
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
            return "AppearanceDocument.Unparsed[family=" + snapshot.family() + "]";
        }
    }

    record Rejected(AppearanceRejectionCode reason) implements AppearanceDocument {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
