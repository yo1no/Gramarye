package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.SkillRetentionRootSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Research-only executable owner for the approved R2Q checkpoint order.
 *
 * <p>This is not a production playerdata scanner and none of its coordinates are production
 * limits. It exists so R2Q-A does not reduce the locked profile to disconnected numeric facts:
 * directory/source selection, compressed/decompressed ingress, wire-NBT checkpoints, P4-C
 * admission, raw-root capture, and Store audit all publish through one bounded state machine.</p>
 */
final class P4E0R2QAuditBudget {
    private final P4E0R2QProfile profile;
    private final P4E0ResearchWireNbt.AggregateCheckpointBudget structural;
    private long directoryEntries;
    private long relevantRecords;
    private long compressedBytes;
    private long decompressedBytes;
    private long attachmentAdmissions;
    private long rawRootClaims;
    private boolean journalReady;

    P4E0R2QAuditBudget() {
        this(P4E0R2QProfile.locked());
    }

    private P4E0R2QAuditBudget(P4E0R2QProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.structural = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                aggregateCheckpointLimits(profile));
    }

    void requireJournalReady(boolean ready) throws AuditFailure {
        if (!ready) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.JOURNAL_READINESS,
                    FailureCode.JOURNAL_UNAVAILABLE);
        }
        journalReady = true;
    }

    void observeDirectoryEntries(long occurrences) throws AuditFailure {
        requireJournalState();
        directoryEntries = checkedAdd(
                directoryEntries,
                occurrences,
                P4E0R2QProfile.Counter.DIRECTORY_ENTRIES,
                P4E0R2QCasePlan.FailureStage.DIRECTORY_ENTRIES);
    }

    FileScope select(SourceSelection selection, long compressedFileBytes)
            throws AuditFailure {
        requireJournalState();
        Objects.requireNonNull(selection, "selection");
        if (selection == SourceSelection.INVALID) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.SOURCE_SELECTION,
                    FailureCode.SOURCE_SELECTION_INVALID);
        }
        relevantRecords = checkedAdd(
                relevantRecords,
                1L,
                P4E0R2QProfile.Counter.RELEVANT_RECORDS,
                P4E0R2QCasePlan.FailureStage.RELEVANT_RECORDS);
        if (compressedFileBytes < 0) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.PER_FILE_COMPRESSED,
                    FailureCode.FIXTURE_INVALID);
        }
        var perFileMaximum = profile.maximum(
                P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE);
        if (compressedFileBytes > perFileMaximum) {
            throw AuditFailure.capacity(
                    P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE,
                    P4E0R2QCasePlan.FailureStage.PER_FILE_COMPRESSED,
                    compressedFileBytes,
                    perFileMaximum);
        }
        compressedBytes = checkedAdd(
                compressedBytes,
                compressedFileBytes,
                P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL,
                P4E0R2QCasePlan.FailureStage.AGGREGATE_COMPRESSED);
        return new FileScope();
    }

    DataVersionObservation observeDataVersion(
            P4E0ResearchWireNbt.DataVersionFacts dataVersion,
            DfuInvocationProbe dfuProbe) throws AuditFailure {
        Objects.requireNonNull(dataVersion, "dataVersion");
        Objects.requireNonNull(dfuProbe, "dfuProbe");
        if (dataVersion.kind() == P4E0ResearchWireNbt.DataVersionKind.MISSING) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.DATA_VERSION,
                    FailureCode.DATA_VERSION_MISSING);
        }
        if (dataVersion.kind() != P4E0ResearchWireNbt.DataVersionKind.INT_TAG) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.DATA_VERSION,
                    FailureCode.DATA_VERSION_WRONG_TYPE);
        }
        if (dataVersion.intValue() != profile.acceptedDataVersion()) {
            // V0 accepts no DFU records. The probe is intentionally not invoked.
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.DATA_VERSION,
                    FailureCode.DATA_VERSION_WRONG_VALUE);
        }
        return new DataVersionObservation(dataVersion.intValue(), dfuProbe.invocations());
    }

    void observeAttachmentAdmission(P4E0ResearchAttachmentFixtures.Variant variant)
            throws AuditFailure {
        // A production total-admission result is always non-null.  Keep a null result as a
        // research-only negative control at the P4-C checkpoint instead of allowing it to fall
        // through to the later multiplicity counter or masquerade as a missing Attachment.
        if (variant == null) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.P4C_ADMISSION,
                    FailureCode.P4C_ADMISSION_FAILED);
        }
        attachmentAdmissions = checkedAdd(
                attachmentAdmissions,
                1L,
                P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS,
                P4E0R2QCasePlan.FailureStage.ATTACHMENT_ADMISSION_COUNTER);
    }

    SkillRetentionRootSnapshot captureRawRoots(List<SkillReference> references)
            throws AuditFailure {
        Objects.requireNonNull(references, "references");
        var maximum = profile.maximum(P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS);
        var observed = references.size();
        if (observed > maximum) {
            rawRootClaims = Math.addExact(maximum, 1L);
            throw AuditFailure.capacity(
                    P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS,
                    P4E0R2QCasePlan.FailureStage.RAW_ROOT_CAPTURE,
                    rawRootClaims,
                    maximum);
        }
        rawRootClaims = observed;
        return SkillRetentionRootSnapshot.fromCompleteRoots(references);
    }

    void requireStoreAudit(boolean audited) throws AuditFailure {
        if (!audited) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.STORE_REFERENCE_OWNER_AUDIT,
                    FailureCode.STORE_AUDIT_FAILED);
        }
    }

    AuditFailure translateStructuralFailure(
            String coordinate, P4E0R2QCasePlan.FailureStage eventStage) {
        Objects.requireNonNull(coordinate, "coordinate");
        Objects.requireNonNull(eventStage, "eventStage");
        var counter = switch (coordinate) {
            case "compressed_bytes", "input_bytes" ->
                    P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE;
            case "decompressed_bytes" ->
                    P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE;
            case "compound_containers_per_file" ->
                    P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE;
            case "compound_containers_total" ->
                    P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL;
            case "compound_field_entries_per_file" ->
                    P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE;
            case "compound_field_entries_total" ->
                    P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL;
            case "list_elements_per_file" ->
                    P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE;
            case "list_elements_total" ->
                    P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL;
            case "byte_array_elements_per_file" ->
                    P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE;
            case "byte_array_elements_total" ->
                    P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL;
            case "int_array_elements_per_file" ->
                    P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE;
            case "int_array_elements_total" ->
                    P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL;
            case "long_array_elements_per_file" ->
                    P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE;
            case "long_array_elements_total" ->
                    P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL;
            case "modified_utf8_bytes_per_file" ->
                    P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE;
            case "modified_utf8_bytes_total" ->
                    P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL;
            case "scalar_tags_per_file" ->
                    P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE;
            case "scalar_tags_total" ->
                    P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL;
            case "container_depth_per_file" ->
                    P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE;
            default -> null;
        };
        if (counter == null) {
            return AuditFailure.nonCounter(
                    eventStage,
                    FailureCode.FIXTURE_PARSER_INVALID);
        }
        return AuditFailure.capacity(
                counter,
                P4E0R2QCasePlan.stageFor(counter),
                Math.addExact(profile.maximum(counter), 1L),
                profile.maximum(counter));
    }

    AuditFailure translateGzipFramingFailure() {
        return AuditFailure.nonCounter(
                P4E0R2QCasePlan.FailureStage.GZIP_FRAMING,
                FailureCode.GZIP_FRAMING_INVALID);
    }

    AuditFailure translateFixtureParserFailure(P4E0R2QCasePlan.FailureStage stage) {
        Objects.requireNonNull(stage, "stage");
        return AuditFailure.nonCounter(stage, FailureCode.FIXTURE_PARSER_INVALID);
    }

    Facts facts() {
        return new Facts(
                directoryEntries,
                relevantRecords,
                compressedBytes,
                decompressedBytes,
                structural.observed(),
                attachmentAdmissions,
                rawRootClaims,
                journalReady);
    }

    private long checkedAdd(
            long current,
            long increment,
            P4E0R2QProfile.Counter counter,
            P4E0R2QCasePlan.FailureStage stage) throws AuditFailure {
        if (increment < 0) {
            throw new IllegalArgumentException("negative R2Q audit increment");
        }
        final long observed;
        try {
            observed = Math.addExact(current, increment);
        } catch (ArithmeticException exception) {
            throw AuditFailure.capacity(counter, stage, Long.MAX_VALUE, profile.maximum(counter));
        }
        var maximum = profile.maximum(counter);
        if (observed > maximum) {
            throw AuditFailure.capacity(counter, stage, observed, maximum);
        }
        return observed;
    }

    private void requireJournalState() throws AuditFailure {
        if (!journalReady) {
            throw AuditFailure.nonCounter(
                    P4E0R2QCasePlan.FailureStage.JOURNAL_READINESS,
                    FailureCode.JOURNAL_UNAVAILABLE);
        }
    }

    private static P4E0ResearchWireNbt.CheckpointLimits perFileCheckpointLimits(
            P4E0R2QProfile profile) {
        return new P4E0ResearchWireNbt.CheckpointLimits(
                Math.toIntExact(profile.maximum(
                        P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE)),
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE),
                profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE));
    }

    private static P4E0ResearchWireNbt.CheckpointLimits aggregateCheckpointLimits(
            P4E0R2QProfile profile) {
        return new P4E0ResearchWireNbt.CheckpointLimits(
                Math.toIntExact(profile.maximum(
                        P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE)),
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL),
                profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL));
    }

    enum SourceSelection {
        PRIMARY,
        OLD,
        INVALID
    }

    enum FailureCode {
        JOURNAL_UNAVAILABLE,
        SOURCE_SELECTION_INVALID,
        COUNTER_CAPACITY_EXCEEDED,
        DATA_VERSION_MISSING,
        DATA_VERSION_WRONG_TYPE,
        DATA_VERSION_WRONG_VALUE,
        GZIP_FRAMING_INVALID,
        FIXTURE_PARSER_INVALID,
        P4C_ADMISSION_FAILED,
        STORE_AUDIT_FAILED,
        FIXTURE_INVALID
    }

    static final class DfuInvocationProbe {
        private final AtomicInteger invocations = new AtomicInteger();

        void invoke() {
            invocations.incrementAndGet();
        }

        int invocations() {
            return invocations.get();
        }
    }

    final class FileScope {
        private long decompressed;

        private FileScope() {
        }

        P4E0ResearchWireNbt.ScanLimits scanLimits() {
            return new P4E0ResearchWireNbt.ScanLimits(
                    profile.maximum(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE),
                    profile.maximum(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE),
                    profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE)
                            + profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE)
                            + 4_096L,
                    Math.toIntExact(profile.maximum(
                            P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE)),
                    Math.max(
                            profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE),
                            Math.max(
                                    profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE),
                                    profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE))),
                    profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE));
        }

        P4E0ResearchWireNbt.CheckpointLimits checkpointLimits() {
            return perFileCheckpointLimits(profile);
        }

        P4E0ResearchWireNbt.AggregateCheckpointBudget aggregateCheckpoints() {
            return structural;
        }

        void observeDecompressed(long increment) throws AuditFailure {
            decompressed = checkedAdd(
                    decompressed,
                    increment,
                    P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE,
                    P4E0R2QCasePlan.FailureStage.PER_FILE_DECOMPRESSED);
            decompressedBytes = checkedAdd(
                    decompressedBytes,
                    increment,
                    P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL,
                    P4E0R2QCasePlan.FailureStage.AGGREGATE_DECOMPRESSED);
        }
    }

    static final class AuditFailure extends IOException {
        private final Optional<P4E0R2QProfile.Counter> counter;
        private final P4E0R2QCasePlan.FailureStage stage;
        private final FailureCode code;
        private final long observedAtLeast;
        private final long maximum;

        private AuditFailure(
                Optional<P4E0R2QProfile.Counter> counter,
                P4E0R2QCasePlan.FailureStage stage,
                FailureCode code,
                long observedAtLeast,
                long maximum) {
            super("R2Q audit incomplete");
            this.counter = Objects.requireNonNull(counter, "counter");
            this.stage = Objects.requireNonNull(stage, "stage");
            this.code = Objects.requireNonNull(code, "code");
            this.observedAtLeast = observedAtLeast;
            this.maximum = maximum;
        }

        static AuditFailure capacity(
                P4E0R2QProfile.Counter counter,
                P4E0R2QCasePlan.FailureStage stage,
                long observedAtLeast,
                long maximum) {
            return new AuditFailure(
                    Optional.of(counter),
                    stage,
                    FailureCode.COUNTER_CAPACITY_EXCEEDED,
                    observedAtLeast,
                    maximum);
        }

        static AuditFailure nonCounter(
                P4E0R2QCasePlan.FailureStage stage, FailureCode code) {
            return new AuditFailure(Optional.empty(), stage, code, 0L, 0L);
        }

        Optional<P4E0R2QProfile.Counter> counter() {
            return counter;
        }

        P4E0R2QCasePlan.FailureStage stage() {
            return stage;
        }

        FailureCode code() {
            return code;
        }

        long observedAtLeast() {
            return observedAtLeast;
        }

        long maximum() {
            return maximum;
        }
    }

    record DataVersionObservation(int acceptedValue, int dfuInvocations) {
    }

    record Facts(
            long directoryEntries,
            long relevantRecords,
            long compressedBytes,
            long decompressedBytes,
            P4E0ResearchWireNbt.CheckpointFacts structural,
            long attachmentAdmissions,
            long rawRootClaims,
            boolean journalReady) {
        Facts {
            Objects.requireNonNull(structural, "structural");
        }
    }
}
