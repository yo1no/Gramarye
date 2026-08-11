package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/** Bounded public diagnostics for one synchronous P4-E1 root-audit attempt. */
public sealed abstract class SkillRetentionRootAuditResult
        permits SkillRetentionRootAuditResult.Complete,
                SkillRetentionRootAuditResult.Incomplete,
                SkillRetentionRootAuditResult.OverLimit,
                SkillRetentionRootAuditResult.ReconciliationRequired {
    private final AuditSummary summary;

    private SkillRetentionRootAuditResult(AuditSummary summary) {
        this.summary = Objects.requireNonNull(summary, "summary");
    }

    /** Returns bounded counts only; no root, source, Store, or runtime witness is exposed. */
    public final AuditSummary summary() {
        return summary;
    }

    static Complete complete(AuditSummary summary, CompleteAuthority authority) {
        return new Complete(summary, new PermitShell(authority));
    }

    /** Successful diagnostics plus a non-public, single-use authority shell. */
    public static final class Complete extends SkillRetentionRootAuditResult {
        private PermitShell permit;

        private Complete(AuditSummary summary, PermitShell permit) {
            super(summary);
            this.permit = Objects.requireNonNull(permit, "permit");
        }

        CompleteAuthority claimAuthority() {
            var current = permit;
            if (current == null) {
                throw new IllegalStateException("P4E1_COMPLETE_ALREADY_CONSUMED");
            }
            permit = null;
            return current.claim();
        }

        @Override
        public String toString() {
            return "Complete[summary=" + summary() + "]";
        }
    }

    /** Bounded non-capacity terminal result. */
    public static final class Incomplete extends SkillRetentionRootAuditResult {
        private final IncompleteReason reason;
        private final Diagnostic diagnostic;

        public Incomplete(
                IncompleteReason reason, Diagnostic diagnostic, AuditSummary summary) {
            super(summary);
            this.reason = Objects.requireNonNull(reason, "reason");
            this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        }

        public IncompleteReason reason() {
            return reason;
        }

        public Diagnostic diagnostic() {
            return diagnostic;
        }

        @Override
        public String toString() {
            return "Incomplete[reason=" + reason + ", diagnostic=" + diagnostic
                    + ", summary=" + summary() + "]";
        }
    }

    /** The canonical raw-root ceiling was exceeded before publication. */
    public static final class OverLimit extends SkillRetentionRootAuditResult {
        private final Counter counter;
        private final Stage stage;
        private final long observedAtLeast;
        private final long maximum;

        public OverLimit(
                Counter counter,
                Stage stage,
                long observedAtLeast,
                long maximum,
                AuditSummary summary) {
            super(summary);
            this.counter = Objects.requireNonNull(counter, "counter");
            this.stage = Objects.requireNonNull(stage, "stage");
            if (counter != Counter.RAW_ROOT_CLAIMS || stage != Stage.RAW_ROOT_CAPTURE) {
                throw new IllegalArgumentException("over-limit is reserved for raw root claims");
            }
            if (maximum < 0L || maximum == Long.MAX_VALUE
                    || observedAtLeast != maximum + 1L) {
                throw new IllegalArgumentException("non-canonical over-limit diagnostic");
            }
            this.observedAtLeast = observedAtLeast;
            this.maximum = maximum;
        }

        public Counter counter() {
            return counter;
        }

        public Stage stage() {
            return stage;
        }

        public long observedAtLeast() {
            return observedAtLeast;
        }

        public long maximum() {
            return maximum;
        }

        @Override
        public String toString() {
            return "OverLimit[counter=" + counter + ", stage=" + stage
                    + ", observedAtLeast=" + observedAtLeast + ", maximum=" + maximum
                    + ", summary=" + summary() + "]";
        }
    }

    /** The first raw-order player claim requiring future P4-E2 reconciliation. */
    public static final class ReconciliationRequired extends SkillRetentionRootAuditResult {
        private final ReconciliationReason reason;
        private final Disposition disposition;
        private final int staleObservedAtLeast;
        private final UUID sourceIdentity;

        public ReconciliationRequired(
                ReconciliationReason reason,
                Disposition disposition,
                int staleObservedAtLeast,
                UUID sourceIdentity,
                AuditSummary summary) {
            super(summary);
            this.reason = Objects.requireNonNull(reason, "reason");
            this.disposition = Objects.requireNonNull(disposition, "disposition");
            if (staleObservedAtLeast != 1) {
                throw new IllegalArgumentException("staleObservedAtLeast must be exactly one");
            }
            this.staleObservedAtLeast = staleObservedAtLeast;
            this.sourceIdentity = Objects.requireNonNull(sourceIdentity, "sourceIdentity");
        }

        public ReconciliationReason reason() {
            return reason;
        }

        public Disposition disposition() {
            return disposition;
        }

        public int staleObservedAtLeast() {
            return staleObservedAtLeast;
        }

        public UUID sourceIdentity() {
            return sourceIdentity;
        }

        @Override
        public String toString() {
            return "ReconciliationRequired[reason=" + reason + ", disposition=" + disposition
                    + ", staleObservedAtLeast=" + staleObservedAtLeast
                    + ", sourceIdentity=" + sourceIdentity + ", summary=" + summary() + "]";
        }
    }

    /** Exact optional counters established before the terminal result. */
    public record AuditSummary(
            OptionalLong indexGeneration,
            OptionalInt selectedOwnerCount,
            OptionalInt onlineOwnerCount,
            OptionalInt integratedOwnerCount,
            OptionalInt diskOwnerCount,
            OptionalInt playerRootClaimCount,
            OptionalInt journalRootClaimCount,
            OptionalInt totalRawRootClaimCount,
            OptionalInt distinctSkillIdCount,
            OptionalInt auditedValidClaimCount,
            OptionalInt sourceCount) {
        public AuditSummary {
            indexGeneration = requireNonNegative(indexGeneration, "indexGeneration");
            selectedOwnerCount = requireNonNegative(selectedOwnerCount, "selectedOwnerCount");
            onlineOwnerCount = requireNonNegative(onlineOwnerCount, "onlineOwnerCount");
            integratedOwnerCount = requireNonNegative(
                    integratedOwnerCount, "integratedOwnerCount");
            diskOwnerCount = requireNonNegative(diskOwnerCount, "diskOwnerCount");
            playerRootClaimCount = requireNonNegative(
                    playerRootClaimCount, "playerRootClaimCount");
            journalRootClaimCount = requireNonNegative(
                    journalRootClaimCount, "journalRootClaimCount");
            totalRawRootClaimCount = requireNonNegative(
                    totalRawRootClaimCount, "totalRawRootClaimCount");
            distinctSkillIdCount = requireNonNegative(
                    distinctSkillIdCount, "distinctSkillIdCount");
            auditedValidClaimCount = requireNonNegative(
                    auditedValidClaimCount, "auditedValidClaimCount");
            sourceCount = requireNonNegative(sourceCount, "sourceCount");
        }

        static AuditSummary generationOnly(long generation) {
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be non-negative");
            }
            return new AuditSummary(
                    OptionalLong.of(generation),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty(),
                    OptionalInt.empty());
        }

        private static OptionalLong requireNonNegative(OptionalLong value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isPresent() && value.getAsLong() < 0L) {
                throw new IllegalArgumentException(name + " must be non-negative when present");
            }
            return value;
        }

        private static OptionalInt requireNonNegative(OptionalInt value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isPresent() && value.getAsInt() < 0) {
                throw new IllegalArgumentException(name + " must be non-negative when present");
            }
            return value;
        }
    }

    /** Bounded operational metadata; absence is never represented as a fabricated zero. */
    public record Diagnostic(
            Stage stage,
            Optional<Counter> counter,
            OptionalLong observedAtLeast,
            OptionalLong maximum,
            OptionalInt ordinal,
            Optional<UUID> playerId,
            String exceptionClassName) {
        public Diagnostic {
            Objects.requireNonNull(stage, "stage");
            counter = Objects.requireNonNull(counter, "counter");
            observedAtLeast = AuditSummary.requireNonNegative(
                    observedAtLeast, "observedAtLeast");
            maximum = AuditSummary.requireNonNegative(maximum, "maximum");
            ordinal = AuditSummary.requireNonNegative(ordinal, "ordinal");
            playerId = Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(exceptionClassName, "exceptionClassName");
            if (!exceptionClassName.equals(
                    P4E1SourceFailure.boundedExceptionClassName(exceptionClassName))) {
                throw new IllegalArgumentException("exception class name is not bounded");
            }
            if (counter.isPresent()
                    != (observedAtLeast.isPresent() && maximum.isPresent())) {
                throw new IllegalArgumentException("capacity diagnostic fields must co-occur");
            }
        }

        static Diagnostic simple(Stage stage) {
            return new Diagnostic(
                    stage,
                    Optional.empty(),
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    OptionalInt.empty(),
                    Optional.empty(),
                    "");
        }
    }

    public enum IncompleteReason {
        HEAP_FLOOR_NOT_MET,
        HEAP_FLOOR_UNVERIFIABLE,
        STORE_UNAVAILABLE,
        JOURNAL_NOT_READY,
        JOURNAL_UNAVAILABLE,
        JOURNAL_TARGET_INVALID,
        INVENTORY_PROVIDER_MISSING,
        COUNTER_CAPACITY_EXCEEDED,
        DIRECTORY_UNREADABLE,
        DIRECTORY_TYPE_UNSUPPORTED,
        DIRECTORY_IDENTITY_UNAVAILABLE,
        DIRECTORY_RACE_DETECTED,
        PLAYERDATA_NAME_NONCANONICAL,
        PRIMARY_FILE_UNREADABLE,
        PRIMARY_FILE_TYPE_UNSUPPORTED,
        PRIMARY_FILE_IDENTITY_UNAVAILABLE,
        PRIMARY_FILE_RACE_DETECTED,
        PLATFORM_READ_FAILURE_PROVEN,
        STRICT_GZIP_REJECTED,
        STRICT_NBT_REJECTED,
        DATA_VERSION_MISSING,
        DATA_VERSION_WRONG_TYPE,
        DATA_VERSION_NOT_CURRENT,
        ATTACHMENT_ADMISSION_REJECTED,
        ATTACHMENT_QUARANTINED,
        INTEGRATED_OWNER_IDENTITY_UNAVAILABLE,
        INTEGRATED_OWNER_FRESHNESS_LOST,
        ONLINE_SOURCE_FRESHNESS_LOST,
        SERVER_FRESHNESS_LOST,
        CALL_CHAIN_FRESHNESS_LOST,
        INDEX_RESERVATION_LOST,
        STORE_SOURCE_FRESHNESS_LOST,
        JOURNAL_FRESHNESS_LOST,
        JOURNAL_TARGET_PROOF_LOST,
        INVENTORY_PROVIDER_FRESHNESS_LOST,
        SELECTED_FILE_FRESHNESS_LOST,
        GENERATION_EXHAUSTED,
        INTERNAL_RUNTIME_FAILURE
    }

    public enum ReconciliationReason {
        STORE_REFERENCE_MISSING,
        STORE_OWNER_MISMATCH
    }

    public enum Disposition {
        ONLINE,
        DEFERRED_INTEGRATED,
        DEFERRED_OFFLINE
    }

    public enum Counter {
        DIRECTORY_ENTRIES,
        RELEVANT_RECORDS,
        COMPRESSED_BYTES_PER_FILE,
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
        RAW_ROOT_CLAIMS
    }

    public enum Stage {
        HEAP_FLOOR_OBSERVATION,
        JOURNAL_READINESS,
        DIRECTORY_ENTRIES,
        SOURCE_SELECTION,
        RELEVANT_RECORDS,
        PER_FILE_COMPRESSED,
        AGGREGATE_COMPRESSED_CHECKED_ADD,
        GZIP_FRAMING,
        PER_FILE_DECOMPRESSED,
        AGGREGATE_DECOMPRESSED_CHECKED_ADD,
        COMPOUND_FIELD_CHECKPOINT,
        DEPTH_CONTAINER_SCALAR_KIND,
        LIST_LENGTH,
        TYPED_ARRAY_LENGTH,
        MODIFIED_UTF_PREFIX,
        DATA_VERSION,
        P4C_ADMISSION,
        ATTACHMENT_ADMISSION_COUNTER,
        RAW_ROOT_CAPTURE,
        STORE_REFERENCE_OWNER_AUDIT,
        FINAL_FRESHNESS,
        INDEX_PUBLICATION
    }

    interface CompleteAuthority {
    }

    private static final class PermitShell {
        private CompleteAuthority authority;
        private boolean consumed;

        private PermitShell(CompleteAuthority authority) {
            this.authority = Objects.requireNonNull(authority, "authority");
        }

        private CompleteAuthority claim() {
            if (consumed || authority == null) {
                throw new IllegalStateException("P4E1_COMPLETE_ALREADY_CONSUMED");
            }
            consumed = true;
            var current = authority;
            authority = null;
            return current;
        }
    }
}
