package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.SkillRetentionRootSnapshot;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable proof that one bounded R2Q state machine owns the approved checkpoint order. */
final class P4E0ResearchR2QAuditBudgetTest {
    @TempDir
    Path temporary;

    @Test
    void reducedPositiveTraversesEveryQualificationStageWithoutDfu() throws Exception {
        var path = temporary.resolve("positive.dat");
        writeDataVersion(path, DataVersionShape.CANONICAL);
        var budget = new P4E0R2QAuditBudget();
        budget.requireJournalReady(true);
        budget.observeDirectoryEntries(1L);
        var scanned = P4E0ResearchWireNbt.scan(
                path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
        var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
        var accepted = budget.observeDataVersion(scanned.dataVersion(), dfu);
        var attachment = P4E0ResearchAttachmentFixtures.readyRootMax(false);
        budget.observeAttachmentAdmission(attachment.variant());
        var roots = budget.captureRawRoots(attachment.projectedRoots().orElseThrow());
        budget.requireStoreAudit(true);

        var complete = assertInstanceOf(SkillRetentionRootSnapshot.Complete.class, roots);
        var facts = budget.facts();
        assertEquals(3_955, accepted.acceptedValue());
        assertEquals(0, accepted.dfuInvocations());
        assertEquals(0, dfu.invocations());
        assertEquals(320, complete.roots().size());
        assertEquals(1L, facts.directoryEntries());
        assertEquals(1L, facts.relevantRecords());
        assertEquals(scanned.physicalBytes(), facts.compressedBytes());
        assertEquals(scanned.decompressedBytes(), facts.decompressedBytes());
        assertEquals(1L, facts.attachmentAdmissions());
        assertEquals(320L, facts.rawRootClaims());
        assertTrue(facts.journalReady());
    }

    @Test
    void journalDirectorySourceAndRelevantPrecedenceAreDistinctAndBounded()
            throws Exception {
        var beforeJournal = new P4E0R2QAuditBudget();
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> beforeJournal.observeDirectoryEntries(1L)),
                P4E0R2QCasePlan.FailureStage.JOURNAL_READINESS,
                null);

        var directory = readyBudget();
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> directory.observeDirectoryEntries(4_097L)),
                P4E0R2QCasePlan.FailureStage.DIRECTORY_ENTRIES,
                P4E0R2QProfile.Counter.DIRECTORY_ENTRIES);

        var source = readyBudget();
        source.observeDirectoryEntries(1L);
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> source.select(P4E0R2QAuditBudget.SourceSelection.INVALID, 0L)),
                P4E0R2QCasePlan.FailureStage.SOURCE_SELECTION,
                null);

        var relevant = readyBudget();
        relevant.observeDirectoryEntries(4_096L);
        for (var index = 0; index < 2_048; index++) {
            relevant.select(P4E0R2QAuditBudget.SourceSelection.PRIMARY, 0L);
        }
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> relevant.select(
                                P4E0R2QAuditBudget.SourceSelection.OLD, 0L)),
                P4E0R2QCasePlan.FailureStage.RELEVANT_RECORDS,
                P4E0R2QProfile.Counter.RELEVANT_RECORDS);
    }

    @Test
    void compressedAndDecompressedChecksUsePerFileBeforeAggregate() throws Exception {
        var profile = P4E0R2QProfile.locked();
        var compressedPerFile = readyBudget();
        compressedPerFile.observeDirectoryEntries(1L);
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> compressedPerFile.select(
                                P4E0R2QAuditBudget.SourceSelection.PRIMARY,
                                profile.maximum(
                                        P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE) + 1L)),
                P4E0R2QCasePlan.FailureStage.PER_FILE_COMPRESSED,
                P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE);

        var compressedTotal = readyBudget();
        compressedTotal.observeDirectoryEntries(9L);
        var perFile = profile.maximum(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE);
        var remaining = profile.maximum(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL);
        while (remaining > 0) {
            var accepted = Math.min(perFile, remaining);
            compressedTotal.select(P4E0R2QAuditBudget.SourceSelection.PRIMARY, accepted);
            remaining -= accepted;
        }
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> compressedTotal.select(
                                P4E0R2QAuditBudget.SourceSelection.PRIMARY, 1L)),
                P4E0R2QCasePlan.FailureStage.AGGREGATE_COMPRESSED,
                P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL);

        var decompressedPerFile = readyBudget();
        decompressedPerFile.observeDirectoryEntries(1L);
        var perFileScope = decompressedPerFile.select(
                P4E0R2QAuditBudget.SourceSelection.PRIMARY, 0L);
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> perFileScope.observeDecompressed(
                                profile.maximum(
                                        P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE) + 1L)),
                P4E0R2QCasePlan.FailureStage.PER_FILE_DECOMPRESSED,
                P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE);

        var decompressedTotal = readyBudget();
        decompressedTotal.observeDirectoryEntries(3L);
        for (var index = 0; index < 2; index++) {
            decompressedTotal.select(P4E0R2QAuditBudget.SourceSelection.PRIMARY, 0L)
                    .observeDecompressed(profile.maximum(
                            P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE));
        }
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> decompressedTotal
                                .select(P4E0R2QAuditBudget.SourceSelection.PRIMARY, 0L)
                                .observeDecompressed(1L)),
                P4E0R2QCasePlan.FailureStage.AGGREGATE_DECOMPRESSED,
                P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL);
    }

    @Test
    void typedArrayLengthWinsBeforeTruncatedPayloadAndLegacyGenericGuard() throws Exception {
        var path = temporary.resolve("byte-array-over.dat");
        P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.BEST_COMPRESSION,
                1_024L,
                128L,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("b");
                    output.writeInt(268_435_385);
                    // Deliberately absent payload: the approved length checkpoint must win.
                    output.writeByte(Tag.TAG_END);
                });
        var budget = readyBudget();
        budget.observeDirectoryEntries(1L);
        var failure = assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                () -> P4E0ResearchWireNbt.scan(
                        path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY));
        assertFailure(
                failure,
                P4E0R2QCasePlan.FailureStage.TYPED_ARRAY_LENGTH,
                P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE);
        assertEquals(268_435_385L, failure.observedAtLeast());
    }

    @Test
    void allDataVersionControlsFailBeforeDfuInvocation() throws Exception {
        for (var shape : new DataVersionShape[] {
                DataVersionShape.MISSING,
                DataVersionShape.WRONG_TYPE,
                DataVersionShape.WRONG_VALUE
        }) {
            var path = temporary.resolve(shape.name().toLowerCase() + ".dat");
            writeDataVersion(path, shape);
            var budget = readyBudget();
            budget.observeDirectoryEntries(1L);
            var scanned = P4E0ResearchWireNbt.scan(
                    path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
            var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
            var failure = assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                    () -> budget.observeDataVersion(scanned.dataVersion(), dfu));
            assertEquals(P4E0R2QCasePlan.FailureStage.DATA_VERSION, failure.stage());
            assertEquals(0, dfu.invocations());
            assertFalse(failure.getMessage().contains(path.toString()));
        }
    }

    @Test
    void attachmentRootAndStoreStagesStayDistinctFromMissingData() throws Exception {
        var budget = readyBudget();
        for (var index = 0; index < 1_024; index++) {
            budget.observeAttachmentAdmission(P4E0ResearchAttachmentFixtures.Variant.READY);
        }
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> budget.observeAttachmentAdmission(
                                P4E0ResearchAttachmentFixtures.Variant.READY)),
                P4E0R2QCasePlan.FailureStage.ATTACHMENT_ADMISSION_COUNTER,
                P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS);

        var reference = P4E0ResearchAttachmentFixtures.readyRootMax(false)
                .projectedRoots().orElseThrow().getFirst();
        var overRoots = Collections.nCopies(65_537, reference);
        var roots = readyBudget();
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> roots.captureRawRoots(overRoots)),
                P4E0R2QCasePlan.FailureStage.RAW_ROOT_CAPTURE,
                P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS);

        var store = readyBudget();
        assertFailure(
                assertThrows(P4E0R2QAuditBudget.AuditFailure.class,
                        () -> store.requireStoreAudit(false)),
                P4E0R2QCasePlan.FailureStage.STORE_REFERENCE_OWNER_AUDIT,
                null);
    }

    @Test
    void malformedGzipIsBoundedAtFramingBeforeAnyWireParserCheckpoint()
            throws Exception {
        var path = temporary.resolve("malformed-gzip.dat");
        Files.write(path, new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        var budget = assertGzipFailure(path);
        assertEquals(
                new P4E0ResearchWireNbt.CheckpointFacts(
                        0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L),
                budget.facts().structural());

        var truncatedTrailer = temporary.resolve("truncated-gzip-trailer.dat");
        writeDataVersion(truncatedTrailer, DataVersionShape.CANONICAL);
        try (var channel = FileChannel.open(truncatedTrailer, StandardOpenOption.WRITE)) {
            channel.truncate(Math.subtractExact(channel.size(), 4L));
        }
        assertGzipFailure(truncatedTrailer);
    }

    @Test
    void fixtureParserClassificationTracksTheActualWireEventCheckpoint()
            throws Exception {
        assertParserFailure(
                "root",
                P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND,
                output -> {
                    output.writeByte(Tag.TAG_INT);
                    output.writeShort(0);
                    output.writeInt(1);
                });
        assertParserFailure(
                "field-name-modified-utf",
                P4E0R2QCasePlan.FailureStage.MODIFIED_UTF_PREFIX,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeShort(2);
                    output.writeByte(0xc2);
                    output.writeByte(0x20);
                    output.writeInt(1);
                    output.writeByte(Tag.TAG_END);
                });
        assertParserFailure(
                "duplicate-field",
                P4E0R2QCasePlan.FailureStage.MODIFIED_UTF_PREFIX,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("duplicate");
                    output.writeInt(1);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("duplicate");
                    output.writeInt(2);
                    output.writeByte(Tag.TAG_END);
                });
        assertParserFailure(
                "list-length",
                P4E0R2QCasePlan.FailureStage.LIST_LENGTH,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("l");
                    output.writeByte(Tag.TAG_INT);
                    output.writeInt(-1);
                    output.writeByte(Tag.TAG_END);
                });
        assertParserFailure(
                "typed-array-payload",
                P4E0R2QCasePlan.FailureStage.TYPED_ARRAY_LENGTH,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("b");
                    output.writeInt(2);
                    output.writeByte(1);
                });
        assertParserFailure(
                "data-version-payload",
                P4E0R2QCasePlan.FailureStage.DATA_VERSION,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("DataVersion");
                    output.writeShort(1);
                });
    }

    @Test
    void p4cTotalityFailurePrecedesTheAdmissionMultiplicityCounter()
            throws Exception {
        var budget = readyBudget();
        for (var index = 0; index < 1_024; index++) {
            budget.observeAttachmentAdmission(P4E0ResearchAttachmentFixtures.Variant.READY);
        }

        var totality = assertThrows(
                P4E0R2QAuditBudget.AuditFailure.class,
                () -> budget.observeAttachmentAdmission(null));
        assertBoundedNonCounterFailure(
                totality,
                P4E0R2QCasePlan.FailureStage.P4C_ADMISSION,
                P4E0R2QAuditBudget.FailureCode.P4C_ADMISSION_FAILED);
        assertEquals(1_024L, budget.facts().attachmentAdmissions());

        var counter = assertThrows(
                P4E0R2QAuditBudget.AuditFailure.class,
                () -> budget.observeAttachmentAdmission(
                        P4E0ResearchAttachmentFixtures.Variant.READY));
        assertFailure(
                counter,
                P4E0R2QCasePlan.FailureStage.ATTACHMENT_ADMISSION_COUNTER,
                P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS);
    }

    private static P4E0R2QAuditBudget readyBudget() throws Exception {
        var budget = new P4E0R2QAuditBudget();
        budget.requireJournalReady(true);
        return budget;
    }

    private static void assertFailure(
            P4E0R2QAuditBudget.AuditFailure failure,
            P4E0R2QCasePlan.FailureStage stage,
            P4E0R2QProfile.Counter counter) {
        assertEquals(stage, failure.stage());
        assertEquals(counter, failure.counter().orElse(null));
        assertEquals("R2Q audit incomplete", failure.getMessage());
        assertFalse(failure.getMessage().contains("/"));
    }

    private void assertParserFailure(
            String name,
            P4E0R2QCasePlan.FailureStage stage,
            P4E0ResearchWireNbt.PayloadWriter payload) throws Exception {
        var path = temporary.resolve(name + ".dat");
        P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.BEST_COMPRESSION,
                1_024L,
                1_024L,
                payload);
        var budget = readyBudget();
        budget.observeDirectoryEntries(1L);
        var failure = assertThrows(
                P4E0R2QAuditBudget.AuditFailure.class,
                () -> P4E0ResearchWireNbt.scan(
                        path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY));
        assertBoundedNonCounterFailure(
                failure,
                stage,
                P4E0R2QAuditBudget.FailureCode.FIXTURE_PARSER_INVALID);
    }

    private P4E0R2QAuditBudget assertGzipFailure(Path path) throws Exception {
        var budget = readyBudget();
        budget.observeDirectoryEntries(1L);
        var failure = assertThrows(
                P4E0R2QAuditBudget.AuditFailure.class,
                () -> P4E0ResearchWireNbt.scan(
                        path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY));
        assertBoundedNonCounterFailure(
                failure,
                P4E0R2QCasePlan.FailureStage.GZIP_FRAMING,
                P4E0R2QAuditBudget.FailureCode.GZIP_FRAMING_INVALID);
        return budget;
    }

    private static void assertBoundedNonCounterFailure(
            P4E0R2QAuditBudget.AuditFailure failure,
            P4E0R2QCasePlan.FailureStage stage,
            P4E0R2QAuditBudget.FailureCode code) {
        assertEquals(stage, failure.stage());
        assertEquals(code, failure.code());
        assertTrue(failure.counter().isEmpty());
        assertEquals(0L, failure.observedAtLeast());
        assertEquals(0L, failure.maximum());
        assertEquals("R2Q audit incomplete", failure.getMessage());
        assertNull(failure.getCause());
    }

    private static void writeDataVersion(Path path, DataVersionShape shape) throws Exception {
        P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.BEST_COMPRESSION,
                1_024L,
                1_024L,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    switch (shape) {
                        case CANONICAL -> {
                            output.writeByte(Tag.TAG_INT);
                            output.writeUTF("DataVersion");
                            output.writeInt(3_955);
                        }
                        case MISSING -> {
                        }
                        case WRONG_TYPE -> {
                            output.writeByte(Tag.TAG_STRING);
                            output.writeUTF("DataVersion");
                            output.writeUTF("3955");
                        }
                        case WRONG_VALUE -> {
                            output.writeByte(Tag.TAG_INT);
                            output.writeUTF("DataVersion");
                            output.writeInt(3_954);
                        }
                    }
                    output.writeByte(Tag.TAG_END);
                });
    }

    private enum DataVersionShape {
        CANONICAL,
        MISSING,
        WRONG_TYPE,
        WRONG_VALUE
    }
}
