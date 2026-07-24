package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Machine-readable failures confined to the P4-A raw/document boundary. */
sealed interface SkillDocumentPersistenceFailure
        permits SkillDocumentPersistenceFailure.UnsupportedRawFamily,
                SkillDocumentPersistenceFailure.InvalidRawContext,
                SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded,
                SkillDocumentPersistenceFailure.DocumentEncodedCapacityExceeded,
                SkillDocumentPersistenceFailure.MalformedJsonRaw,
                SkillDocumentPersistenceFailure.MalformedNbtRaw,
                SkillDocumentPersistenceFailure.RegistryContextUnavailable,
                SkillDocumentPersistenceFailure.MalformedPhysicalDocument,
                SkillDocumentPersistenceFailure.UnsupportedDocumentSchema,
                SkillDocumentPersistenceFailure.DocumentBoundsExceeded,
                SkillDocumentPersistenceFailure.EncodeFailed,
                SkillDocumentPersistenceFailure.InternalCodecException {
    SkillDocumentPersistenceLocation location();

    record UnsupportedRawFamily(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public UnsupportedRawFamily {
            Objects.requireNonNull(location, "location");
        }
    }

    record InvalidRawContext(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public InvalidRawContext {
            Objects.requireNonNull(location, "location");
        }
    }

    record RawEntryEncodedCapacityExceeded(
            SkillDocumentPersistenceLocation location,
            long observedAtLeast,
            long maximum) implements SkillDocumentPersistenceFailure {
        public RawEntryEncodedCapacityExceeded {
            Objects.requireNonNull(location, "location");
            requireCapacityExceeded(observedAtLeast, maximum);
        }
    }

    record DocumentEncodedCapacityExceeded(
            SkillDocumentPersistenceLocation location,
            long observedAtLeast,
            long maximum) implements SkillDocumentPersistenceFailure {
        public DocumentEncodedCapacityExceeded {
            Objects.requireNonNull(location, "location");
            requireCapacityExceeded(observedAtLeast, maximum);
        }
    }

    record MalformedJsonRaw(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public MalformedJsonRaw {
            Objects.requireNonNull(location, "location");
        }
    }

    record MalformedNbtRaw(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public MalformedNbtRaw {
            Objects.requireNonNull(location, "location");
        }
    }

    record RegistryContextUnavailable(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public RegistryContextUnavailable {
            Objects.requireNonNull(location, "location");
        }
    }

    record MalformedPhysicalDocument(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public MalformedPhysicalDocument {
            Objects.requireNonNull(location, "location");
        }
    }

    record UnsupportedDocumentSchema(
            SkillDocumentPersistenceLocation location,
            int actual,
            int supported) implements SkillDocumentPersistenceFailure {
        public UnsupportedDocumentSchema {
            Objects.requireNonNull(location, "location");
            if (actual < 0 || supported < 0) {
                throw new IllegalArgumentException("schema versions must be non-negative");
            }
        }
    }

    record DocumentBoundsExceeded(
            SkillDocumentPersistenceLocation location,
            DocumentBoundKind kind) implements SkillDocumentPersistenceFailure {
        public DocumentBoundsExceeded {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(kind, "kind");
        }
    }

    record EncodeFailed(SkillDocumentPersistenceLocation location)
            implements SkillDocumentPersistenceFailure {
        public EncodeFailed {
            Objects.requireNonNull(location, "location");
        }
    }

    record InternalCodecException(
            SkillDocumentPersistenceLocation location,
            String exceptionClassName) implements SkillDocumentPersistenceFailure {
        public InternalCodecException {
            Objects.requireNonNull(location, "location");
            exceptionClassName = boundedClassName(exceptionClassName);
        }

        static InternalCodecException from(
                SkillDocumentPersistenceLocation location,
                RuntimeException exception) {
            Objects.requireNonNull(exception, "exception");
            return new InternalCodecException(location, exception.getClass().getName());
        }
    }

    enum DocumentBoundKind {
        DEPTH,
        NODE_COUNT,
        KEY_LENGTH
    }

    private static void requireCapacityExceeded(long observedAtLeast, long maximum) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("maximum must be positive");
        }
        if (observedAtLeast <= maximum) {
            throw new IllegalArgumentException("observedAtLeast must exceed maximum");
        }
    }

    private static String boundedClassName(String exceptionClassName) {
        Objects.requireNonNull(exceptionClassName, "exceptionClassName");
        if (exceptionClassName.isBlank()) {
            throw new IllegalArgumentException("exceptionClassName must not be blank");
        }
        return exceptionClassName.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? exceptionClassName
                : exceptionClassName.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }
}
