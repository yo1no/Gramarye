package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import org.junit.jupiter.api.Test;

final class P4E1AuditBudgetTest {
    private static final List<CounterPair> PAIRS = List.of(
            pair(P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                    P4E1AuditCounter.COMPRESSED_BYTES_TOTAL),
            pair(P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE,
                    P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL),
            pair(P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE,
                    P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL),
            pair(P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                    P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL),
            pair(P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.LIST_ELEMENTS_TOTAL),
            pair(P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL),
            pair(P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL),
            pair(P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE,
                    P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL),
            pair(P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE,
                    P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL),
            pair(P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                    P4E1AuditCounter.SCALAR_TAGS_TOTAL));

    @Test
    void vocabularyHasExactlyTwentyFiveIndependentCounters() {
        assertEquals(25, P4E1AuditCounter.values().length);
        var budget = P4E1TestBudgets.create();
        for (var counter : P4E1AuditCounter.values()) {
            assertTrue(budget.maximum(counter) > 0L, counter.name());
            assertEquals(0L, budget.observed(counter), counter.name());
        }
        assertEquals(
                MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM,
                budget.maximum(P4E1AuditCounter.RAW_ROOT_CLAIMS));
    }

    @Test
    void standaloneCountersAcceptExactMaximumAndRejectCanonicalMaximumPlusOne() {
        assertStandaloneExactAndExcess(
                P4E1AuditCounter.DIRECTORY_ENTRIES,
                P4E1AuditStage.DIRECTORY_ENTRIES);
        assertStandaloneExactAndExcess(
                P4E1AuditCounter.RELEVANT_RECORDS,
                P4E1AuditStage.RELEVANT_RECORDS);

        var admissions = P4E1TestBudgets.create();
        var admissionMaximum = admissions.maximum(P4E1AuditCounter.ATTACHMENT_ADMISSIONS);
        for (var index = 0L; index < admissionMaximum; index++) {
            assertTrue(admissions.checkpointAttachmentAdmissionAfterInvocation(
                    P4E1AuditStage.ATTACHMENT_ADMISSION_COUNTER).isEmpty());
        }
        assertCanonicalExcess(
                admissions.checkpointAttachmentAdmissionAfterInvocation(
                        P4E1AuditStage.ATTACHMENT_ADMISSION_COUNTER).orElseThrow(),
                P4E1AuditCounter.ATTACHMENT_ADMISSIONS,
                P4E1AuditStage.ATTACHMENT_ADMISSION_COUNTER,
                admissionMaximum);

        var roots = P4E1TestBudgets.create();
        var rootMaximum = roots.maximum(P4E1AuditCounter.RAW_ROOT_CLAIMS);
        assertTrue(roots.checkpointRawRootClaim(P4E1AuditStage.RAW_ROOT_CAPTURE).isEmpty());
        assertTrue(roots.checkpointSingle(
                P4E1AuditCounter.DIRECTORY_ENTRIES,
                P4E1AuditStage.DIRECTORY_ENTRIES,
                0L).isEmpty());
        for (var index = 1L; index < rootMaximum; index++) {
            assertTrue(roots.checkpointRawRootClaim(P4E1AuditStage.RAW_ROOT_CAPTURE).isEmpty());
        }
        assertCanonicalExcess(
                roots.checkpointRawRootClaim(P4E1AuditStage.RAW_ROOT_CAPTURE).orElseThrow(),
                P4E1AuditCounter.RAW_ROOT_CLAIMS,
                P4E1AuditStage.RAW_ROOT_CAPTURE,
                rootMaximum);
    }

    @Test
    void everyPerFileCounterAcceptsExactMaximumAndRejectsMaximumPlusOne() {
        for (var pair : PAIRS) {
            var budget = P4E1TestBudgets.create();
            var file = budget.newFileScope();
            var maximum = budget.maximum(pair.perFile());
            assertTrue(file.checkpointFileAndAggregate(
                    pair.perFile(),
                    pair.aggregate(),
                    pair.perFileStage(),
                    pair.aggregateStage(),
                    maximum).isEmpty());
            assertEquals(maximum, file.observed(pair.perFile()));
            assertCanonicalExcess(
                    file.checkpointFileAndAggregate(
                            pair.perFile(),
                            pair.aggregate(),
                            pair.perFileStage(),
                            pair.aggregateStage(),
                            1L).orElseThrow(),
                    pair.perFile(),
                    pair.perFileStage(),
                    maximum);
            assertEquals(maximum, file.observed(pair.perFile()));
        }

        var budget = P4E1TestBudgets.create();
        var file = budget.newFileScope();
        var maximum = budget.maximum(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE);
        assertTrue(file.checkpointDepth(
                P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND, maximum).isEmpty());
        assertCanonicalExcess(
                file.checkpointDepth(
                        P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                        maximum + 1L).orElseThrow(),
                P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE,
                P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                maximum);
        assertEquals(maximum, file.observed(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE));
    }

    @Test
    void everyAggregateCounterAcceptsExactMaximumAndRejectsMaximumPlusOne() {
        for (var pair : PAIRS) {
            var budget = P4E1TestBudgets.create();
            var aggregateMaximum = budget.maximum(pair.aggregate());
            var remaining = aggregateMaximum;
            while (remaining > 0L) {
                var file = budget.newFileScope();
                var delta = Math.min(remaining, budget.maximum(pair.perFile()));
                assertTrue(file.checkpointFileAndAggregate(
                        pair.perFile(),
                        pair.aggregate(),
                        pair.perFileStage(),
                        pair.aggregateStage(),
                        delta).isEmpty());
                remaining -= delta;
            }
            assertEquals(aggregateMaximum, budget.observed(pair.aggregate()));
            var nextFile = budget.newFileScope();
            assertCanonicalExcess(
                    nextFile.checkpointFileAndAggregate(
                            pair.perFile(),
                            pair.aggregate(),
                            pair.perFileStage(),
                            pair.aggregateStage(),
                            1L).orElseThrow(),
                    pair.aggregate(),
                    pair.aggregateStage(),
                    aggregateMaximum);
            assertEquals(0L, nextFile.observed(pair.perFile()));
            assertEquals(aggregateMaximum, budget.observed(pair.aggregate()));
        }
    }

    @Test
    void pairCheckpointIsAtomicWithPerFilePrecedence() {
        var pair = pair(
                P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                P4E1AuditCounter.COMPRESSED_BYTES_TOTAL);
        var budget = P4E1TestBudgets.create();
        var file = budget.newFileScope();
        var maximum = budget.maximum(pair.perFile());
        assertTrue(file.checkpointFileAndAggregate(
                pair.perFile(),
                pair.aggregate(),
                pair.perFileStage(),
                pair.aggregateStage(),
                maximum - 1L).isEmpty());
        var aggregateBefore = budget.observed(pair.aggregate());

        var excess = file.checkpointFileAndAggregate(
                pair.perFile(),
                pair.aggregate(),
                pair.perFileStage(),
                pair.aggregateStage(),
                2L).orElseThrow();

        assertEquals(pair.perFile(), excess.counter());
        assertEquals(maximum - 1L, file.observed(pair.perFile()));
        assertEquals(aggregateBefore, budget.observed(pair.aggregate()));
    }

    @Test
    void longOverflowIsRejectedBeforeAdditionAndNegativeDeltaIsProgrammingFailure() {
        var budget = P4E1TestBudgets.create();
        var maximum = budget.maximum(P4E1AuditCounter.DIRECTORY_ENTRIES);
        assertCanonicalExcess(
                budget.checkpointSingle(
                        P4E1AuditCounter.DIRECTORY_ENTRIES,
                        P4E1AuditStage.DIRECTORY_ENTRIES,
                        Long.MAX_VALUE).orElseThrow(),
                P4E1AuditCounter.DIRECTORY_ENTRIES,
                P4E1AuditStage.DIRECTORY_ENTRIES,
                maximum);
        assertEquals(0L, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
        assertThrows(IllegalArgumentException.class, () -> budget.checkpointSingle(
                P4E1AuditCounter.DIRECTORY_ENTRIES,
                P4E1AuditStage.DIRECTORY_ENTRIES,
                -1L));

        for (var pair : PAIRS) {
            var pairedBudget = P4E1TestBudgets.create();
            var file = pairedBudget.newFileScope();
            assertTrue(file.checkpointFileAndAggregate(
                    pair.perFile(),
                    pair.aggregate(),
                    pair.perFileStage(),
                    pair.aggregateStage(),
                    1L).isEmpty());
            var aggregateBefore = pairedBudget.observed(pair.aggregate());
            var exceeded = file.checkpointFileAndAggregate(
                    pair.perFile(),
                    pair.aggregate(),
                    pair.perFileStage(),
                    pair.aggregateStage(),
                    Long.MAX_VALUE).orElseThrow();
            assertEquals(pair.perFile(), exceeded.counter());
            assertEquals(1L, file.observed(pair.perFile()));
            assertEquals(aggregateBefore, pairedBudget.observed(pair.aggregate()));
        }

        var depthBudget = P4E1TestBudgets.create();
        var depthFile = depthBudget.newFileScope();
        var depthExceeded = depthFile.checkpointDepth(
                P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND,
                Long.MAX_VALUE).orElseThrow();
        assertEquals(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE,
                depthExceeded.counter());
        assertEquals(0L, depthFile.observed(
                P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE));
    }

    @Test
    void integratedCompressedCoordinateIsExplicitlyNotApplicable() {
        var budget = P4E1TestBudgets.create();
        var file = budget.newFileScope();
        assertEquals(
                P4E1AuditBudget.CompressedBytesApplicability.UNOBSERVED,
                file.compressedBytesApplicability());

        file.markCompressedBytesNotApplicable();

        assertEquals(
                P4E1AuditBudget.CompressedBytesApplicability.NOT_APPLICABLE,
                file.compressedBytesApplicability());
        assertEquals(0L, budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL));
        assertThrows(IllegalStateException.class, () -> file.checkpointFileAndAggregate(
                P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                P4E1AuditStage.PER_FILE_COMPRESSED,
                P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                0L));

        var disk = budget.newFileScope();
        assertFalse(disk.checkpointFileAndAggregate(
                P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                P4E1AuditStage.PER_FILE_COMPRESSED,
                P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                0L).isPresent());
        assertEquals(
                P4E1AuditBudget.CompressedBytesApplicability.APPLICABLE,
                disk.compressedBytesApplicability());
        assertThrows(IllegalStateException.class, disk::markCompressedBytesNotApplicable);
    }

    @Test
    void mismatchedCounterFamiliesAndGenericSpecialCounterUseFailFast() {
        var budget = P4E1TestBudgets.create();
        var file = budget.newFileScope();
        assertThrows(IllegalArgumentException.class, () -> file.checkpointFileAndAggregate(
                P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL,
                P4E1AuditStage.TYPED_ARRAY_LENGTH,
                P4E1AuditStage.TYPED_ARRAY_LENGTH,
                1L));
        assertThrows(IllegalArgumentException.class, () -> budget.checkpointSingle(
                P4E1AuditCounter.ATTACHMENT_ADMISSIONS,
                P4E1AuditStage.ATTACHMENT_ADMISSION_COUNTER,
                1L));
    }

    private static void assertStandaloneExactAndExcess(
            P4E1AuditCounter counter,
            P4E1AuditStage stage) {
        var budget = P4E1TestBudgets.create();
        var maximum = budget.maximum(counter);
        assertTrue(budget.checkpointSingle(counter, stage, maximum).isEmpty());
        assertEquals(maximum, budget.observed(counter));
        assertCanonicalExcess(
                budget.checkpointSingle(counter, stage, 1L).orElseThrow(),
                counter,
                stage,
                maximum);
        assertEquals(maximum, budget.observed(counter));
    }

    private static void assertCanonicalExcess(
            P4E1AuditBudget.Exceeded excess,
            P4E1AuditCounter counter,
            P4E1AuditStage stage,
            long maximum) {
        assertEquals(counter, excess.counter());
        assertEquals(stage, excess.stage());
        assertEquals(maximum + 1L, excess.observedAtLeast());
        assertEquals(maximum, excess.maximum());
    }

    private static CounterPair pair(
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate) {
        var perFileStage = perFile == P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE
                ? P4E1AuditStage.MODIFIED_UTF_PREFIX
                : perFile == P4E1AuditCounter.LIST_ELEMENTS_PER_FILE
                        ? P4E1AuditStage.LIST_LENGTH
                        : perFile == P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE
                                || perFile == P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE
                                || perFile == P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE
                                        ? P4E1AuditStage.TYPED_ARRAY_LENGTH
                                        : perFile == P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE
                                                ? P4E1AuditStage.PER_FILE_COMPRESSED
                                                : perFile
                                                                == P4E1AuditCounter
                                                                        .DECOMPRESSED_BYTES_PER_FILE
                                                        ? P4E1AuditStage.PER_FILE_DECOMPRESSED
                                                        : P4E1AuditStage
                                                                .DEPTH_CONTAINER_SCALAR_KIND;
        var aggregateStage = perFile == P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE
                ? P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD
                : perFile == P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE
                        ? P4E1AuditStage.AGGREGATE_DECOMPRESSED_CHECKED_ADD
                        : perFileStage;
        return new CounterPair(perFile, aggregate, perFileStage, aggregateStage);
    }

    private record CounterPair(
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate,
            P4E1AuditStage perFileStage,
            P4E1AuditStage aggregateStage) {
    }
}
