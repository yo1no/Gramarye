package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact counting proof that one 2,048-record plan realizes the full structural tuple. */
final class P4E0ResearchR2QJointRecordsTest {
    private static final long MAXIMUM_PHYSICAL_BYTES = 33_559_514L;
    private static final long MAXIMUM_DECOMPRESSED_BYTES = 268_435_456L;

    @TempDir
    Path temporary;

    @Test
    void case04IdentityArithmeticAndConstructionCeilingStayExact() throws Exception {
        var spec = P4E0R2QCasePlan.standard().cases().get(4);
        var target = P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE;
        var failure = spec.expectedFailure().orElseThrow();
        var diagnostic = P4E0R2QJointRecords.diagnoseDecompressedPerFileConstruction();
        var plan = P4E0R2QJointRecords.buildNegative(target);
        var relaxedRecords = plan.records().stream()
                .filter(record -> record.constructionDecompressedCeiling()
                        == MAXIMUM_DECOMPRESSED_BYTES + 1L)
                .toList();

        assertAll(
                () -> assertEquals(4, spec.index()),
                () -> assertEquals(
                        "p4-e0-r2q-balanced-v0-1536-over-decompressed-bytes-per-file",
                        spec.caseId()),
                () -> assertEquals(target, spec.targetCounter().orElseThrow()),
                () -> assertEquals(MAXIMUM_DECOMPRESSED_BYTES, spec.maximum()),
                () -> assertEquals(MAXIMUM_DECOMPRESSED_BYTES + 1L,
                        spec.observedAtLeast()),
                () -> assertEquals(
                        P4E0R2QCasePlan.FailureCode.COUNTER_CAPACITY_EXCEEDED,
                        failure.code()),
                () -> assertEquals(
                        P4E0R2QCasePlan.FailureStage.PER_FILE_DECOMPRESSED,
                        failure.stage()),
                () -> assertEquals(
                        "6a6f4541f4c23b9aefad465eb29ec0420d3a4f635f06b528ca07239a93f99418",
                        P4E0R2QProfile.manifestHash()),
                () -> assertEquals(
                        "23408739f292d2a5696c56c39b8b4b3978b3840af383930293efbe6b824f5035",
                        P4E0R2QCasePlan.standard().planHash()),
                () -> assertEquals(
                        P4E0R2QJointRecords.ConstructionDiagnosticCode
                                .COMPLETE_TARGET_REACHED_AT_MAX_PLUS_ONE,
                        diagnostic.code()),
                () -> assertEquals(268_435_456L, diagnostic.countBeforeWrite()),
                () -> assertEquals(1L, diagnostic.requestedWriteWidth()),
                () -> assertEquals(268_435_456L, diagnostic.measurementCeiling()),
                () -> assertEquals(268_435_457L, diagnostic.expectedObservedValue()),
                () -> assertEquals(268_435_457L, diagnostic.projectedCountAfterWrite()),
                () -> assertEquals(1, relaxedRecords.size()),
                () -> assertEquals(268_435_457L,
                        relaxedRecords.getFirst().facts().decompressedBytes()),
                () -> assertTrue(plan.records().stream()
                        .filter(record -> record != relaxedRecords.getFirst())
                        .allMatch(record -> record.constructionDecompressedCeiling()
                                == MAXIMUM_DECOMPRESSED_BYTES)));
    }

    @Test
    void depthNegativeInsertsOneEmptyNamedCompoundWithoutRenamingTheBaselineChain() {
        var baseline = P4E0R2QFixturePlan.locked().jointRecords().records().get(2).facts();
        var negative = P4E0R2QJointRecords.buildNegative(
                P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE)
                .records().get(2).facts();

        assertAll(
                () -> assertEquals(2_577L, baseline.decompressedBytes()),
                () -> assertEquals(2_581L, negative.decompressedBytes()),
                () -> assertEquals(512, baseline.containerDepth()),
                () -> assertEquals(513, negative.containerDepth()),
                () -> assertEquals(
                        baseline.compoundContainers() + 1L,
                        negative.compoundContainers()),
                () -> assertEquals(
                        baseline.compoundFieldEntries() + 1L,
                        negative.compoundFieldEntries()),
                () -> assertEquals(522L, baseline.modifiedUtf8Bytes()),
                () -> assertEquals(
                        baseline.modifiedUtf8Bytes(), negative.modifiedUtf8Bytes()),
                () -> assertEquals(
                        baseline.decompressedBytes() + 4L,
                        negative.decompressedBytes()));
    }

    @Test
    void samePhysicalWritersDeriveAllStructuralTotalsAndCanonicalGzipBaselines()
            throws Exception {
        var structural = P4E0R2QFixturePlan.StructuralComposition.locked();
        var plan = P4E0R2QJointRecords.build(structural);
        var aggregate = plan.aggregate();

        assertEquals(2_048, plan.records().size());
        assertEquals(2_048, plan.canonicalPhysicalBytes().size());
        assertEquals(structural.decompressedBytes(), aggregate.decompressedBytes());
        assertEquals(structural.peaks().containerDepthPerFile(), aggregate.containerDepth());
        assertEquals(structural.compoundContainers(), aggregate.compoundContainers());
        assertEquals(structural.compoundFieldEntries(), aggregate.compoundFieldEntries());
        assertEquals(structural.listElements(), aggregate.listElements());
        assertEquals(structural.byteArrayElements(), aggregate.byteArrayElements());
        assertEquals(structural.intArrayElements(), aggregate.intArrayElements());
        assertEquals(structural.longArrayElements(), aggregate.longArrayElements());
        assertEquals(structural.modifiedUtf8Bytes(), aggregate.modifiedUtf8Bytes());
        assertEquals(structural.scalarTags(), aggregate.scalarTags());
        assertTrue(plan.canonicalPhysicalBytes().stream()
                .allMatch(bytes -> bytes > 0L && bytes <= 33_559_514L));

        for (var index : new int[] {0, 1, 8, 2_047}) {
            var measured = plan.measure(
                    index,
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    33_559_514L);
            assertEquals(
                    plan.records().get(index).canonicalPhysicalBytes(),
                    measured.physicalBytes());
            assertEquals(
                    plan.records().get(index).facts().decompressedBytes(),
                    measured.decompressedBytes());
        }
    }

    @Test
    void everyTunedMemberPassesStrictFhcrcEofAndIndependentScannerTraversal()
            throws Exception {
        var blueprint = P4E0R2QFixturePlan.locked();
        var records = blueprint.jointRecords();
        var tuning = blueprint.compressed();
        var budget = new P4E0R2QAuditBudget();
        var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
        var member = temporary.resolve("joint-member.dat");
        var fhcrcMembers = 0;
        var invalidFhcrcRejected = false;
        var compressedTrailingDataRejected = false;

        budget.requireJournalReady(true);
        budget.observeDirectoryEntries(4_096L);
        for (var file : tuning.files()) {
            var record = records.records().get(file.fileIndex());
            try {
                var written = records.write(
                        file.fileIndex(),
                        member,
                        file.headerOptions(),
                        file.targetPhysicalBytes());
                var strict = P4E0ResearchGzipAdapter.readWireDrain(
                        member,
                        file.targetPhysicalBytes(),
                        MAXIMUM_DECOMPRESSED_BYTES);
                var scanned = P4E0ResearchWireNbt.scan(
                        member,
                        budget,
                        P4E0R2QAuditBudget.SourceSelection.PRIMARY);
                var dataVersion = budget.observeDataVersion(scanned.dataVersion(), dfu);

                assertEquals(file.targetPhysicalBytes(), written.physicalBytes());
                assertEquals(file.targetPhysicalBytes(), strict.physicalFileBytes());
                assertEquals(file.targetPhysicalBytes(), strict.compressedMemberBytes());
                assertEquals(file.targetPhysicalBytes(), scanned.physicalBytes());
                assertEquals(record.facts().decompressedBytes(), written.decompressedBytes());
                assertEquals(record.facts().decompressedBytes(), strict.decompressedRootBytes());
                assertEquals(record.facts().decompressedBytes(), scanned.decompressedBytes());
                assertEquals(3_955, dataVersion.acceptedValue());
                assertEquals(0, dataVersion.dfuInvocations());
                assertRecordFacts(record.facts(), scanned.nbt());

                if (file.fileNameBytes() == 0) {
                    assertEquals(10L, written.headerBytes());
                    assertEquals(10L, strict.gzipHeaderBytes());
                } else {
                    fhcrcMembers++;
                    assertTrue(file.headerOptions().fhcrc());
                    assertEquals(13L + file.fileNameBytes(), written.headerBytes());
                    assertEquals(written.headerBytes(), strict.gzipHeaderBytes());
                    if (!invalidFhcrcRejected) {
                        corruptFirstFhcrcByte(member, written.headerBytes());
                        assertThrows(
                                IOException.class,
                                () -> P4E0ResearchGzipAdapter.readWireDrain(
                                        member,
                                        file.targetPhysicalBytes(),
                                        MAXIMUM_DECOMPRESSED_BYTES));
                        invalidFhcrcRejected = true;
                    } else if (!compressedTrailingDataRejected) {
                        Files.write(
                                member,
                                new byte[] {0},
                                StandardOpenOption.APPEND);
                        assertThrows(
                                IOException.class,
                                () -> P4E0ResearchGzipAdapter.readWireDrain(
                                        member,
                                        file.targetPhysicalBytes() + 1L,
                                        MAXIMUM_DECOMPRESSED_BYTES));
                        compressedTrailingDataRejected = true;
                    }
                }
            } finally {
                Files.deleteIfExists(member);
            }
        }

        var observed = budget.facts();
        var structural = observed.structural();
        var expected = blueprint.counters();
        assertTrue(fhcrcMembers > 0);
        assertTrue(invalidFhcrcRejected);
        assertTrue(compressedTrailingDataRejected);
        assertEquals(4_096L, observed.directoryEntries());
        assertEquals(2_048L, observed.relevantRecords());
        assertEquals(expected.compressedBytesTotal(), observed.compressedBytes());
        assertEquals(expected.decompressedBytesTotal(), observed.decompressedBytes());
        assertEquals(expected.containerDepthPerFile(), structural.maxContainerDepth());
        assertEquals(expected.compoundContainersTotal(), structural.compoundContainers());
        assertEquals(expected.compoundFieldEntriesTotal(), structural.compoundFieldEntries());
        assertEquals(expected.listElementsTotal(), structural.listElements());
        assertEquals(expected.byteArrayElementsTotal(), structural.byteArrayElements());
        assertEquals(expected.intArrayElementsTotal(), structural.intArrayElements());
        assertEquals(expected.longArrayElementsTotal(), structural.longArrayElements());
        assertEquals(expected.modifiedUtf8BytesTotal(), structural.modifiedUtf8Bytes());
        assertEquals(expected.scalarTagsTotal(), structural.scalarTags());
        assertEquals(0L, observed.attachmentAdmissions());
        assertEquals(0L, observed.rawRootClaims());
        assertTrue(observed.journalReady());
        assertEquals(0, dfu.invocations());
        assertTrue(Files.notExists(member));
    }

    private static void assertRecordFacts(
            P4E0R2QJointRecords.RecordFacts expected,
            P4E0ResearchNbtMetrics actual) {
        assertEquals(expected.containerDepth(), actual.maxContainerDepth());
        assertEquals(expected.compoundContainers(), actual.compoundCount());
        assertEquals(expected.compoundFieldEntries(), actual.compoundEntryCount());
        assertEquals(expected.listElements(), actual.listElementCount());
        assertEquals(expected.byteArrayElements(), actual.byteArrayElements());
        assertEquals(expected.intArrayElements(), actual.intArrayElements());
        assertEquals(expected.longArrayElements(), actual.longArrayElements());
        assertEquals(expected.modifiedUtf8Bytes(), actual.modifiedUtf8Bytes());
        assertEquals(expected.scalarTags(), actual.scalarTagCount());
    }

    private static void corruptFirstFhcrcByte(Path path, long headerBytes)
            throws IOException {
        var position = Math.subtractExact(headerBytes, 2L);
        try (var channel = FileChannel.open(
                path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var value = ByteBuffer.allocate(1);
            channel.position(position);
            while (value.hasRemaining()) {
                if (channel.read(value) < 0) {
                    throw new IOException("truncated joint FHCRC witness");
                }
            }
            value.flip();
            value.put(0, (byte) (value.get(0) ^ 0x01));
            channel.position(position);
            while (value.hasRemaining()) {
                channel.write(value);
            }
            channel.force(true);
        }
    }
}
