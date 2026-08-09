package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;

/**
 * Single checked owner of all P4-E V0 source-admission counters.
 *
 * <p>The object is deliberately thread-confined. A future global capture owns one instance and
 * creates one {@link FileScope} for each selected disk record. No checkpoint saturates or borrows
 * capacity from another counter.</p>
 */
final class P4E1AuditBudget {
    private final long[] observed = new long[P4E1AuditCounter.values().length];

    P4E1AuditBudget(P4E1SourceAdmissionPreflight.QualifiedPermit permit) {
        Objects.requireNonNull(permit, "qualified heap permit");
    }

    FileScope newFileScope() {
        return new FileScope(this);
    }

    Optional<Exceeded> checkpointSingle(
            P4E1AuditCounter counter,
            P4E1AuditStage stage,
            long delta) {
        Objects.requireNonNull(counter, "counter");
        var supported = switch (counter) {
            case DIRECTORY_ENTRIES, RELEVANT_RECORDS -> true;
            case COMPRESSED_BYTES_PER_FILE,
                    DECOMPRESSED_BYTES_PER_FILE,
                    CONTAINER_DEPTH_PER_FILE,
                    COMPOUND_CONTAINERS_PER_FILE,
                    COMPOUND_FIELD_ENTRIES_PER_FILE,
                    LIST_ELEMENTS_PER_FILE,
                    BYTE_ARRAY_ELEMENTS_PER_FILE,
                    INT_ARRAY_ELEMENTS_PER_FILE,
                    LONG_ARRAY_ELEMENTS_PER_FILE,
                    MODIFIED_UTF8_BYTES_PER_FILE,
                    SCALAR_TAGS_PER_FILE,
                    COMPRESSED_BYTES_TOTAL,
                    DECOMPRESSED_BYTES_TOTAL,
                    COMPOUND_CONTAINERS_TOTAL,
                    COMPOUND_FIELD_ENTRIES_TOTAL,
                    LIST_ELEMENTS_TOTAL,
                    BYTE_ARRAY_ELEMENTS_TOTAL,
                    INT_ARRAY_ELEMENTS_TOTAL,
                    LONG_ARRAY_ELEMENTS_TOTAL,
                    MODIFIED_UTF8_BYTES_TOTAL,
                    SCALAR_TAGS_TOTAL,
                    ATTACHMENT_ADMISSIONS,
                    RAW_ROOT_CLAIMS -> false;
        };
        if (!supported) {
            throw new IllegalArgumentException(
                    "counter requires its dedicated checkpoint: " + counter);
        }
        return checkpointValue(counter, stage, delta);
    }

    /** Called only after the shared P4-C admission invocation has returned. */
    Optional<Exceeded> checkpointAttachmentAdmissionAfterInvocation(P4E1AuditStage stage) {
        return checkpointValue(P4E1AuditCounter.ATTACHMENT_ADMISSIONS, stage, 1L);
    }

    /** Future P4-E1-B raw append checkpoint; it reuses the existing P3-D root ceiling. */
    Optional<Exceeded> checkpointRawRootClaim(P4E1AuditStage stage) {
        return checkpointRawRootClaims(stage, 1L);
    }

    /** Reserves a complete source delta before any root callback is permitted. */
    Optional<Exceeded> checkpointRawRootClaims(P4E1AuditStage stage, long delta) {
        return checkpointValue(P4E1AuditCounter.RAW_ROOT_CLAIMS, stage, delta);
    }

    long observed(P4E1AuditCounter counter) {
        return observed[Objects.requireNonNull(counter, "counter").ordinal()];
    }

    long maximum(P4E1AuditCounter counter) {
        Objects.requireNonNull(counter, "counter");
        return switch (counter) {
            case DIRECTORY_ENTRIES -> MagicSafetyCeilings.MAX_PLAYERDATA_DIRECTORY_ENTRIES;
            case RELEVANT_RECORDS -> MagicSafetyCeilings.MAX_PLAYERDATA_RELEVANT_RECORDS;
            case COMPRESSED_BYTES_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPRESSED_BYTES_PER_FILE;
            case DECOMPRESSED_BYTES_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_DECOMPRESSED_BYTES_PER_FILE;
            case CONTAINER_DEPTH_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_CONTAINER_DEPTH_PER_FILE;
            case COMPOUND_CONTAINERS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPOUND_CONTAINERS_PER_FILE;
            case COMPOUND_FIELD_ENTRIES_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_PER_FILE;
            case LIST_ELEMENTS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_LIST_ELEMENTS_PER_FILE;
            case BYTE_ARRAY_ELEMENTS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_PER_FILE;
            case INT_ARRAY_ELEMENTS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_PER_FILE;
            case LONG_ARRAY_ELEMENTS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_PER_FILE;
            case MODIFIED_UTF8_BYTES_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_PER_FILE;
            case SCALAR_TAGS_PER_FILE ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_SCALAR_TAGS_PER_FILE;
            case COMPRESSED_BYTES_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPRESSED_BYTES_TOTAL;
            case DECOMPRESSED_BYTES_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_DECOMPRESSED_BYTES_TOTAL;
            case COMPOUND_CONTAINERS_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPOUND_CONTAINERS_TOTAL;
            case COMPOUND_FIELD_ENTRIES_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_TOTAL;
            case LIST_ELEMENTS_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_LIST_ELEMENTS_TOTAL;
            case BYTE_ARRAY_ELEMENTS_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_TOTAL;
            case INT_ARRAY_ELEMENTS_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_TOTAL;
            case LONG_ARRAY_ELEMENTS_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_TOTAL;
            case MODIFIED_UTF8_BYTES_TOTAL ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_TOTAL;
            case SCALAR_TAGS_TOTAL -> MagicSafetyCeilings.MAX_PLAYERDATA_SCALAR_TAGS_TOTAL;
            case ATTACHMENT_ADMISSIONS ->
                    MagicSafetyCeilings.MAX_PLAYERDATA_ATTACHMENT_ADMISSIONS;
            case RAW_ROOT_CLAIMS -> MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;
        };
    }

    private Optional<Exceeded> checkpointValue(
            P4E1AuditCounter counter,
            P4E1AuditStage stage,
            long delta) {
        Objects.requireNonNull(stage, "stage");
        requireNonNegative(delta, "delta");
        var current = observed[counter.ordinal()];
        var maximum = maximum(counter);
        if (delta > maximum - current) {
            return Optional.of(Exceeded.atFirstExcess(counter, stage, maximum));
        }
        observed[counter.ordinal()] = current + delta;
        return Optional.empty();
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    record Exceeded(
            P4E1AuditCounter counter,
            P4E1AuditStage stage,
            long observedAtLeast,
            long maximum) {
        Exceeded {
            Objects.requireNonNull(counter, "counter");
            Objects.requireNonNull(stage, "stage");
            if (maximum < 0L || maximum == Long.MAX_VALUE
                    || observedAtLeast != maximum + 1L) {
                throw new IllegalArgumentException("non-canonical P4-E audit excess");
            }
        }

        private static Exceeded atFirstExcess(
                P4E1AuditCounter counter,
                P4E1AuditStage stage,
                long maximum) {
            return new Exceeded(counter, stage, maximum + 1L, maximum);
        }
    }

    enum CompressedBytesApplicability {
        UNOBSERVED,
        APPLICABLE,
        NOT_APPLICABLE
    }

    static final class FileScope {
        private final P4E1AuditBudget owner;
        private final long[] perFileObserved = new long[P4E1AuditCounter.values().length];
        private CompressedBytesApplicability compressedBytesApplicability =
                CompressedBytesApplicability.UNOBSERVED;

        private FileScope(P4E1AuditBudget owner) {
            this.owner = owner;
        }

        Optional<Exceeded> checkpointFileAndAggregate(
                P4E1AuditCounter perFileCounter,
                P4E1AuditCounter aggregateCounter,
                P4E1AuditStage perFileStage,
                P4E1AuditStage aggregateStage,
                long delta) {
            Objects.requireNonNull(perFileCounter, "perFileCounter");
            Objects.requireNonNull(aggregateCounter, "aggregateCounter");
            Objects.requireNonNull(perFileStage, "perFileStage");
            Objects.requireNonNull(aggregateStage, "aggregateStage");
            requireNonNegative(delta, "delta");
            var expectedAggregate = aggregateFor(perFileCounter);
            if (expectedAggregate != aggregateCounter) {
                throw new IllegalArgumentException("mismatched P4-E per-file/aggregate counters");
            }
            if (perFileCounter == P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE) {
                if (compressedBytesApplicability
                        == CompressedBytesApplicability.NOT_APPLICABLE) {
                    throw new IllegalStateException(
                            "compressed bytes were explicitly not applicable");
                }
                compressedBytesApplicability = CompressedBytesApplicability.APPLICABLE;
            }

            var perFileCurrent = perFileObserved[perFileCounter.ordinal()];
            var perFileMaximum = owner.maximum(perFileCounter);
            if (delta > perFileMaximum - perFileCurrent) {
                return Optional.of(Exceeded.atFirstExcess(
                        perFileCounter, perFileStage, perFileMaximum));
            }

            var aggregateCurrent = owner.observed[aggregateCounter.ordinal()];
            var aggregateMaximum = owner.maximum(aggregateCounter);
            if (delta > aggregateMaximum - aggregateCurrent) {
                return Optional.of(Exceeded.atFirstExcess(
                        aggregateCounter, aggregateStage, aggregateMaximum));
            }

            var nextPerFile = perFileCurrent + delta;
            perFileObserved[perFileCounter.ordinal()] = nextPerFile;
            owner.observed[perFileCounter.ordinal()] = Math.max(
                    owner.observed[perFileCounter.ordinal()], nextPerFile);
            owner.observed[aggregateCounter.ordinal()] = aggregateCurrent + delta;
            return Optional.empty();
        }

        Optional<Exceeded> checkpointDepth(P4E1AuditStage stage, long observedDepth) {
            Objects.requireNonNull(stage, "stage");
            requireNonNegative(observedDepth, "observedDepth");
            var counter = P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE;
            var depthMaximum = owner.maximum(counter);
            if (observedDepth > depthMaximum) {
                return Optional.of(Exceeded.atFirstExcess(counter, stage, depthMaximum));
            }
            var current = perFileObserved[counter.ordinal()];
            if (observedDepth > current) {
                perFileObserved[counter.ordinal()] = observedDepth;
                owner.observed[counter.ordinal()] = Math.max(
                        owner.observed[counter.ordinal()], observedDepth);
            }
            return Optional.empty();
        }

        void markCompressedBytesNotApplicable() {
            if (compressedBytesApplicability == CompressedBytesApplicability.APPLICABLE) {
                throw new IllegalStateException("compressed bytes were already observed");
            }
            compressedBytesApplicability = CompressedBytesApplicability.NOT_APPLICABLE;
        }

        CompressedBytesApplicability compressedBytesApplicability() {
            return compressedBytesApplicability;
        }

        long observed(P4E1AuditCounter perFileCounter) {
            requirePerFileCounter(perFileCounter);
            return perFileObserved[perFileCounter.ordinal()];
        }

        long maximum(P4E1AuditCounter counter) {
            return owner.maximum(Objects.requireNonNull(counter, "counter"));
        }

        private P4E1AuditCounter aggregateFor(P4E1AuditCounter perFileCounter) {
            return switch (perFileCounter) {
                case COMPRESSED_BYTES_PER_FILE -> P4E1AuditCounter.COMPRESSED_BYTES_TOTAL;
                case DECOMPRESSED_BYTES_PER_FILE -> P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL;
                case COMPOUND_CONTAINERS_PER_FILE ->
                        P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL;
                case COMPOUND_FIELD_ENTRIES_PER_FILE ->
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL;
                case LIST_ELEMENTS_PER_FILE -> P4E1AuditCounter.LIST_ELEMENTS_TOTAL;
                case BYTE_ARRAY_ELEMENTS_PER_FILE ->
                        P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL;
                case INT_ARRAY_ELEMENTS_PER_FILE ->
                        P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL;
                case LONG_ARRAY_ELEMENTS_PER_FILE ->
                        P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL;
                case MODIFIED_UTF8_BYTES_PER_FILE ->
                        P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL;
                case SCALAR_TAGS_PER_FILE -> P4E1AuditCounter.SCALAR_TAGS_TOTAL;
                case DIRECTORY_ENTRIES,
                        RELEVANT_RECORDS,
                        CONTAINER_DEPTH_PER_FILE,
                        COMPRESSED_BYTES_TOTAL,
                        DECOMPRESSED_BYTES_TOTAL,
                        COMPOUND_CONTAINERS_TOTAL,
                        COMPOUND_FIELD_ENTRIES_TOTAL,
                        LIST_ELEMENTS_TOTAL,
                        BYTE_ARRAY_ELEMENTS_TOTAL,
                        INT_ARRAY_ELEMENTS_TOTAL,
                        LONG_ARRAY_ELEMENTS_TOTAL,
                        MODIFIED_UTF8_BYTES_TOTAL,
                        SCALAR_TAGS_TOTAL,
                        ATTACHMENT_ADMISSIONS,
                        RAW_ROOT_CLAIMS -> throw new IllegalArgumentException(
                                "counter is not a paired per-file counter: " + perFileCounter);
            };
        }

        private void requirePerFileCounter(P4E1AuditCounter counter) {
            Objects.requireNonNull(counter, "counter");
            switch (counter) {
                case COMPRESSED_BYTES_PER_FILE,
                        DECOMPRESSED_BYTES_PER_FILE,
                        CONTAINER_DEPTH_PER_FILE,
                        COMPOUND_CONTAINERS_PER_FILE,
                        COMPOUND_FIELD_ENTRIES_PER_FILE,
                        LIST_ELEMENTS_PER_FILE,
                        BYTE_ARRAY_ELEMENTS_PER_FILE,
                        INT_ARRAY_ELEMENTS_PER_FILE,
                        LONG_ARRAY_ELEMENTS_PER_FILE,
                        MODIFIED_UTF8_BYTES_PER_FILE,
                        SCALAR_TAGS_PER_FILE -> {
                    return;
                }
                case DIRECTORY_ENTRIES,
                        RELEVANT_RECORDS,
                        COMPRESSED_BYTES_TOTAL,
                        DECOMPRESSED_BYTES_TOTAL,
                        COMPOUND_CONTAINERS_TOTAL,
                        COMPOUND_FIELD_ENTRIES_TOTAL,
                        LIST_ELEMENTS_TOTAL,
                        BYTE_ARRAY_ELEMENTS_TOTAL,
                        INT_ARRAY_ELEMENTS_TOTAL,
                        LONG_ARRAY_ELEMENTS_TOTAL,
                        MODIFIED_UTF8_BYTES_TOTAL,
                        SCALAR_TAGS_TOTAL,
                        ATTACHMENT_ADMISSIONS,
                        RAW_ROOT_CLAIMS -> throw new IllegalArgumentException(
                                "counter is not per-file: " + counter);
            }
        }
    }
}
