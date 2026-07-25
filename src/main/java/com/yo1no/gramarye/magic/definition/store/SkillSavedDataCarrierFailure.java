package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Bounded machine-readable P4-B1 framing, migration, load, and rebuild failure. */
sealed interface SkillSavedDataCarrierFailure
        permits SkillSavedDataCarrierFailure.DecompressedWholeRootCapacityExceeded,
                SkillSavedDataCarrierFailure.SavedDataCarrierCapacityExceeded,
                SkillSavedDataCarrierFailure.PendingAttachmentUpdatesCapacityExceeded,
                SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope,
                SkillSavedDataCarrierFailure.UnsupportedSavedDataSchema,
                SkillSavedDataCarrierFailure.SavedDataEnvelopeMigrationFailed,
                SkillSavedDataCarrierFailure.OpaqueTokenInvariantViolation,
                SkillSavedDataCarrierFailure.StoreLoadFailed,
                SkillSavedDataCarrierFailure.CarrierRebuildFailed,
                SkillSavedDataCarrierFailure.InternalCodecException {

    record DecompressedWholeRootCapacityExceeded(long observedAtLeast, long maximum)
            implements SkillSavedDataCarrierFailure {
        public DecompressedWholeRootCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record SavedDataCarrierCapacityExceeded(long observedAtLeast, long maximum)
            implements SkillSavedDataCarrierFailure {
        public SavedDataCarrierCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record PendingAttachmentUpdatesCapacityExceeded(long observedAtLeast, long maximum)
            implements SkillSavedDataCarrierFailure {
        public PendingAttachmentUpdatesCapacityExceeded {
            requireCapacity(observedAtLeast, maximum);
        }
    }

    record MalformedSavedDataEnvelope(EnvelopeStage stage)
            implements SkillSavedDataCarrierFailure {
        public MalformedSavedDataEnvelope {
            Objects.requireNonNull(stage, "stage");
        }
    }

    record UnsupportedSavedDataSchema(int actual, int supported)
            implements SkillSavedDataCarrierFailure {
        public UnsupportedSavedDataSchema {
            if (actual < 0 || supported < 0 || actual <= supported) {
                throw new IllegalArgumentException("unsupported schema metadata is invalid");
            }
        }
    }

    record SavedDataEnvelopeMigrationFailed(
            SkillSavedDataCarrierMigrationFailure failure)
            implements SkillSavedDataCarrierFailure {
        public SavedDataEnvelopeMigrationFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum OpaqueTokenInvariantViolation implements SkillSavedDataCarrierFailure {
        INSTANCE
    }

    record StoreLoadFailed(StorePersistenceFailure failure)
            implements SkillSavedDataCarrierFailure {
        public StoreLoadFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record CarrierRebuildFailed(StorePersistenceFailure failure)
            implements SkillSavedDataCarrierFailure {
        public CarrierRebuildFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record InternalCodecException(EnvelopeStage stage, String exceptionClassName)
            implements SkillSavedDataCarrierFailure {
        public InternalCodecException {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
            if (exceptionClassName.isBlank()
                    || exceptionClassName.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("exception class name is invalid");
            }
        }

        static InternalCodecException from(EnvelopeStage stage, RuntimeException exception) {
            Objects.requireNonNull(exception, "exception");
            var name = exception.getClass().getName();
            return new InternalCodecException(
                    stage,
                    name.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                            ? name
                            : name.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH));
        }
    }

    enum EnvelopeStage {
        WHOLE_ROOT,
        INNER_CARRIER,
        OUTER_MIGRATION,
        STORE_LOAD,
        CARRIER_REBUILD
    }

    private static void requireCapacity(long observedAtLeast, long maximum) {
        if (maximum <= 0 || observedAtLeast <= maximum) {
            throw new IllegalArgumentException(
                    "capacity metadata requires observedAtLeast > maximum > 0");
        }
    }
}
