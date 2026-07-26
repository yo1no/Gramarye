package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Bounded machine-readable failures from the P4-B2-A primary file boundary. */
sealed interface SkillSavedDataPrimaryFailure
        permits SkillSavedDataPrimaryFailure.OuterSavedDataUnreadable,
                SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded,
                SkillSavedDataPrimaryFailure.UnsupportedPrimaryFileType,
                SkillSavedDataPrimaryFailure.PrimaryFileIdentityUnavailable,
                SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected,
                SkillSavedDataPrimaryFailure.MalformedGzip,
                SkillSavedDataPrimaryFailure.MultipleGzipMembers,
                SkillSavedDataPrimaryFailure.CompressedTrailingData,
                SkillSavedDataPrimaryFailure.DecompressedCarrierFailure {

    record OuterSavedDataUnreadable(PrimaryIngressStage stage)
            implements SkillSavedDataPrimaryFailure {
        public OuterSavedDataUnreadable {
            Objects.requireNonNull(stage, "stage");
        }
    }

    record SavedDataFileCapacityExceeded(long observedAtLeast, long maximum)
            implements SkillSavedDataPrimaryFailure {
        public SavedDataFileCapacityExceeded {
            if (maximum <= 0 || observedAtLeast <= maximum) {
                throw new IllegalArgumentException(
                        "capacity metadata requires observedAtLeast > maximum > 0");
            }
        }
    }

    record UnsupportedPrimaryFileType(PrimaryFileKind kind)
            implements SkillSavedDataPrimaryFailure {
        public UnsupportedPrimaryFileType {
            Objects.requireNonNull(kind, "kind");
        }
    }

    enum PrimaryFileIdentityUnavailable implements SkillSavedDataPrimaryFailure {
        INSTANCE
    }

    record PrimaryFileRaceDetected(PrimaryFileRaceKind kind)
            implements SkillSavedDataPrimaryFailure {
        public PrimaryFileRaceDetected {
            Objects.requireNonNull(kind, "kind");
        }
    }

    record MalformedGzip(GzipFailureKind kind)
            implements SkillSavedDataPrimaryFailure {
        public MalformedGzip {
            Objects.requireNonNull(kind, "kind");
        }
    }

    enum MultipleGzipMembers implements SkillSavedDataPrimaryFailure {
        INSTANCE
    }

    enum CompressedTrailingData implements SkillSavedDataPrimaryFailure {
        INSTANCE
    }

    record DecompressedCarrierFailure(SkillSavedDataCarrierFailure failure)
            implements SkillSavedDataPrimaryFailure {
        public DecompressedCarrierFailure {
            Objects.requireNonNull(failure, "failure");
        }
    }

    enum PrimaryIngressStage {
        INITIAL_ATTRIBUTES,
        ABSENT_RECHECK,
        OPEN_CHANNEL,
        POST_OPEN_ATTRIBUTES,
        READ_CHANNEL,
        FINAL_ATTRIBUTES,
        CLOSE_CHANNEL
    }

    enum PrimaryFileKind {
        SYMBOLIC_LINK,
        DIRECTORY,
        OTHER
    }

    enum PrimaryFileRaceKind {
        APPEARED_AFTER_ABSENT_CHECK,
        REPLACED,
        GREW_DURING_READ,
        SHRANK_DURING_READ
    }

    enum GzipFailureKind {
        HEADER_INVALID,
        FHCRC_INVALID,
        DEFLATE_INVALID,
        TRAILER_CRC_INVALID,
        TRAILER_ISIZE_INVALID,
        TRUNCATED
    }
}
