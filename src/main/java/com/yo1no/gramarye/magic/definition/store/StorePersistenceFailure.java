package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Bounded, machine-readable P4-A2 encode/load failure vocabulary. */
sealed interface StorePersistenceFailure
        permits StorePersistenceFailure.StoreBlobEncodedCapacityExceeded,
                StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded,
                StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded,
                StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded,
                StorePersistenceFailure.MalformedStoreEnvelope,
                StorePersistenceFailure.MalformedHistoryEnvelope,
                StorePersistenceFailure.MalformedRevisionEnvelope,
                StorePersistenceFailure.UnsupportedStoreSchema,
                StorePersistenceFailure.UnsupportedDocumentEncoding,
                StorePersistenceFailure.StoreEnvelopeMigrationFailed,
                StorePersistenceFailure.DocumentMigrationFailed,
                StorePersistenceFailure.OpaqueTokenInvariantViolation,
                StorePersistenceFailure.DocumentDecodeFailed,
                StorePersistenceFailure.RegistryContextUnavailable,
                StorePersistenceFailure.StoreRestoreRejected,
                StorePersistenceFailure.EncodeFailed {

    record StoreBlobEncodedCapacityExceeded(long observedAtLeast, long maximum)
            implements StorePersistenceFailure {
        public StoreBlobEncodedCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record HistoryBlobEncodedCapacityExceeded(long observedAtLeast, long maximum)
            implements StorePersistenceFailure {
        public HistoryBlobEncodedCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record RevisionBlobEncodedCapacityExceeded(long observedAtLeast, long maximum)
            implements StorePersistenceFailure {
        public RevisionBlobEncodedCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record DocumentBlobEncodedCapacityExceeded(long observedAtLeast, long maximum)
            implements StorePersistenceFailure {
        public DocumentBlobEncodedCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    enum MalformedStoreEnvelope implements StorePersistenceFailure {
        INSTANCE
    }

    enum MalformedHistoryEnvelope implements StorePersistenceFailure {
        INSTANCE
    }

    enum MalformedRevisionEnvelope implements StorePersistenceFailure {
        INSTANCE
    }

    record UnsupportedStoreSchema(int actual, int supported)
            implements StorePersistenceFailure {
        public UnsupportedStoreSchema {
            if (actual < 0 || supported < 0 || actual == supported) {
                throw new IllegalArgumentException("unsupported schema metadata is invalid");
            }
        }
    }

    record UnsupportedDocumentEncoding(SkillReference reference)
            implements StorePersistenceFailure {
        public UnsupportedDocumentEncoding {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record StoreEnvelopeMigrationFailed(StorePersistenceMigrationFailure failure)
            implements StorePersistenceFailure {
        public StoreEnvelopeMigrationFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record DocumentMigrationFailed(SkillReference reference)
            implements StorePersistenceFailure {
        public DocumentMigrationFailed {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record OpaqueTokenInvariantViolation(SkillReference reference)
            implements StorePersistenceFailure {
        public OpaqueTokenInvariantViolation {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record DocumentDecodeFailed(SkillReference reference)
            implements StorePersistenceFailure {
        public DocumentDecodeFailed {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record RegistryContextUnavailable(SkillReference reference)
            implements StorePersistenceFailure {
        public RegistryContextUnavailable {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record StoreRestoreRejected(SkillDefinitionStoreRestoreFailure failure)
            implements StorePersistenceFailure {
        public StoreRestoreRejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum EncodeFailed implements StorePersistenceFailure {
        INSTANCE
    }

    private static void requireCapacity(long observedAtLeast, long maximum) {
        if (maximum <= 0 || observedAtLeast <= maximum) {
            throw new IllegalArgumentException("capacity metadata requires observedAtLeast > maximum > 0");
        }
    }
}
